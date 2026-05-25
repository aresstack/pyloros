package com.aresstack.pyloros.manageragent;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ManagerAgentBootstrapVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgentBootstrapVerticle.class);

    @Override
    public void start() {
        log.info("[MANAGER-AGENT] Java 21 ACP manager-agent module bootstrap is alive");
    }
}
