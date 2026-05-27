package com.aresstack.pyloros.manageragent;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagerAgentBootstrapVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgentBootstrapVerticle.class);
    private Thread protocolThread;

    @Override
    public void start() {
        ManagerAgentProtocolServer protocolServer = new ManagerAgentProtocolServer(System.in, System.out);
        protocolThread = Thread.ofPlatform()
                .name("manager-agent-acp-stdio")
                .start(() -> runProtocol(protocolServer));
        log.info("[MANAGER-AGENT] ACP stdio protocol loop started");
    }

    @Override
    public void stop() {
        if (protocolThread != null) {
            protocolThread.interrupt();
        }
    }

    private void runProtocol(ManagerAgentProtocolServer protocolServer) {
        try {
            protocolServer.run();
            log.info("[MANAGER-AGENT] ACP stdio input closed, shutting down");
            vertx.close();
        } catch (Exception exception) {
            log.error("[MANAGER-AGENT] ACP stdio protocol loop failed", exception);
            System.exit(1);
        }
    }
}
