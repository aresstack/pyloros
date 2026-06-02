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

    private volatile Snapshot snapshot;

    public ProviderRegistry(List<ToolProvider> providers) {
        LinkedHashMap<String, ToolProvider> registeredProviders = new LinkedHashMap<>();
        LinkedHashMap<String, ProviderDescriptor> registeredDescriptors = new LinkedHashMap<>();
        LinkedHashMap<String, ProviderStatus> registeredStatuses = new LinkedHashMap<>();
        for (ToolProvider provider : providers == null ? List.<ToolProvider>of() : providers) {
            registerProvider(registeredProviders, registeredDescriptors, registeredStatuses, provider);
        }

        this.snapshot = Snapshot.from(registeredProviders, registeredDescriptors, registeredStatuses);
    }

    public List<ToolProvider> providers() {
        return snapshot.providers();
    }

    public Map<String, ToolProvider> providersById() {
        return snapshot.providersById();
    }

    public List<ProviderDescriptor> descriptors() {
        return snapshot.descriptors();
    }

    public Map<String, ProviderDescriptor> descriptorsById() {
        return snapshot.descriptorsById();
    }

    public Map<String, ProviderStatus> statusesById() {
        return snapshot.statusesById();
    }

    public Optional<ToolProvider> findById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.providersById().get(providerId));
    }

    public Optional<ProviderDescriptor> findDescriptorById(String providerId) {
        if (providerId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(snapshot.descriptorsById().get(providerId));
    }

    public ProviderStatus status(String providerId) {
        if (providerId == null) {
            return ProviderStatus.UNKNOWN;
        }
        return snapshot.statusesById().getOrDefault(providerId, ProviderStatus.UNKNOWN);
    }

    /**
     * Dynamically registers a new provider. If a provider with the same id already exists,
     * it is replaced.
     */
    public void register(ToolProvider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        String providerId = provider.providerId();
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Provider id must not be blank for provider " + provider.getClass().getName());
        }

        lock.writeLock().lock();
        try {
            Snapshot current = snapshot;
            LinkedHashMap<String, ToolProvider> newProvidersById = new LinkedHashMap<>(current.providersById());
            LinkedHashMap<String, ProviderDescriptor> newDescriptorsById = new LinkedHashMap<>(current.descriptorsById());
            LinkedHashMap<String, ProviderStatus> newStatusesById = new LinkedHashMap<>(current.statusesById());

            newProvidersById.put(providerId, provider);
            newDescriptorsById.put(providerId, ProviderDescriptor.from(provider));
            newStatusesById.put(providerId, ProviderStatus.AVAILABLE);

            this.snapshot = Snapshot.from(newProvidersById, newDescriptorsById, newStatusesById);
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
            Snapshot current = snapshot;
            if (!current.providersById().containsKey(providerId)) {
                return false;
            }

            LinkedHashMap<String, ToolProvider> newProvidersById = new LinkedHashMap<>(current.providersById());
            LinkedHashMap<String, ProviderDescriptor> newDescriptorsById = new LinkedHashMap<>(current.descriptorsById());
            LinkedHashMap<String, ProviderStatus> newStatusesById = new LinkedHashMap<>(current.statusesById());

            newProvidersById.remove(providerId);
            newDescriptorsById.remove(providerId);
            newStatusesById.remove(providerId);

            this.snapshot = Snapshot.from(newProvidersById, newDescriptorsById, newStatusesById);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private record Snapshot(
            Map<String, ToolProvider> providersById,
            Map<String, ProviderDescriptor> descriptorsById,
            Map<String, ProviderStatus> statusesById,
            List<ToolProvider> providers,
            List<ProviderDescriptor> descriptors
    ) {
        private static Snapshot from(
                LinkedHashMap<String, ToolProvider> providersById,
                LinkedHashMap<String, ProviderDescriptor> descriptorsById,
                LinkedHashMap<String, ProviderStatus> statusesById) {
            return new Snapshot(
                    Collections.unmodifiableMap(new LinkedHashMap<>(providersById)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(descriptorsById)),
                    Collections.unmodifiableMap(new LinkedHashMap<>(statusesById)),
                    List.copyOf(new ArrayList<>(providersById.values())),
                    List.copyOf(new ArrayList<>(descriptorsById.values())));
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
    