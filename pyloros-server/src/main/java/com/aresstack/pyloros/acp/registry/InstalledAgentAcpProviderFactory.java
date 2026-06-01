package com.aresstack.pyloros.acp.registry;

import com.aresstack.pyloros.acp.AcpExecutionConfiguration;
import com.aresstack.pyloros.acp.AcpProcessConfiguration;
import com.aresstack.pyloros.acp.AcpProviderConfiguration;
import com.aresstack.pyloros.acp.AcpVirtualToolProvider;
import com.aresstack.pyloros.acp.InMemoryAgentTaskRepository;
import com.aresstack.pyloros.tool.ToolProvider;
import io.vertx.core.Vertx;

import java.util.Map;
import java.util.Objects;

/**
 * Creates an {@link AcpVirtualToolProvider} from an {@link InstalledAgent} record.
 */
public final class InstalledAgentAcpProviderFactory {

    private final Vertx vertx;
    private final String injectedViewToken;

    public InstalledAgentAcpProviderFactory(Vertx vertx, String injectedViewToken) {
        this.vertx = Objects.requireNonNull(vertx, "vertx must not be null");
        this.injectedViewToken = injectedViewToken == null ? "" : injectedViewToken;
    }

    public ToolProvider create(InstalledAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        if (agent.command() == null || agent.command().isBlank()) {
            throw new IllegalArgumentException("Agent command must not be blank for agent: " + agent.id());
        }

        AcpProcessConfiguration processConfig = new AcpProcessConfiguration(
                agent.command(),
                agent.args(),
                null,
                agent.environment()
        );

        AcpProviderConfiguration providerConfig = new AcpProviderConfiguration(
                agent.id(),
                agent.prefix(),
                agent.agentToolView(),
                agent.exposeInViews(),
                processConfig,
                new AcpExecutionConfiguration()
        );

        return new AcpVirtualToolProvider(vertx, providerConfig, new InMemoryAgentTaskRepository(), injectedViewToken);
    }
}
