package com.aresstack.pyloros.config;

import com.aresstack.pyloros.acp.AcpExecutionConfiguration;
import com.aresstack.pyloros.acp.AcpProcessConfiguration;
import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import com.aresstack.pyloros.acp.AcpVirtualToolProvider;
import com.aresstack.pyloros.acp.AgentTaskRepository;
import com.aresstack.pyloros.acp.AgentToolViewValidator;
import com.aresstack.pyloros.acp.InMemoryAgentTaskRepository;
import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates ACP virtual tool providers from mcp.json configuration.
 * Invalid or non-startable providers are logged but do not crash the server.
 */
public final class AcpProviderFactory {

    private static final Logger log = LoggerFactory.getLogger(AcpProviderFactory.class);

    private AcpProviderFactory() {
    }

    public static List<ToolProvider> createProviders(List<AcpProviderJsonConfig> configs, Vertx vertx) {
        return createProviders(configs, vertx, "");
    }

    public static List<ToolProvider> createProviders(
            List<AcpProviderJsonConfig> configs,
            Vertx vertx,
            String injectedViewToken) {
        if (configs == null || configs.isEmpty()) {
            return List.of();
        }

        List<ToolProvider> providers = new ArrayList<>();
        Set<String> allAcpProviderIds = collectProviderIds(configs);
        Map<String, Set<String>> acpProviderIdsByExposedView = collectProviderIdsByExposedView(configs);

        for (AcpProviderJsonConfig jsonConfig : configs) {
            try {
                ToolProvider provider = createProvider(jsonConfig, allAcpProviderIds, acpProviderIdsByExposedView, vertx, injectedViewToken);
                providers.add(provider);
                log.info("[ACP-PROVIDER] registered provider={} prefix={} agentToolView={}",
                        jsonConfig.id(), jsonConfig.prefix(), jsonConfig.agentToolView());
            } catch (Exception exception) {
                log.error("[ACP-PROVIDER] failed to create provider={} reason={}",
                        jsonConfig.id(), exception.getMessage());
            }
        }

        return providers;
    }

    private static ToolProvider createProvider(
            AcpProviderJsonConfig jsonConfig,
            Set<String> allAcpProviderIds,
            Map<String, Set<String>> acpProviderIdsByExposedView,
            Vertx vertx,
            String injectedViewToken) {
        validateType(jsonConfig);
        AcpProviderConfiguration providerConfig = toProviderConfiguration(jsonConfig);
        AgentToolViewValidator.validate(providerConfig, allAcpProviderIds, acpProviderIdsByExposedView);

        AgentTaskRepository repository = new InMemoryAgentTaskRepository();
        return new AcpVirtualToolProvider(vertx, providerConfig, repository, injectedViewToken);
    }

    private static void validateType(AcpProviderJsonConfig jsonConfig) {
        if (jsonConfig.type() == null || !jsonConfig.type().equalsIgnoreCase("acp")) {
            throw new IllegalArgumentException("ACP provider type must be 'acp', got: " + jsonConfig.type());
        }
        if (jsonConfig.id() == null || jsonConfig.id().isBlank()) {
            throw new IllegalArgumentException("ACP provider id must not be blank");
        }
        if (jsonConfig.process() == null) {
            throw new IllegalArgumentException("ACP provider process configuration is required");
        }
        if (jsonConfig.process().command() == null || jsonConfig.process().command().isBlank()) {
            throw new IllegalArgumentException("ACP provider process.command is required");
        }
    }

    private static AcpProviderConfiguration toProviderConfiguration(AcpProviderJsonConfig jsonConfig) {
        AcpProcessConfiguration processConfig = new AcpProcessConfiguration(
                jsonConfig.process().command(),
                jsonConfig.process().args(),
                jsonConfig.process().workingDirectory(),
                jsonConfig.process().environment()
        );

        AcpExecutionConfiguration executionConfig = toExecutionConfig(jsonConfig.execution());

        String prefix = jsonConfig.prefix() != null ? jsonConfig.prefix() : jsonConfig.id() + "/";
        String agentToolView = jsonConfig.agentToolView() != null ? jsonConfig.agentToolView() : "agent";
        List<String> exposeInViews = jsonConfig.exposeInViews() != null ? jsonConfig.exposeInViews() : List.of("public");

        return new AcpProviderConfiguration(
                jsonConfig.id(),
                prefix,
                agentToolView,
                exposeInViews,
                processConfig,
                executionConfig
        );
    }

    private static AcpExecutionConfiguration toExecutionConfig(AcpProviderJsonConfig.AcpExecutionJsonConfig exec) {
        if (exec == null) {
            return new AcpExecutionConfiguration();
        }
        return new AcpExecutionConfiguration(
                exec.defaultTaskTimeoutSeconds() != null ? exec.defaultTaskTimeoutSeconds() : 900,
                exec.maxEventsPerTask() != null ? exec.maxEventsPerTask() : 1000,
                exec.maxEventTextChars() != null ? exec.maxEventTextChars() : 12000,
                exec.maxResultTextChars() != null ? exec.maxResultTextChars() : 24000
        );
    }

    private static Set<String> collectProviderIds(List<AcpProviderJsonConfig> configs) {
        Set<String> ids = new HashSet<>();
        for (AcpProviderJsonConfig config : configs) {
            if (isEligibleForCreation(config) && config.id() != null && !config.id().isBlank()) {
                ids.add(config.id().trim());
            }
        }
        return ids;
    }

    private static Map<String, Set<String>> collectProviderIdsByExposedView(List<AcpProviderJsonConfig> configs) {
        Map<String, Set<String>> providerIdsByView = new HashMap<>();
        for (AcpProviderJsonConfig config : configs) {
            if (!isEligibleForCreation(config)) {
                continue;
            }
            if (config.id() == null || config.id().isBlank()) {
                continue;
            }
            String providerId = config.id().trim();
            List<String> exposeInViews = config.exposeInViews() != null ? config.exposeInViews() : List.of("public");
            for (String exposeInView : exposeInViews) {
                if (exposeInView == null || exposeInView.isBlank()) {
                    continue;
                }
                providerIdsByView.computeIfAbsent(exposeInView.trim(), ignored -> new HashSet<>()).add(providerId);
            }
        }
        return providerIdsByView;
    }

    private static boolean isEligibleForCreation(AcpProviderJsonConfig config) {
        return config != null
                && config.type() != null
                && config.type().equalsIgnoreCase("acp")
                && config.id() != null
                && !config.id().isBlank()
                && config.process() != null
                && config.process().command() != null
                && !config.process().command().isBlank();
    }
}
