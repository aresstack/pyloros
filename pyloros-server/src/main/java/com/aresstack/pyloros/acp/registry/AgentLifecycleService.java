package com.aresstack.pyloros.acp.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Orchestrates install, update, and uninstall lifecycle operations for ACP registry agents.
 *
 * <p>Design principles:
 * <ul>
 *   <li>No automatic installation or execution without explicit user decision</li>
 *   <li>Existing installations are never silently overwritten</li>
 *   <li>Failed updates attempt rollback to the previous state</li>
 *   <li>All operations are audit-logged without exposing secrets</li>
 *   <li>Binary distributions are materialized (downloaded/extracted) under the install path</li>
 * </ul>
 */
public final class AgentLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleService.class);

    private final AgentStoreOperations store;
    private final DistributionResolver resolver;
    private final AgentInstaller installer;

    public AgentLifecycleService(AgentStoreOperations store, DistributionResolver resolver, AgentInstaller installer) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
        this.installer = Objects.requireNonNull(installer, "installer must not be null");
    }

    /**
     * Installs a registry agent. Rejects the operation if the agent is already installed.
     * For binary distributions, downloads and extracts the archive before recording install.
     *
     * @param registryAgent the agent metadata from the registry
     * @return structured lifecycle result
     */
    public AgentLifecycleResult install(RegistryAgent registryAgent) {
        Objects.requireNonNull(registryAgent, "registryAgent must not be null");
        String agentId = registryAgent.id();

        log.info("[LIFECYCLE] install requested agentId={} version={}", sanitize(agentId), sanitize(registryAgent.version()));

        Optional<InstalledAgent> existing = store.findById(agentId);
        if (existing.isPresent()) {
            log.warn("[LIFECYCLE] install rejected agentId={} reason=already_installed installedVersion={}",
                    sanitize(agentId), sanitize(existing.get().installedVersion()));
            return new AgentLifecycleResult.Rejected(
                    agentId,
                    "Agent '" + agentId + "' is already installed (version " + existing.get().installedVersion() + ")",
                    AgentLifecycleResult.RejectionKind.ALREADY_INSTALLED);
        }

        DistributionSpec spec;
        try {
            spec = toDistributionSpec(registryAgent);
        } catch (UnsupportedDistributionException e) {
            log.warn("[LIFECYCLE] install rejected agentId={} reason=unsupported_distribution", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId, e.getMessage(), AgentLifecycleResult.RejectionKind.UNSUPPORTED_DISTRIBUTION);
        }

        InstallPlan plan;
        try {
            plan = resolver.resolve(spec);
        } catch (UnsupportedPlatformException e) {
            log.warn("[LIFECYCLE] install rejected agentId={} reason=unsupported_platform", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId, e.getMessage(), AgentLifecycleResult.RejectionKind.UNSUPPORTED_PLATFORM);
        }

        // Materialize the installation (downloads/extracts for binary; no-op for npx/uvx)
        try {
            installer.materialize(plan);
        } catch (AgentInstallException e) {
            log.error("[LIFECYCLE] install failed agentId={} reason={}", sanitize(agentId), e.getMessage());
            return new AgentLifecycleResult.Failed(
                    agentId, e.getMessage(), false, "Installation materialization failed");
        }

        Instant now = Instant.now();
        InstalledAgent agent = new InstalledAgent(
                agentId,
                registryAgent.version(),
                spec.type().name().toLowerCase(),
                plan.command(),
                plan.args(),
                plan.installPath(),
                registryAgent.version(),
                registryAgent.license() != null ? registryAgent.license() : "",
                agentId + "/",
                agentId + "-agent-view",
                true,
                now,
                now
        );

        store.save(agent);

        log.info("[LIFECYCLE] install completed agentId={} version={} distributionType={} installPath={}",
                sanitize(agentId), sanitize(agent.installedVersion()),
                sanitize(agent.distributionType()), sanitize(agent.installPath()));

        return new AgentLifecycleResult.Success(
                agent,
                AgentLifecycleResult.Action.INSTALLED,
                "Agent '" + agentId + "' installed successfully (version " + agent.installedVersion() + ")");
    }

    /**
     * Updates an installed agent to the version in the registry.
     * If the update fails after modifying state, rolls back to the previous installation.
     *
     * @param registryAgent the agent metadata from the registry (newer version)
     * @return structured lifecycle result
     */
    public AgentLifecycleResult update(RegistryAgent registryAgent) {
        Objects.requireNonNull(registryAgent, "registryAgent must not be null");
        String agentId = registryAgent.id();

        log.info("[LIFECYCLE] update requested agentId={} registryVersion={}",
                sanitize(agentId), sanitize(registryAgent.version()));

        Optional<InstalledAgent> existing = store.findById(agentId);
        if (existing.isEmpty()) {
            log.warn("[LIFECYCLE] update rejected agentId={} reason=not_installed", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId,
                    "Agent '" + agentId + "' is not installed",
                    AgentLifecycleResult.RejectionKind.NOT_INSTALLED);
        }

        InstalledAgent previousAgent = existing.get();
        if (previousAgent.installedVersion().equals(registryAgent.version())) {
            log.info("[LIFECYCLE] update rejected agentId={} reason=already_up_to_date version={}",
                    sanitize(agentId), sanitize(previousAgent.installedVersion()));
            return new AgentLifecycleResult.Rejected(
                    agentId,
                    "Agent '" + agentId + "' is already at version " + previousAgent.installedVersion(),
                    AgentLifecycleResult.RejectionKind.ALREADY_UP_TO_DATE);
        }

        DistributionSpec spec;
        try {
            spec = toDistributionSpec(registryAgent);
        } catch (UnsupportedDistributionException e) {
            log.warn("[LIFECYCLE] update rejected agentId={} reason=unsupported_distribution", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId, e.getMessage(), AgentLifecycleResult.RejectionKind.UNSUPPORTED_DISTRIBUTION);
        }

        InstallPlan plan;
        try {
            plan = resolver.resolve(spec);
        } catch (UnsupportedPlatformException e) {
            log.warn("[LIFECYCLE] update rejected agentId={} reason=unsupported_platform", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId, e.getMessage(), AgentLifecycleResult.RejectionKind.UNSUPPORTED_PLATFORM);
        }

        // Materialize the new version (downloads/extracts for binary; no-op for npx/uvx)
        try {
            installer.materialize(plan);
        } catch (AgentInstallException e) {
            log.error("[LIFECYCLE] update materialization failed agentId={} reason={}, no state change",
                    sanitize(agentId), e.getMessage());
            return new AgentLifecycleResult.Failed(
                    agentId, e.getMessage(), true,
                    "Materialization failed before state change; previous version preserved");
        }

        // Attempt the update — on failure, rollback to previous state
        try {
            Instant now = Instant.now();
            InstalledAgent updatedAgent = new InstalledAgent(
                    agentId,
                    registryAgent.version(),
                    spec.type().name().toLowerCase(),
                    plan.command(),
                    plan.args(),
                    plan.installPath(),
                    registryAgent.version(),
                    registryAgent.license() != null ? registryAgent.license() : "",
                    previousAgent.configuredPrefix(),
                    previousAgent.agentToolView(),
                    previousAgent.enabled(),
                    previousAgent.installedAt(),
                    now
            );

            store.save(updatedAgent);

            log.info("[LIFECYCLE] update completed agentId={} previousVersion={} newVersion={}",
                    sanitize(agentId), sanitize(previousAgent.installedVersion()),
                    sanitize(updatedAgent.installedVersion()));

            return new AgentLifecycleResult.Success(
                    updatedAgent,
                    AgentLifecycleResult.Action.UPDATED,
                    "Agent '" + agentId + "' updated from " + previousAgent.installedVersion()
                            + " to " + updatedAgent.installedVersion());
        } catch (Exception e) {
            log.error("[LIFECYCLE] update failed agentId={} reason={}, attempting rollback",
                    sanitize(agentId), e.getMessage());
            return rollback(agentId, previousAgent, e.getMessage());
        }
    }

    /**
     * Uninstalls an agent by removing it from the store and cleaning up materialized files.
     * Returns a structured error if the agent is not installed.
     *
     * @param agentId the agent to uninstall
     * @return structured lifecycle result
     */
    public AgentLifecycleResult uninstall(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");

        log.info("[LIFECYCLE] uninstall requested agentId={}", sanitize(agentId));

        Optional<InstalledAgent> existing = store.findById(agentId);
        if (existing.isEmpty()) {
            log.warn("[LIFECYCLE] uninstall rejected agentId={} reason=not_installed", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId,
                    "Agent '" + agentId + "' is not installed",
                    AgentLifecycleResult.RejectionKind.NOT_INSTALLED);
        }

        InstalledAgent agent = existing.get();

        // Remove materialized files for binary distributions
        try {
            InstallPlan removalPlan = new InstallPlan(
                    agent.resolvedCommand(),
                    agent.resolvedArgs(),
                    agent.installPath(),
                    Map.of("distributionType", agent.distributionType())
            );
            installer.remove(removalPlan);
        } catch (AgentInstallException e) {
            log.warn("[LIFECYCLE] uninstall file removal failed agentId={} reason={}, continuing with store removal",
                    sanitize(agentId), e.getMessage());
        }

        Optional<InstalledAgent> removed = store.remove(agentId);
        if (removed.isEmpty()) {
            log.warn("[LIFECYCLE] uninstall rejected agentId={} reason=not_installed", sanitize(agentId));
            return new AgentLifecycleResult.Rejected(
                    agentId,
                    "Agent '" + agentId + "' is not installed",
                    AgentLifecycleResult.RejectionKind.NOT_INSTALLED);
        }

        log.info("[LIFECYCLE] uninstall completed agentId={} previousVersion={}",
                sanitize(agentId), sanitize(removed.get().installedVersion()));

        return new AgentLifecycleResult.Success(
                removed.get(),
                AgentLifecycleResult.Action.UNINSTALLED,
                "Agent '" + agentId + "' uninstalled successfully");
    }

    /**
     * Checks whether an update is available for an installed agent.
     *
     * @param agentId       the installed agent ID
     * @param registryAgent the registry agent metadata to compare against
     * @return true if the registry version differs from the installed version
     */
    public boolean isUpdateAvailable(String agentId, RegistryAgent registryAgent) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(registryAgent, "registryAgent must not be null");
        Optional<InstalledAgent> installed = store.findById(agentId);
        if (installed.isEmpty()) {
            return false;
        }
        return !installed.get().installedVersion().equals(registryAgent.version());
    }

    private AgentLifecycleResult rollback(String agentId, InstalledAgent previousAgent, String failureReason) {
        try {
            store.save(previousAgent);
            log.info("[LIFECYCLE] rollback succeeded agentId={} restoredVersion={}",
                    sanitize(agentId), sanitize(previousAgent.installedVersion()));
            return new AgentLifecycleResult.Failed(
                    agentId,
                    failureReason,
                    true,
                    "Rolled back to version " + previousAgent.installedVersion());
        } catch (Exception rollbackEx) {
            log.error("[LIFECYCLE] rollback failed agentId={} rollbackError={}",
                    sanitize(agentId), rollbackEx.getMessage());
            return new AgentLifecycleResult.Failed(
                    agentId,
                    failureReason,
                    false,
                    "Rollback also failed: " + rollbackEx.getMessage());
        }
    }

    private static DistributionSpec toDistributionSpec(RegistryAgent registryAgent) {
        RegistryDistribution dist = registryAgent.distribution();
        if (dist == null) {
            throw new UnsupportedDistributionException("null");
        }

        if (dist.npx() != null) {
            return new DistributionSpec(
                    DistributionType.NPX,
                    dist.npx().packageName(),
                    dist.npx().args() != null ? dist.npx().args() : List.of(),
                    Map.of()
            );
        }

        if (dist.uvx() != null) {
            return new DistributionSpec(
                    DistributionType.UVX,
                    dist.uvx().packageName(),
                    dist.uvx().args() != null ? dist.uvx().args() : List.of(),
                    Map.of()
            );
        }

        if (dist.binary() != null && !dist.binary().isEmpty()) {
            Map<String, DistributionSpec.BinaryTarget> targets = new java.util.LinkedHashMap<>();
            for (var entry : dist.binary().entrySet()) {
                RegistryDistribution.BinaryTarget bt = entry.getValue();
                targets.put(entry.getKey(), new DistributionSpec.BinaryTarget(
                        bt.archive(),
                        bt.cmd(),
                        bt.args() != null ? bt.args() : List.of()
                ));
            }
            String packageRef = registryAgent.id();
            return new DistributionSpec(
                    DistributionType.BINARY,
                    packageRef,
                    List.of(),
                    targets
            );
        }

        throw new UnsupportedDistributionException("none (no npx, uvx, or binary distribution found)");
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replaceAll("[^a-zA-Z0-9._\\-/@=]", "_");
    }
}
