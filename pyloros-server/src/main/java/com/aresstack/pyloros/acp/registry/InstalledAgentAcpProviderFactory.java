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
 *   <li>A default prefix of {@code <agentId>/} unless explicitly configured otherwise</li>
 *   <li>A non-public {@code agentToolView} (agents must not see the public view)</li>
 *   <li>{@code PYLOROS_MCP_URL} and optionally {@code PYLOROS_MCP_BEARER_TOKEN} injected into the process environment</li>
 * </ul>
 *
 * <p>Root-level tools (empty prefix or {@code "/"}) are only allowed when explicitly configured
 * via {@link InstalledAgent#configuredPrefix()}.
 *
 * <p>The existing {@link AgentToolViewValidator} is reused to reject recursive or colliding agent views.
 */
public final class InstalledAgentAcpProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(InstalledAgentAcpProviderFactory.class);

    private InstalledAgentAcpProviderFactory() {
    }

    /**
     * Converts a list of installed agents into validated ACP provider configurations.
     * Disabled agents are filtered out. Invalid or recursive configurations are logged and skipped.
     *
     * @param agents           all installed agents (enabled and disabled)
     * @param pylorosMcpUrl    the Pyloros MCP URL to inject (required, non-blank)
     * @param pylorosMcpToken  optional bearer token for Pyloros MCP authentication (may be null or blank)
     * @return list of valid provider configurations for enabled agents
     */
    public static List<AcpProviderConfiguration> createConfigurations(
            List<InstalledAgent> agents,
            String pylorosMcpUrl,
            String pylorosMcpToken) {
        Objects.requireNonNull(agents, "agents must not be null");
        if (pylorosMcpUrl == null || pylorosMcpUrl.isBlank()) {
            throw new IllegalArgumentException("pylorosMcpUrl must not be null or blank");
        }

        List<InstalledAgent> enabledAgents = agents.stream()
                .filter(InstalledAgent::enabled)
                .toList();

        if (enabledAgents.isEmpty()) {
            return List.of();
        }

        Set<String> allProviderIds = collectProviderIds(enabledAgents);
        Map<String, Set<String>> providerIdsByExposedView = collectProviderIdsByExposedView(enabledAgents);

        List<AcpProviderConfiguration> configurations = new ArrayList<>();
        for (InstalledAgent agent : enabledAgents) {
            try {
                AcpProviderConfiguration config = toProviderConfiguration(agent, pylorosMcpUrl, pylorosMcpToken);
                AgentToolViewValidator.validate(config, allProviderIds, providerIdsByExposedView);
                configurations.add(config);
                log.info("[ACP-REGISTRY-PROVIDER] registered agent={} prefix={} agentToolView={}",
                        agent.agentId(), config.prefix(), config.agentToolView());
            } catch (Exception e) {
                log.error("[ACP-REGISTRY-PROVIDER] failed to create provider for agent={} reason={}",
                        agent.agentId(), e.getMessage());
            }
        }

        return List.copyOf(configurations);
    }

    /**
     * Converts a single installed agent to an ACP provider configuration.
     * Does not perform validation — use {@link #createConfigurations} for validated output.
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

    private static Set<String> collectProviderIds(List<InstalledAgent> agents) {
        Set<String> ids = new HashSet<>();
        for (InstalledAgent agent : agents) {
            ids.add(agent.agentId());
        }
        return ids;
    }

    private static Map<String, Set<String>> collectProviderIdsByExposedView(List<InstalledAgent> agents) {
        Map<String, Set<String>> providerIdsByView = new HashMap<>();
        for (InstalledAgent agent : agents) {
            // Installed agents are always exposed in "public"
            providerIdsByView.computeIfAbsent("public", ignored -> new HashSet<>()).add(agent.agentId());
        }
        return providerIdsByView;
    }
}
