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
 * {@link PluginDescriptor} per plugin so operators can see which plugins were
 * loaded, disabled, or failed and why.
 *
 * <p>Faulty plugins never abort the server start: every lifecycle phase
 * (instantiate, initialize, contribute) is guarded and produces a structured
 * {@link PluginErrorInfo}. Plugins whose contribution is invalid are dropped
 * entirely so that no partial set of providers is exposed downstream.</p>
 */
public final class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final List<PluginDescriptor> descriptors;
    private final Map<String, PluginDescriptor> descriptorsById;
    private final List<ToolProvider> contributedProviders;

    private PluginRegistry(List<PluginDescriptor> descriptors, List<ToolProvider> contributedProviders) {
        LinkedHashMap<String, PluginDescriptor> byId = new LinkedHashMap<>();
        for (PluginDescriptor descriptor : descriptors) {
            byId.put(descriptor.pluginId(), descriptor);
        }
        this.descriptors = List.copyOf(descriptors);
        this.descriptorsById = Collections.unmodifiableMap(byId);
        this.contributedProviders = List.copyOf(contributedProviders);
    }

    /**
     * Loads plugins from the current thread context class loader using the
     * standard {@link ServiceLoader} mechanism.
     */
    public static PluginRegistry load(PluginContext context, Set<String> disabledPluginIds) {
        ServiceLoader<PylorosPlugin> serviceLoader = ServiceLoader.load(PylorosPlugin.class);
        return loadFrom(serviceLoader.stream().toList(), context, disabledPluginIds);
    }

    /**
     * Test-friendly entry point: loads plugins from the supplied
     * {@link ServiceLoader.Provider} entries.
     */
    public static PluginRegistry loadFrom(
            Iterable<? extends ServiceLoader.Provider<? extends PylorosPlugin>> providers,
            PluginContext context,
            Set<String> disabledPluginIds) {
        Objects.requireNonNull(providers, "providers must not be null");
        PluginContext effectiveContext = context == null ? PluginContext.EMPTY : context;
        Set<String> disabled = disabledPluginIds == null ? Set.of() : Set.copyOf(disabledPluginIds);

        List<PluginDescriptor> descriptors = new ArrayList<>();
        List<ToolProvider> contributed = new ArrayList<>();
        Set<String> usedPluginIds = new LinkedHashSet<>();

        for (ServiceLoader.Provider<? extends PylorosPlugin> entry : providers) {
            PluginDescriptor descriptor = loadOne(entry, effectiveContext, disabled, contributed, usedPluginIds);
            descriptors.add(descriptor);
        }

        return new PluginRegistry(descriptors, contributed);
    }

    private static PluginDescriptor loadOne(
            ServiceLoader.Provider<? extends PylorosPlugin> entry,
            PluginContext context,
            Set<String> disabledPluginIds,
            List<ToolProvider> contributedSink,
            Set<String> usedPluginIds) {
        String fallbackId = entry.type().getName();

        // Phase 1: instantiate
        PylorosPlugin plugin;
        try {
            plugin = entry.get();
        } catch (Throwable t) {
            log.error("[PLUGIN] failed to load plugin class {}: {}", fallbackId, t.toString());
            return PluginDescriptor.failedToLoad(fallbackId, t);
        }
        if (plugin == null) {
            log.error("[PLUGIN] plugin class {} returned null instance", fallbackId);
            return PluginDescriptor.failedToLoad(fallbackId, new IllegalStateException("plugin instance was null"));
        }

        // Resolve a stable plugin id. A throwing or invalid getPluginId() is treated as a load failure
        // because we cannot reliably identify the plugin for any later phase.
        String pluginId;
        try {
            String reported = plugin.getPluginId();
            if (reported == null || reported.isBlank()) {
                return PluginDescriptor.failedToLoad(fallbackId,
                        new IllegalStateException("plugin id must not be blank"));
            }
            pluginId = reported.trim();
        } catch (Throwable t) {
            log.error("[PLUGIN] getPluginId() threw for plugin class {}: {}", fallbackId, t.toString());
            return PluginDescriptor.failedToLoad(fallbackId, t);
        }

        if (!usedPluginIds.add(pluginId)) {
            return PluginDescriptor.failedToLoad(fallbackId,
                    new IllegalStateException("duplicate plugin id: " + pluginId));
        }

        if (disabledPluginIds.contains(pluginId)) {
            log.info("[PLUGIN] {} disabled by configuration", pluginId);
            return PluginDescriptor.disabled(pluginId);
        }

        // Phase 2: initialize
        try {
            plugin.initialize(context);
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to initialize: {}", pluginId, t.toString());
            return PluginDescriptor.failedToInitialize(pluginId, t);
        }

        // Phase 3: contribute (must be atomic — no partial publication)
        List<ToolProvider> contribution;
        try {
            List<ToolProvider> raw = plugin.createToolProviders(context);
            contribution = validateContribution(raw);
        } catch (Throwable t) {
            log.error("[PLUGIN] {} failed to contribute providers: {}", pluginId, t.toString());
            return PluginDescriptor.failedToContribute(pluginId, t);
        }

        contributedSink.addAll(contribution);
        log.info("[PLUGIN] {} loaded with {} provider(s)", pluginId, contribution.size());
        return PluginDescriptor.loaded(pluginId);
    }

    private static List<ToolProvider> validateContribution(List<ToolProvider> raw) {
        if (raw == null) {
            throw new IllegalStateException("createToolProviders returned null");
        }
        List<ToolProvider> validated = new ArrayList<>(raw.size());
        Set<String> seenProviderIds = new LinkedHashSet<>();
        for (int index = 0; index < raw.size(); index++) {
            ToolProvider provider = raw.get(index);
            if (provider == null) {
                throw new IllegalStateException("contributed provider at index " + index + " is null");
            }
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

    /** Descriptors in plugin discovery order, including failed and disabled entries. */
    public List<PluginDescriptor> descriptors() {
        return descriptors;
    }

    public Map<String, PluginDescriptor> descriptorsById() {
        return descriptorsById;
    }

    public Optional<PluginDescriptor> findById(String pluginId) {
        if (pluginId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptorsById.get(pluginId));
    }

    /** Providers contributed by plugins whose status is {@link PluginStatus#LOADED}. */
    public List<ToolProvider> contributedProviders() {
        return contributedProviders;
    }
}
