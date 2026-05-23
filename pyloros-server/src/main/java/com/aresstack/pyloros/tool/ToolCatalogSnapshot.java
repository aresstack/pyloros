package com.aresstack.pyloros.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ToolCatalogSnapshot(
        Map<String, ToolCatalogEntry> toolsByExternalName,
        Map<ToolAddress, ToolCatalogEntry> toolsByAddress,
        Map<String, List<ToolCatalogEntry>> toolsByProviderId
) {

    private static final ToolCatalogSnapshot EMPTY = new ToolCatalogSnapshot(Map.of(), Map.of(), Map.of());

    public ToolCatalogSnapshot {
        toolsByExternalName = immutableMap(Objects.requireNonNull(toolsByExternalName, "toolsByExternalName must not be null"));
        toolsByAddress = immutableMap(Objects.requireNonNull(toolsByAddress, "toolsByAddress must not be null"));
        toolsByProviderId = immutableProviderMap(Objects.requireNonNull(toolsByProviderId, "toolsByProviderId must not be null"));
    }

    public static ToolCatalogSnapshot empty() {
        return EMPTY;
    }

    public Optional<ToolCatalogEntry> findByExternalName(String externalName) {
        if (externalName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolsByExternalName.get(externalName));
    }

    public List<Map<String, Object>> descriptors() {
        List<Map<String, Object>> descriptors = new ArrayList<>(toolsByExternalName.size());
        for (ToolCatalogEntry entry : toolsByExternalName.values()) {
            descriptors.add(entry.descriptor());
        }
        return descriptors;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, List<ToolCatalogEntry>> immutableProviderMap(Map<String, List<ToolCatalogEntry>> source) {
        LinkedHashMap<String, List<ToolCatalogEntry>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<ToolCatalogEntry>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}

