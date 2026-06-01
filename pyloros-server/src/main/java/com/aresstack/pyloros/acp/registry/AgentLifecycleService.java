package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Manages the lifecycle of registry-installed ACP agents.
 * After each mutation (install/uninstall/update/enable/disable), the corresponding
 * ACP provider is dynamically registered or unregistered in the {@link ProviderRegistry}.
 */
public final class AgentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleService.class);

    private final InstalledAgentStore store;
    private final InstalledAgentAcpProviderFactory providerFactory;
    private final ProviderRegistry providerRegistry;

    public AgentLifecycleService(
            InstalledAgentStore store,
            InstalledAgentAcpProviderFactory providerFactory,
            ProviderRegistry providerRegistry) {
        this.store = Objects.requireNonNull(store);
        this.providerFactory = Objects.requireNonNull(providerFactory);
        this.providerRegistry = Objects.requireNonNull(providerRegistry);
    }

    /**
     * Registers providers for all currently enabled installed agents.
     * Called during startup to initialize registry-managed providers.
     */
    public void registerAllEnabled() {
        List<InstalledAgent> agents = store.listAll();
        for (InstalledAgent agent : agents) {
            if (agent.enabled() && agent.command() != null && !agent.command().isBlank()) {
                try {
                    ToolProvider provider = providerFactory.create(agent);
                    providerRegistry.register(provider);
                    log.info("[ACP-REGISTRY] registered provider for agent={}", agent.id());
                } catch (Exception e) {
                    log.error("[ACP-REGISTRY] failed to register provider for agent={}", agent.id(), e);
                }
            }
        }
    }

    public InstalledAgent install(InstalledAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        store.save(agent);
        if (agent.enabled() && agent.command() != null && !agent.command().isBlank()) {
            ToolProvider provider = providerFactory.create(agent);
            providerRegistry.register(provider);
            log.info("[ACP-REGISTRY] installed and registered agent={}", agent.id());
        } else {
            log.info("[ACP-REGISTRY] installed agent={} (disabled or no command)", agent.id());
        }
        return agent;
    }

    public boolean uninstall(String agentId) {
        boolean removed = store.remove(agentId);
        if (removed) {
            boolean unregistered = providerRegistry.unregister(agentId);
            log.info("[ACP-REGISTRY] uninstalled agent={} providerRemoved={}", agentId, unregistered);
        }
        return removed;
    }

    public Optional<InstalledAgent> enable(String agentId) {
        return store.findById(agentId).map(agent -> {
            InstalledAgent enabled = agent.withEnabled(true);
            store.save(enabled);
            if (enabled.command() != null && !enabled.command().isBlank()) {
                ToolProvider provider = providerFactory.create(enabled);
                providerRegistry.register(provider);
                log.info("[ACP-REGISTRY] enabled agent={}", agentId);
            }
            return enabled;
        });
    }

    public Optional<InstalledAgent> disable(String agentId) {
        return store.findById(agentId).map(agent -> {
            InstalledAgent disabled = agent.withEnabled(false);
            store.save(disabled);
            providerRegistry.unregister(agentId);
            log.info("[ACP-REGISTRY] disabled agent={}", agentId);
            return disabled;
        });
    }

    public Optional<InstalledAgent> configure(String agentId, InstalledAgent updated) {
        return store.findById(agentId).map(existing -> {
            store.save(updated);
            // Remove old provider and re-register if enabled
            providerRegistry.unregister(agentId);
            if (updated.enabled() && updated.command() != null && !updated.command().isBlank()) {
                ToolProvider provider = providerFactory.create(updated);
                providerRegistry.register(provider);
                log.info("[ACP-REGISTRY] reconfigured and re-registered agent={}", agentId);
            } else {
                log.info("[ACP-REGISTRY] reconfigured agent={} (disabled or no command)", agentId);
            }
            return updated;
        });
    }

    public Optional<InstalledAgent> getStatus(String agentId) {
        return store.findById(agentId);
    }

    public List<InstalledAgent> listAll() {
        return store.listAll();
    }
}
