package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ToolCatalog {

    private final ProviderRegistry providerRegistry;
    private volatile Map<String, ToolCatalogEntry> entriesByExposedName = Map.of();

    public ToolCatalog(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public Future<List<Map<String, Object>>> listTools() {
        return buildCatalog().map(entries -> {
            List<Map<String, Object>> descriptors = new ArrayList<>(entries.size());
            for (ToolCatalogEntry entry : entries.values()) {
                descriptors.add(entry.descriptor());
            }
            return descriptors;
        });
    }

    public Optional<ToolAddress> resolve(String exposedName) {
        if (exposedName == null) {
            return Optional.empty();
        }
        ToolCatalogEntry entry = entriesByExposedName.get(exposedName);
        return entry == null ? Optional.empty() : Optional.of(entry.address());
    }

    private Future<Map<String, ToolCatalogEntry>> buildCatalog() {
        List<ToolProvider> providers = providerRegistry.providers();
        if (providers.isEmpty()) {
            entriesByExposedName = Map.of();
            return Future.succeededFuture(entriesByExposedName);
        }

        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (ToolProvider provider : providers) {
            futures.add(provider.listTools());
        }

        return Future.all(new ArrayList<>(futures)).map(composite -> {
            Map<String, ToolCatalogEntry> entries = new LinkedHashMap<>();

            for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
                ToolProvider provider = providers.get(providerIndex);
                String providerId = provider.providerId();
                List<Map<String, Object>> tools = composite.resultAt(providerIndex);
                if (tools == null) {
                    continue;
                }

                for (Map<String, Object> tool : tools) {
                    Object nameObj = tool.get("name");
                    if (!(nameObj instanceof String exposedName) || exposedName.isBlank()) {
                        continue;
                    }

                    String nativeToolName = provider.nativeToolName(exposedName);
                    ToolCatalogEntry entry = new ToolCatalogEntry(exposedName, providerId, nativeToolName, tool);
                    ToolCatalogEntry existing = entries.putIfAbsent(exposedName, entry);
                    if (existing != null) {
                        throw new IllegalStateException(
                                "Tool name collision for '" + exposedName + "' between providers '"
                                        + existing.providerId() + "' and '" + providerId + "'");
                    }
                }
            }

            Map<String, ToolCatalogEntry> snapshot = Map.copyOf(entries);
            entriesByExposedName = snapshot;
            return snapshot;
        });
    }
}

