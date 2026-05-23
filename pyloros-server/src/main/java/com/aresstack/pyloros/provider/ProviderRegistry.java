package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {

    private final List<ToolProvider> providers;
    private final Map<String, ToolProvider> providersById;

    public ProviderRegistry(List<ToolProvider> providers) {
        this.providers = List.copyOf(providers == null ? List.of() : providers);

        Map<String, ToolProvider> map = new LinkedHashMap<>();
        for (ToolProvider provider : this.providers) {
            String providerId = provider.providerId();
            if (providerId == null || providerId.isBlank()) {
                throw new IllegalArgumentException("Provider id must not be blank for provider " + provider.getClass().getName());
            }
            if (map.containsKey(providerId)) {
                throw new IllegalArgumentException("Duplicate provider id: " + providerId);
            }
            map.put(providerId, provider);
        }
        this.providersById = Map.copyOf(map);
    }

    public List<ToolProvider> providers() {
        return providers;
    }

    public Optional<ToolProvider> findById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersById.get(providerId));
    }
}

