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
 * Loads {@link PylorosPlugin}s via {@link ServiceLoader} and records one
 * {@link PluginLoadResult} per discovered plugin.
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

    public static PluginRegistry load(Set<String> disabledPluginIds) {
        ServiceLoader<PylorosPlugin> serviceLoader = ServiceLoader.load(PylorosPlugin.class);
        return loadFrom(serviceLoader.stream().toList(), disabledPluginIds);
    }

    /**
     * Load plugins using a {@link PluginActivationResolver} that provides both
     * activation decisions and per-plugin configuration. Each enabled plugin
     * receives a {@link PluginContext} with {@link PluginConfigurationView} and
     * {@link PluginDiagnostics} services bound to its own configuration block.
     *
     * @param providers providers as returned by {@link ServiceLoader#stream()}
     * @param resolver  activation resolver; must not be {@code null}
     * @return the populated registry
     */
    public static PluginRegistry loadFrom(
            Iterable<? extends ServiceLoader.Provider<? extends PylorosPlugin>> providers,
            PluginActivationResolver resolver) {
        Objects.requireNonNull(providers, "providers must not be null");
        Objects.requireNonNull(resolver, "resolver must not be null");

        List<PluginLoadResult> results = new ArrayList<>();
        List<ToolProvider> contributed = new ArrayList<>();
        List<PluginContributionResult> contributionResults = new ArrayList<>();
        Set<String> usedPluginIds = new LinkedHashSet<>();

        for (ServiceLoader.Provider<? extends PylorosPlugin> entry : providers) {
            results.add(loadOne(entry, resolver, contributed, contributionResults, usedPluginIds));
        }
        return new PluginRegistry(results, contributed, contributionResults);
    }

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
            results.add(loadOne(entry, disabled, contributed, contributionResults, usedPluginIds));
        }
        return new PluginRegistry(results, contributed, contributionResults);
    }

    private static PluginLoadResult loadOne(
            ServiceLoader.Provider<? extends PylorosPlugin> entry,
            PluginActivationResolver resolver,
            List<ToolProvider> contributedSink,
            List<PluginContributionResult> contributionResultsSink,
            Set<String> usedPluginIds) {
        String fallbackId = entry.type().getName();

        PylorosPlugin plugin;
        try {
            plugin = entry.get();
        } catch (Throwable t) {
            log.error("[PLUGIN] failed to load plugin class {}: {}", fallbackId, t.toString());
            return PluginLoadResult.failedToLoad(fallbackId, t);
        }
        if (plugin == null) {
            return PluginLoadResult.failedToLoad(fallbackId, new IllegalStateException("plugin instance was null"));
        }

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

        PluginActivation activation = resolver.resolve(pluginId);
        if (!activation.enabled()) {
            log.info("[PLUGIN] {} disabled by configuration", pluginId);
            return PluginLoadResult.disabled(descriptor);
        }

        PluginContext context = HostPluginContext.forPlugin(
                pluginId,
                activation.configuration(),
                slf4jDiagnostics(pluginId));
        try {
            plugin.initialize(context);
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to initialize: {}", pluginId, t.toString());
            return PluginLoadResult.failedToInitialize(descriptor, t);
        }

        PluginContribution contribution;
        try {
            contribution = plugin.contribute(context);
            if (contribution == null) {
                throw new IllegalStateException("contribute(context) returned null");
            }
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to contribute: {}", pluginId, t.toString());
            contributionResultsSink.add(PluginContributionResult.rejected(
                    descriptor, PluginContribution.empty(),
                    "contribute(context) failed: " + truncateReason(t)));
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

    private static PluginLoadResult loadOne(
            ServiceLoader.Provider<? extends PylorosPlugin> entry,
            Set<String> disabledPluginIds,
            List<ToolProvider> contributedSink,
            List<PluginContributionResult> contributionResultsSink,
            Set<String> usedPluginIds) {
        String fallbackId = entry.type().getName();

        PylorosPlugin plugin;
        try {
            plugin = entry.get();
        } catch (Throwable t) {
            log.error("[PLUGIN] failed to load plugin class {}: {}", fallbackId, t.toString());
            return PluginLoadResult.failedToLoad(fallbackId, t);
        }
        if (plugin == null) {
            return PluginLoadResult.failedToLoad(fallbackId, new IllegalStateException("plugin instance was null"));
        }

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

        PluginContext context = HostPluginContext.forPlugin(
                pluginId,
                PluginConfiguration.empty(pluginId),
                slf4jDiagnostics(pluginId));
        try {
            plugin.initialize(context);
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to initialize: {}", pluginId, t.toString());
            return PluginLoadResult.failedToInitialize(descriptor, t);
        }

        PluginContribution contribution;
        try {
            contribution = plugin.contribute(context);
            if (contribution == null) {
                throw new IllegalStateException("contribute(context) returned null");
            }
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to contribute: {}", pluginId, t.toString());
            contributionResultsSink.add(PluginContributionResult.rejected(
                    descriptor, PluginContribution.empty(),
                    "contribute(context) failed: " + truncateReason(t)));
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

    private static PluginDiagnostics slf4jDiagnostics(String pluginId) {
        return new PluginDiagnostics() {
            @Override
            public void info(String message) {
                log.info("[PLUGIN:{}] {}", pluginId, message);
            }

            @Override
            public void warn(String message) {
                log.warn("[PLUGIN:{}] {}", pluginId, message);
            }

            @Override
            public void error(String message) {
                log.error("[PLUGIN:{}] {}", pluginId, message);
            }
        };
    }

    private static List<ToolProvider> validateContribution(PluginContribution contribution) {
        List<ToolProvider> raw = contribution.toolProviders();
        List<ToolProvider> validated = new ArrayList<>(raw.size());
        Set<String> seenProviderIds = new LinkedHashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            ToolProvider provider = raw.get(index);
            String providerId;
            try {
                providerId = provider.providerId();
            } catch (Throwable t) {
                throw new IllegalStateException("providerId() threw for contributed provider at index " + index, t);
            }
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalStateException("contributed provider at index " + index + " has blank providerId");
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

    public List<ToolProvider> contributedProviders() {
        return contributedProviders;
    }

    public List<PluginContributionResult> contributionResults() {
        return contributionResults;
    }
}
