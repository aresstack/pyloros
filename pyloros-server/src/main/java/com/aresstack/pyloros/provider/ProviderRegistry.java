package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ProviderRegistry {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile Map<String, ToolProvider> providersById;
    private volatile Map<String, ProviderDescriptor> descriptorsById;
    private volatile Map<String, ProviderStatus> statusesById;
    private volatile List<ToolProvider> providers;
    private volatile List<ProviderDescriptor> descriptors;

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

    /**
     * Dynamically registers a new provider. If a provider with the same id already exists,
     * it is replaced.
     */
    public void register(ToolProvider provider) {
        String providerId = provider.providerId();
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank for provider " + provider.getClass().getName());
        }

        lock.writeLock().lock();
        try {
            LinkedHashMap<String, ToolProvider> newProvidersById = new LinkedHashMap<>(providersById);
            LinkedHashMap<String, ProviderDescriptor> newDescriptorsById = new LinkedHashMap<>(descriptorsById);
            LinkedHashMap<String, ProviderStatus> newStatusesById = new LinkedHashMap<>(statusesById);

            newProvidersById.put(providerId, provider);
            newDescriptorsById.put(providerId, ProviderDescriptor.from(provider));
            newStatusesById.put(providerId, ProviderStatus.AVAILABLE);

            this.providersById = Collections.unmodifiableMap(newProvidersById);
            this.descriptorsById = Collections.unmodifiableMap(newDescriptorsById);
            this.statusesById = Collections.unmodifiableMap(newStatusesById);
            this.providers = List.copyOf(new ArrayList<>(newProvidersById.values()));
            this.descriptors = List.copyOf(new ArrayList<>(newDescriptorsById.values()));
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Dynamically unregisters a provider by id. Returns true if the provider was present and removed.
     */
    public boolean unregister(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return false;
        }

        lock.writeLock().lock();
        try {
            if (!providersById.containsKey(providerId)) {
                return false;
            }

            LinkedHashMap<String, ToolProvider> newProvidersById = new LinkedHashMap<>(providersById);
            LinkedHashMap<String, ProviderDescriptor> newDescriptorsById = new LinkedHashMap<>(descriptorsById);
            LinkedHashMap<String, ProviderStatus> newStatusesById = new LinkedHashMap<>(statusesById);

            newProvidersById.remove(providerId);
            newDescriptorsById.remove(providerId);
            newStatusesById.remove(providerId);

            this.providersById = Collections.unmodifiableMap(newProvidersById);
            this.descriptorsById = Collections.unmodifiableMap(newDescriptorsById);
            this.statusesById = Collections.unmodifiableMap(newStatusesById);
            this.providers = List.copyOf(new ArrayList<>(newProvidersById.values()));
            this.descriptors = List.copyOf(new ArrayList<>(newDescriptorsById.values()));
            return true;
        } finally {
            lock.writeLock().unlock();
        }
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
    