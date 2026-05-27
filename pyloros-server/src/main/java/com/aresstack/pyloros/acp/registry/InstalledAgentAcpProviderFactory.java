package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.acp.AcpExecutionConfiguration;
import com.aresstack.pyloros.acp.AcpProcessConfiguration;
import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import com.aresstack.pyloros.acp.AgentToolViewValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generates managed {@link AcpProviderConfiguration} instances from installed ACP registry agents.
 *
 * <p>Only enabled agents are converted. Disabled agents are silently skipped.
 * Each agent receives:
 * <ul>
 *   <li>A default prefix of {@code <agentId>/} — enforced if the configured prefix is blank or root</li>
 *   <li>A non-public {@code agentToolView} (agents must not see the public view)</li>
 *   <li>{@code PYLOROS_MCP_URL} and optionally {@code PYLOROS_MCP_BEARER_TOKEN} injected into the process environment</li>
 * </ul>
 *
 * <p>Root-level tools (prefix {@code "/"}) are only allowed when explicitly configured.
 * Blank or null prefixes are rejected as invalid.
 *
 * <p>Validation considers the <em>full ACP provider universe</em>: callers must supply the existing
 * (non-registry) provider IDs and exposed-view map so that recursion protection covers cross-provider
 * collisions.
 *
 * <p>The existing {@link AgentToolViewValidator} is reused to reject recursive or colliding agent views.
 */
public final class InstalledAgentAcpProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(InstalledAgentAcpProviderFactory.class);

    private InstalledAgentAcpProviderFactory() {
    }

    /**
     * Structured result of {@link #createConfigurations}, containing both successfully generated
     * configurations and per-agent failure details.
     *
     * @param configurations successfully validated provider configurations
     * @param failures       per-agent failure reasons for enabled agents that could not be registered
     */
    public record Result(
            List<AcpProviderConfiguration> configurations,
            List<AgentFailure> failures
    ) {
        public Result {
            configurations = List.copyOf(Objects.requireNonNull(configurations, "configurations must not be null"));
            failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        }
    }

    /**
     * Describes a single agent that failed validation or configuration generation.
     *
     * @param agentId the installed agent ID
     * @param reason  human-readable failure reason
     */
    public record AgentFailure(String agentId, String reason) {
        public AgentFailure {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * Converts a list of installed agents into validated ACP provider configurations.
     * Disabled agents are filtered out. Returns a structured {@link Result} containing
     * generated configs and per-agent failures for enabled agents that could not be registered.
     *
     * <p>Validation uses the full ACP provider universe: the caller must supply existing
     * (non-registry) provider IDs and exposed-view mappings so that cross-provider recursion
     * is detected.
     *
     * @param agents                       all installed agents (enabled and disabled)
     * @param existingAcpProviderIds       provider IDs from non-registry ACP providers (e.g. mcp.json)
     * @param existingExposedViewMap       exposed-view → provider-IDs map from non-registry ACP providers
     * @param pylorosMcpUrl                the Pyloros MCP URL to inject (required, non-blank)
     * @param pylorosMcpToken              optional bearer token for Pyloros MCP authentication (may be null or blank)
     * @return structured result with generated configs and failures
     */
    public static Result createConfigurations(
            List<InstalledAgent> agents,
            Set<String> existingAcpProviderIds,
            Map<String, Set<String>> existingExposedViewMap,
            String pylorosMcpUrl,
            String pylorosMcpToken) {
        Objects.requireNonNull(agents, "agents must not be null");
        Objects.requireNonNull(existingAcpProviderIds, "existingAcpProviderIds must not be null");
        Objects.requireNonNull(existingExposedViewMap, "existingExposedViewMap must not be null");
        if (pylorosMcpUrl == null || pylorosMcpUrl.isBlank()) {
            throw new IllegalArgumentException("pylorosMcpUrl must not be null or blank");
        }

        List<InstalledAgent> enabledAgents = agents.stream()
                .filter(InstalledAgent::enabled)
                .toList();

        if (enabledAgents.isEmpty()) {
            return new Result(List.of(), List.of());
        }

        // Merge existing provider universe with registry agent IDs
        Set<String> allProviderIds = new HashSet<>(existingAcpProviderIds);
        for (InstalledAgent agent : enabledAgents) {
            allProviderIds.add(agent.agentId());
        }

        // Merge existing exposed-view map with registry agents (exposed in "public")
        Map<String, Set<String>> providerIdsByExposedView = new HashMap<>();
        existingExposedViewMap.forEach((view, ids) ->
                providerIdsByExposedView.put(view, new HashSet<>(ids)));
        for (InstalledAgent agent : enabledAgents) {
            providerIdsByExposedView.computeIfAbsent("public", ignored -> new HashSet<>()).add(agent.agentId());
        }

        List<AcpProviderConfiguration> configurations = new ArrayList<>();
        List<AgentFailure> failures = new ArrayList<>();

        for (InstalledAgent agent : enabledAgents) {
            try {
                validatePrefix(agent);
                AcpProviderConfiguration config = toProviderConfiguration(agent, pylorosMcpUrl, pylorosMcpToken);
                AgentToolViewValidator.validate(config, allProviderIds, providerIdsByExposedView);
                configurations.add(config);
                log.info("[ACP-REGISTRY-PROVIDER] registered agent={} prefix={} agentToolView={}",
                        agent.agentId(), config.prefix(), config.agentToolView());
            } catch (Exception e) {
                log.error("[ACP-REGISTRY-PROVIDER] failed to create provider for agent={} reason={}",
                        agent.agentId(), e.getMessage());
                failures.add(new AgentFailure(agent.agentId(), e.getMessage()));
            }
        }

        return new Result(configurations, failures);
    }

    /**
     * Validates the prefix of an installed agent.
     * The prefix must not be blank. Root prefix ({@code "/"}) is only allowed
     * as an explicit opt-in — it must literally be "/" to be accepted.
     * The default (and expected) prefix pattern is {@code <agentId>/}.
     */
    static void validatePrefix(InstalledAgent agent) {
        String prefix = agent.configuredPrefix();
        // configuredPrefix is already validated as non-blank by InstalledAgent record,
        // but we enforce the semantic: root "/" is explicit opt-in, anything else must end with "/"
        if ("/".equals(prefix)) {
            // Explicit root opt-in — allowed but noteworthy
            return;
        }
        if (!prefix.endsWith("/")) {
            throw new IllegalArgumentException(
                    "Prefix for agent '" + agent.agentId() + "' must end with '/' or be exactly '/' for root, got: '"
                            + prefix + "'");
        }
    }

    /**
     * Converts a single installed agent to an ACP provider configuration.
     * Does not perform cross-provider validation — use {@link #createConfigurations} for validated output.
     */
    static AcpProviderConfiguration toProviderConfiguration(
            InstalledAgent agent,
            String pylorosMcpUrl,
            String pylorosMcpToken) {
        String prefix = agent.configuredPrefix();
        String agentToolView = agent.agentToolView();

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PYLOROS_MCP_URL", pylorosMcpUrl);
        if (pylorosMcpToken != null && !pylorosMcpToken.isBlank()) {
            environment.put("PYLOROS_MCP_BEARER_TOKEN", pylorosMcpToken);
        }

        AcpProcessConfiguration processConfig = new AcpProcessConfiguration(
                agent.resolvedCommand(),
                agent.resolvedArgs(),
                null,
                environment
        );

        // Installed agents expose their tools in "public" (visible to users),
        // but the agent itself sees a non-public agentToolView (not "public")
        List<String> exposeInViews = List.of("public");

        return new AcpProviderConfiguration(
                agent.agentId(),
                prefix,
                agentToolView,
                exposeInViews,
                processConfig,
                new AcpExecutionConfiguration()
        );
    }
}
