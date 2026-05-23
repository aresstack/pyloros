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
    private volatile ToolCatalogSnapshot snapshot = ToolCatalogSnapshot.empty();

    public ToolCatalog(ProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    public Future<List<Map<String, Object>>> listTools() {
        return refresh().map(ToolCatalogSnapshot::descriptors);
    }

    public Optional<ToolCatalogEntry> findByExternalName(String externalName) {
        return snapshot.findByExternalName(externalName);
    }

    public ToolCatalogSnapshot snapshot() {
        return snapshot;
    }

    private Future<ToolCatalogSnapshot> refresh() {
        List<ToolProvider> providers = providerRegistry.providers();
        if (providers.isEmpty()) {
            snapshot = ToolCatalogSnapshot.empty();
            return Future.succeededFuture(snapshot);
        }

        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (ToolProvider provider : providers) {
            futures.add(provider.listTools());
        }

        return Future.all(new ArrayList<>(futures)).map(composite -> {
            Map<String, ToolCatalogEntry> toolsByExternalName = new LinkedHashMap<>();
            Map<ToolAddress, ToolCatalogEntry> toolsByAddress = new LinkedHashMap<>();
            Map<String, List<ToolCatalogEntry>> toolsByProviderId = new LinkedHashMap<>();

            for (int providerIndex = 0; providerIndex < providers.size(); providerIndex++) {
                ToolProvider provider = providers.get(providerIndex);
                String providerId = provider.providerId();
                List<Map<String, Object>> tools = composite.resultAt(providerIndex);
                if (tools == null) {
                    continue;
                }

                for (Map<String, Object> tool : tools) {
                    ToolCatalogEntry entry = toEntry(provider, providerId, tool);
                    if (entry == null) {
                        continue;
                    }

                    ToolCatalogEntry existingExternal = toolsByExternalName.putIfAbsent(entry.externalName(), entry);
                    if (existingExternal != null) {
                        throw new IllegalStateException(
                                "Duplicate external tool name: " + entry.externalName());
                    }

                    ToolCatalogEntry existingAddress = toolsByAddress.putIfAbsent(entry.address(), entry);
                    if (existingAddress != null) {
                        throw new IllegalStateException(
                                "Duplicate tool address: providerId=" + entry.address().providerId()
                                        + ", upstreamToolName=" + entry.address().upstreamToolName());
                    }

                    toolsByProviderId.computeIfAbsent(providerId, ignored -> new ArrayList<>()).add(entry);
                }
            }

            ToolCatalogSnapshot refreshed = new ToolCatalogSnapshot(
                    toolsByExternalName,
                    toolsByAddress,
                    toolsByProviderId
            );
            snapshot = refreshed;
            return refreshed;
        });
    }

    private ToolCatalogEntry toEntry(ToolProvider provider, String providerId, Map<String, Object> tool) {
        if (tool == null) {
            return null;
        }

        Object nameObj = tool.get("name");
        if (!(nameObj instanceof String upstreamToolName) || upstreamToolName.isBlank()) {
            return null;
        }

        String externalName = provider.preservesUpstreamToolName()
                ? upstreamToolName
                : providerId + "__" + upstreamToolName;
        ToolAddress address = new ToolAddress(providerId, upstreamToolName);

        LinkedHashMap<String, Object> descriptor = new LinkedHashMap<>(tool);
        descriptor.put("name", externalName);
        return new ToolCatalogEntry(externalName, address, descriptor);
    }
}
