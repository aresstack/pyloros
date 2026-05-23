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
    private final Map<String, ProviderDescriptor> descriptorsById;
    private final Map<String, ProviderStatus> statusesById;
    private final List<ToolProvider> providers;
    private final List<ProviderDescriptor> descriptors;

    public ProviderRegistry(List<ToolProvider> providers) {
        LinkedHashMap<String, ToolProvider> registeredProviders = new LinkedHashMap<>();
        LinkedHashMap<String, ProviderDescriptor> registeredDescriptors = new LinkedHashMap<>();
        LinkedHashMap<String, ProviderStatus> registeredStatuses = new LinkedHashMap<>();
        for (ToolProvider provider : providers == null ? List.<ToolProvider>of() : providers) {
            registerProvider(registeredProviders, registeredDescriptors, registeredStatuses, provider);
        }

        this.providersById = Collections.unmodifiableMap(registeredProviders);
        this.descriptorsById = Collections.unmodifiableMap(registeredDescriptors);
        this.statusesById = Collections.unmodifiableMap(registeredStatuses);
        this.providers = List.copyOf(new ArrayList<>(registeredProviders.values()));
        this.descriptors = List.copyOf(new ArrayList<>(registeredDescriptors.values()));
    }

    public List<ToolProvider> providers() {
        return providers;
    }

    public Map<String, ToolProvider> providersById() {
        return providersById;
    }

    public List<ProviderDescriptor> descriptors() {
        return descriptors;
    }

    public Map<String, ProviderDescriptor> descriptorsById() {
        return descriptorsById;
    }

    public Map<String, ProviderStatus> statusesById() {
        return statusesById;
    }

    public Optional<ToolProvider> findById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersById.get(providerId));
    }

    public Optional<ProviderDescriptor> findDescriptorById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptorsById.get(providerId));
    }

    public ProviderStatus status(String providerId) {
        if (providerId == null) {
            return ProviderStatus.UNKNOWN;
        }
        return statusesById.getOrDefault(providerId, ProviderStatus.UNKNOWN);
    }

    private static void registerProvider(
            Map<String, ToolProvider> registeredProviders,
            Map<String, ProviderDescriptor> registeredDescriptors,
            Map<String, ProviderStatus> registeredStatuses,
            ToolProvider provider) {
        String providerId = provider.providerId();
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank for provider " + provider.getClass().getName());
        }

        ToolProvider existing = registeredProviders.putIfAbsent(providerId, provider);
        if (existing != null) {
            throw new IllegalArgumentException("Duplicate provider id: " + providerId);
        }

        registeredDescriptors.put(providerId, ProviderDescriptor.from(provider));
        registeredStatuses.put(providerId, ProviderStatus.AVAILABLE);
    }
}
    