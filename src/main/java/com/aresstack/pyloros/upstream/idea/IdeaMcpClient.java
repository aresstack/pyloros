package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level client that coordinates SSE session and JSON-RPC client. 001-B provides skeleton functionality.
 */
public final class IdeaMcpClient {

    private static final Logger log = LoggerFactory.getLogger(IdeaMcpClient.class);

    private final Vertx vertx;
    private final IdeaMcpConfig config;

    private final IdeaSseSession sseSession;
    private final IdeaJsonRpcClient jsonRpcClient;

    public IdeaMcpClient(Vertx vertx, IdeaMcpConfig config) {
        this.vertx = vertx;
        this.config = config;
        this.sseSession = new IdeaSseSession(vertx, config);
        this.jsonRpcClient = new IdeaJsonRpcClient(vertx, config, sseSession);
    }

    public void start() {
        try {
            sseSession.start();
            log.info("IdeaMcpClient started (SSE session requested)");
        } catch (Exception ex) {
            log.debug("Failed to start IdeaMcpClient: {}", ex.getMessage());
        }
    }

    public boolean isReady() {
        return sseSession.isReady();
    }

    public String getEndpoint() {
        return sseSession.getEndpoint();
    }

    public Future<JsonObject> call(String method, JsonObject params) {
        return jsonRpcClient.postJsonRpc(method, params);
    }

    public Future<Void> stop() {
        return sseSession.stop();
    }
}

