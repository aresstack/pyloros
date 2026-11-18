package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.provider.ProviderDescriptor;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.provider.ProviderType;
import io.vertx.core.Future;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ToolCatalog {

    private final ProviderRegistry providerRegistry;
    private final ToolNameFormatter toolNameFormatter;
    private final Set<ToolView> managedAgentViews;
    private final Set<ToolView> eagerManagedViews;
    private final Set<String> directAgentToolNames;
    private volatile ToolCatalogSnapshot snapshot = ToolCatalogSnapshot.empty();
    private final Map<ToolView, ToolCatalogSnapshot> snapshotsByView = new java.util.concurrent.ConcurrentHashMap<>();

    public ToolCatalog(ProviderRegistry providerRegistry) {
        this(providerRegistry, ToolNameFormatter.defaultFormatter());
    }

    public ToolCatalog(ProviderRegistry providerRegistry, ToolNameFormatter toolNameFormatter) {
        this(providerRegistry, toolNameFormatter, Set.of());
    }

    public ToolCatalog(ProviderRegistry providerRegistry, ToolNameFormatter toolNameFormatter,
                       Set<ToolView> managedAgentViews) {
        this(providerRegistry, toolNameFormatter, managedAgentViews, Set.of());
    }

    public ToolCatalog(ProviderRegistry providerRegistry, ToolNameFormatter toolNameFormatter,
                       Set<ToolView> managedAgentViews, Set<String> directAgentToolNames) {
        this(providerRegistry, toolNameFormatter, managedAgentViews, directAgentToolNames, managedAgentViews);
    }

    public ToolCatalog(ProviderRegistry providerRegistry, ToolNameFormatter toolNameFormatter,
                       Set<ToolView> managedAgentViews, Set<String> directAgentToolNames,
                       Set<ToolView> eagerManagedViews) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
        this.toolNameFormatter = Objects.requireNonNull(toolNameFormatter, "toolNameFormatter must not be null");
        this.managedAgentViews = Set.copyOf(Objects.requireNonNull(managedAgentViews, "managedAgentViews must not be null"));
        this.directAgentToolNames = Set.copyOf(Objects.requireNonNull(
                directAgentToolNames, "directAgentToolNames must not be null"));
        this.eagerManagedViews = Set.copyOf(Objects.requireNonNull(
                eagerManagedViews, "eagerManagedViews must not be null"));
        if (this.managedAgentViews.contains(ToolView.PUBLIC)) {
            throw new IllegalArgumentException("The public tool view cannot be a managed agent view");
        }
        if (!this.managedAgentViews.containsAll(this.eagerManagedViews)) {
            throw new IllegalArgumentException("Eager agent views must be registered managed views");
        }
    }

    public Future<List<Map<String, Object>>> listTools() {
        return listTools(ToolView.PUBLIC);
    }

    public Future<List<Map<String, Object>>> listTools(ToolView toolView) {
        return refresh(Objects.requireNonNull(toolView, "toolView must not be null"))
                .map(ToolCatalogSnapshot::descriptors);
    }

    public Optional<ToolCatalogEntry> findByExternalName(String externalName) {
        return snapshot.findByExternalName(externalName);
    }

    public Optional<ToolCatalogEntry> findByExternalName(String externalName, ToolView toolView) {
        Objects.requireNonNull(toolView, "toolView must not be null");
        ToolCatalogSnapshot viewSnapshot = snapshotsByView.get(toolView);
        if (viewSnapshot == null) {
            return Optional.empty();
        }
        return viewSnapshot.findByExternalName(externalName);
    }

    public ToolCatalogSnapshot snapshot() {
        return snapshot;
    }

    public ToolCatalogSnapshot cachedSnapshotForView(ToolView toolView) {
        Objects.requireNonNull(toolView, "toolView must not be null");
        return snapshotsByView.getOrDefault(toolView, ToolCatalogSnapshot.empty());
    }

    public Future<ToolCatalogSnapshot> snapshotForView(ToolView toolView) {
        return Future.succeededFuture(cachedSnapshotForView(toolView));
    }

    public Future<ToolCatalogSnapshot> refreshForView(ToolView toolView) {
        return refresh(Objects.requireNonNull(toolView, "toolView must not be null"));
    }

    private Future<ToolCatalogSnapshot> refresh(ToolView toolView) {
        List<ToolProvider> providers = providerRegistry.providers().stream()
                .filter(provider -> providerRegistry.findDescriptorById(provider.providerId())
                        .map(descriptor -> isVisibleInView(descriptor, toolView))
                        .orElse(false))
                .filter(provider -> isRelevantDirectToolProvider(provider, toolView))
                .toList();
        if (providers.isEmpty()) {
            ToolCatalogSnapshot emptySnapshot = ToolCatalogSnapshot.empty();
            snapshot = emptySnapshot;
            snapshotsByView.put(toolView, emptySnapshot);
            return Future.succeededFuture(emptySnapshot);
        }

        List<Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (ToolProvider provider : providers) {
            futures.add(provider.listTools(managedAgentViews.contains(toolView) ? ToolView.PUBLIC : toolView));
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
                    ToolCatalogEntry entry = toEntry(provider, providerId, tool, toolView);
                    if (entry == null || !isSelectedDirectTool(entry.externalName(), toolView)) {
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
            snapshotsByView.put(toolView, refreshed);
            return refreshed;
        });
    }

    private boolean isVisibleInView(ProviderDescriptor descriptor, ToolView toolView) {
        if (!managedAgentViews.contains(toolView)) {
            return descriptor.isExposedIn(toolView);
        }
        return descriptor.providerType() == ProviderType.MCP && descriptor.isExposedIn(ToolView.PUBLIC);
    }

    private boolean isRelevantDirectToolProvider(ToolProvider provider, ToolView toolView) {
        if (!eagerManagedViews.contains(toolView)
                || directAgentToolNames.isEmpty()
                || provider.preservesUpstreamToolName()) {
            return true;
        }
        String prefix = provider.providerId() + toolNameFormatter.separator();
        return directAgentToolNames.stream().anyMatch(name -> name.startsWith(prefix));
    }

    private boolean isSelectedDirectTool(String externalName, ToolView toolView) {
        return !eagerManagedViews.contains(toolView)
                || directAgentToolNames.isEmpty()
                || directAgentToolNames.contains(externalName);
    }

    private ToolCatalogEntry toEntry(ToolProvider provider, String providerId,
                                     Map<String, Object> tool, ToolView toolView) {
        if (tool == null) {
            return null;
        }

        Object nameObj = tool.get("name");
        if (!(nameObj instanceof String upstreamToolName) || upstreamToolName.isBlank()) {
            return null;
        }

        String externalName = provider.preservesUpstreamToolName()
                ? upstreamToolName
                : toolNameFormatter.externalName(providerId, upstreamToolName);
        ToolAddress address = new ToolAddress(providerId, upstreamToolName);

        LinkedHashMap<String, Object> descriptor = new LinkedHashMap<>(tool);
        descriptor.put("name", externalName);
        if (eagerManagedViews.contains(toolView)) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (descriptor.get("_meta") instanceof Map<?, ?> originalMetadata) {
                originalMetadata.forEach((key, value) -> {
                    if (key instanceof String name && value != null) {
                        metadata.put(name, value);
                    }
                });
            }
            metadata.put("antigravity/alwaysLoad", true);
            descriptor.put("_meta", Map.copyOf(metadata));
        }
        return new ToolCatalogEntry(externalName, address, descriptor);
    }
}
