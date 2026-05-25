package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Loads {@link PylorosPlugin}s via the Java {@link ServiceLoader} and records a
 * {@link PluginLoadResult} per plugin so operators can see which plugins were
 * loaded, disabled, or failed and why.
 *
 * <p>This class owns the R4-06 concerns: failure isolation across plugins,
 * structured diagnostics ({@link PluginStatus} + {@link PluginErrorInfo}), and
 * atomic publication of contributed providers — invalid contributions are
 * dropped entirely so that no partial set of providers is exposed downstream.
 * The plugin API itself ({@link PylorosPlugin}, {@link PluginDescriptor},
 * {@link PluginContribution}, {@link PluginContributionResult}) is owned by
 * R4-01.</p>
 *
 * <p>Three lifecycle phases are diagnosed independently:</p>
 * <ol>
 *   <li><b>load</b> — instantiate the implementation class and obtain its
 *       {@link PluginDescriptor}. Any failure here is reported as
 *       {@link PluginStatus#FAILED_TO_LOAD} using the impl class name as a
 *       fallback id.</li>
 *   <li><b>initialize</b> — reserved for any future initialization hook that
 *       runs before {@code contribute()}; the canonical R4-01 API does not
 *       expose such a hook yet, but the enum value
 *       {@link PluginStatus#FAILED_TO_INITIALIZE} is kept available so that
 *       diagnostics stay forward-compatible.</li>
 *   <li><b>contribute</b> — call {@link PylorosPlugin#contribute()} and
 *       validate the returned {@link PluginContribution}. Any failure
 *       (thrown exception, {@code null} contribution, {@code null} provider,
 *       blank {@code providerId} or duplicate {@code providerId}) is reported
 *       as {@link PluginStatus#FAILED_TO_CONTRIBUTE} and the partial
 *       contribution is dropped.</li>
 * </ol>
 */
public final class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final List<PluginLoadResult> results;
    private final Map<String, PluginLoadResult> resultsById;
    private final List<ToolProvider> contributedProviders;
    private final List<PluginContributionResult> contributionResults;

    private PluginRegistry(
            List<PluginLoadResult> results,
            List<ToolProvider> contributedProviders,
            List<PluginContributionResult> contributionResults) {
        LinkedHashMap<String, PluginLoadResult> byId = new LinkedHashMap<>();
        for (PluginLoadResult result : results) {
            byId.put(result.pluginId(), result);
        }
        this.results = List.copyOf(results);
        this.resultsById = Collections.unmodifiableMap(byId);
        this.contributedProviders = List.copyOf(contributedProviders);
        this.contributionResults = List.copyOf(contributionResults);
    }

    /**
     * Loads plugins from the current thread context class loader using the
     * standard {@link ServiceLoader} mechanism.
     */
    public static PluginRegistry load(Set<String> disabledPluginIds) {
        ServiceLoader<PylorosPlugin> serviceLoader = ServiceLoader.load(PylorosPlugin.class);
        return loadFrom(serviceLoader.stream().toList(), disabledPluginIds);
    }

    /**
     * Test-friendly entry point: loads plugins from the supplied
     * {@link ServiceLoader.Provider} entries.
     */
    public static PluginRegistry loadFrom(
            Iterable<? extends ServiceLoader.Provider<? extends PylorosPlugin>> providers,
            Set<String> disabledPluginIds) {
        Objects.requireNonNull(providers, "providers must not be null");
        Set<String> disabled = disabledPluginIds == null ? Set.of() : Set.copyOf(disabledPluginIds);

        List<PluginLoadResult> results = new ArrayList<>();
        List<ToolProvider> contributed = new ArrayList<>();
        List<PluginContributionResult> contributionResults = new ArrayList<>();
        Set<String> usedPluginIds = new LinkedHashSet<>();

        for (ServiceLoader.Provider<? extends PylorosPlugin> entry : providers) {
            PluginLoadResult result = loadOne(entry, disabled, contributed, contributionResults, usedPluginIds);
            results.add(result);
        }

        return new PluginRegistry(results, contributed, contributionResults);
    }

    private static PluginLoadResult loadOne(
            ServiceLoader.Provider<? extends PylorosPlugin> entry,
            Set<String> disabledPluginIds,
            List<ToolProvider> contributedSink,
            List<PluginContributionResult> contributionResultsSink,
            Set<String> usedPluginIds) {
        String fallbackId = entry.type().getName();

        // Phase 1: instantiate
        PylorosPlugin plugin;
        try {
            plugin = entry.get();
        } catch (Throwable t) {
            log.error("[PLUGIN] failed to load plugin class {}: {}", fallbackId, t.toString());
            return PluginLoadResult.failedToLoad(fallbackId, t);
        }
        if (plugin == null) {
            log.error("[PLUGIN] plugin class {} returned null instance", fallbackId);
            return PluginLoadResult.failedToLoad(fallbackId, new IllegalStateException("plugin instance was null"));
        }

        // Resolve canonical descriptor. A throwing or invalid descriptor() call
        // is treated as a load failure because we cannot reliably identify the
        // plugin for any later phase.
        PluginDescriptor descriptor;
        try {
            descriptor = plugin.descriptor();
            if (descriptor == null) {
                return PluginLoadResult.failedToLoad(fallbackId,
                        new IllegalStateException("descriptor() returned null"));
            }
        } catch (Throwable t) {
            log.error("[PLUGIN] descriptor() threw for plugin class {}: {}", fallbackId, t.toString());
            return PluginLoadResult.failedToLoad(fallbackId, t);
        }

        String pluginId = descriptor.id();
        if (!usedPluginIds.add(pluginId)) {
            String reason = "duplicate plugin id '" + pluginId + "'";
            contributionResultsSink.add(PluginContributionResult.rejected(
                    descriptor, PluginContribution.empty(), reason));
            return PluginLoadResult.failedToLoad(fallbackId, new IllegalStateException(reason));
        }

        if (disabledPluginIds.contains(pluginId)) {
            log.info("[PLUGIN] {} disabled by configuration", pluginId);
            return PluginLoadResult.disabled(descriptor);
        }

        // Phase 2: contribute (must be atomic — no partial publication)
        PluginContribution contribution;
        try {
            contribution = plugin.contribute();
            if (contribution == null) {
                throw new IllegalStateException("contribute() returned null");
            }
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to contribute: {}", pluginId, t.toString());
            contributionResultsSink.add(PluginContributionResult.rejected(
                    descriptor, PluginContribution.empty(),
                    "contribute() failed: " + truncateReason(t)));
            return PluginLoadResult.failedToContribute(descriptor, t);
        }

        List<ToolProvider> validated;
        try {
            validated = validateContribution(contribution);
        } catch (Throwable t) {
            log.error("[PLUGIN] {} produced invalid contribution: {}", pluginId, t.toString());
            contributionResultsSink.add(PluginContributionResult.rejected(
                    descriptor, contribution, "invalid contribution: " + truncateReason(t)));
            return PluginLoadResult.failedToContribute(descriptor, t);
        }

        contributedSink.addAll(validated);
        contributionResultsSink.add(PluginContributionResult.accepted(descriptor, contribution));
        log.info("[PLUGIN] {} loaded with {} provider(s)", pluginId, validated.size());
        return PluginLoadResult.loaded(descriptor);
    }

    private static List<ToolProvider> validateContribution(PluginContribution contribution) {
        List<ToolProvider> raw = contribution.toolProviders();
        // PluginContribution already guarantees non-null and no null entries;
        // R4-06 additionally requires non-blank providerIds and no duplicates.
        List<ToolProvider> validated = new ArrayList<>(raw.size());
        Set<String> seenProviderIds = new LinkedHashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            ToolProvider provider = raw.get(index);
            String providerId;
            try {
                providerId = provider.providerId();
            } catch (Throwable t) {
                throw new IllegalStateException(
                        "providerId() threw for contributed provider at index " + index, t);
            }
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalStateException(
                        "contributed provider at index " + index + " has blank providerId");
            }
            if (!seenProviderIds.add(providerId)) {
                throw new IllegalStateException("duplicate providerId in contribution: " + providerId);
            }
            validated.add(provider);
        }
        return validated;
    }

    private static String truncateReason(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.isEmpty()) {
            return t.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 117) + "..." : message;
    }

    /** Load results in plugin discovery order, including failed and disabled entries. */
    public List<PluginLoadResult> results() {
        return results;
    }

    public Map<String, PluginLoadResult> resultsById() {
        return resultsById;
    }

    public Optional<PluginLoadResult> findById(String pluginId) {
        if (pluginId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resultsById.get(pluginId));
    }

    /** Providers contributed by plugins whose status is {@link PluginStatus#LOADED}. */
    public List<ToolProvider> contributedProviders() {
        return contributedProviders;
    }

    /**
     * Per-plugin host outcome for every plugin that produced a contribution
     * (accepted or rejected). Plugins that failed before reaching the
     * contribution phase do not appear here; see {@link #results()} for the
     * full diagnostic view.
     */
    public List<PluginContributionResult> contributionResults() {
        return contributionResults;
    }
}
