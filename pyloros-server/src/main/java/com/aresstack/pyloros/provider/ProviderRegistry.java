package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {

    private final Map<String, ToolProvider> providersById;
    private final List<ToolProvider> providers;

    public ProviderRegistry(List<ToolProvider> providers) {
        LinkedHashMap<String, ToolProvider> registeredProviders = new LinkedHashMap<>();
        for (ToolProvider provider : providers == null ? List.<ToolProvider>of() : providers) {
            registerProvider(registeredProviders, provider);
        }

        this.providersById = Collections.unmodifiableMap(registeredProviders);
        this.providers = List.copyOf(new ArrayList<>(registeredProviders.values()));
    }

    public List<ToolProvider> providers() {
        return providers;
    }

    public Map<String, ToolProvider> providersById() {
        return providersById;
    }

    private static void registerProvider(Map<String, ToolProvider> registeredProviders, ToolProvider provider) {
        String providerId = provider.providerId();
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank for provider " + provider.getClass().getName());
        }

        ToolProvider existing = registeredProviders.putIfAbsent(providerId, provider);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate provider id: " + providerId);
        }
    }

    public Optional<ToolProvider> findById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersById.get(providerId));
    }
}
