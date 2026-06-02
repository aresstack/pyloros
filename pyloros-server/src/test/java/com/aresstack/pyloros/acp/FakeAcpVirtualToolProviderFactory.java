package com.aresstack.pyloros.acp;

import io.vertx.core.Vertx;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Test fixture that wires {@link AcpVirtualToolProvider} to an in-process {@link FakeAcpAgent}.
 */
public final class FakeAcpVirtualToolProviderFactory {

    private FakeAcpVirtualToolProviderFactory() {
    }

    public static RunningProvider create(
            AcpProviderConfiguration configuration,
            String behavior,
            String injectedViewToken) throws IOException {
        Objects.requireNonNull(configuration, "configuration must not be null");
        FakeAcpAgent agent = new FakeAcpAgent();
        agent.setBehavior(behavior);
        agent.start();

        Vertx vertx = Vertx.vertx();
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AcpProcessLauncher launcher = new AcpProcessLauncher() {
            @Override
            public AcpProcessHandle launch(AcpProcessConfiguration ignored) {
                return agent.processHandle();
            }
        };
        AcpVirtualToolProvider provider = new AcpVirtualToolProvider(
                vertx,
                configuration,
                repository,
                launcher,
                new AcpAuditLogger(),
                new AcpEventMapper(),
                injectedViewToken);
        return new RunningProvider(provider, repository, vertx, agent);
    }

    public record RunningProvider(
            AcpVirtualToolProvider provider,
            InMemoryAgentTaskRepository repository,
            Vertx vertx,
            FakeAcpAgent agent
    ) implements AutoCloseable {

        @Override
        public void close() {
            agent.close();
            vertx.close().toCompletionStage().toCompletableFuture().orTimeout(5, TimeUnit.SECONDS).join();
        }
    }
}
