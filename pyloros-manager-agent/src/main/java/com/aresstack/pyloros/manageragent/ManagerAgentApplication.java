package com.aresstack.pyloros.manageragent;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagerAgentApplication {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgentApplication.class);

    private ManagerAgentApplication() {
    }

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new ManagerAgentBootstrapVerticle())
                .onSuccess(id -> log.info("[MANAGER-AGENT] bootstrap started verticleId={}", id))
                .onFailure(error -> {
                    log.error("[MANAGER-AGENT] bootstrap failed", error);
                    System.exit(1);
                });
    }
}
