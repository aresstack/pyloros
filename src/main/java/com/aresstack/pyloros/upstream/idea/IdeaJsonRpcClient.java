package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Minimal JSON-RPC client skeleton for IDEA MCP. For 001-B this is only a lightweight helper that
 * knows where to post requests when an endpoint is available. It does not implement full JSON-RPC framing here.
 */
public final class IdeaJsonRpcClient {

    private static final Logger log = LoggerFactory.getLogger(IdeaJsonRpcClient.class);

    private final Vertx vertx;
    private final IdeaMcpConfig config;
    private final IdeaSseSession sseSession;

    private HttpClient client;

    public IdeaJsonRpcClient(Vertx vertx, IdeaMcpConfig config, IdeaSseSession sseSession) {
        this.vertx = vertx;
        this.config = config;
        this.sseSession = sseSession;
        this.client = vertx.createHttpClient();
    }

    public boolean hasEndpoint() {
        return sseSession != null && sseSession.isReady();
    }

    public Future<JsonObject> postJsonRpc(String method, JsonObject params) {
        if (!hasEndpoint()) {
            return Future.failedFuture(new IllegalStateException("No IDEA endpoint available"));
        }

        String endpoint = sseSession.getEndpoint();
        if (endpoint == null) {
            return Future.failedFuture(new IllegalStateException("No IDEA endpoint available"));
        }

        // For 001-B we will not perform a real JSON-RPC call; return a successful placeholder future.
        log.debug("Would POST JSON-RPC to {} with method {}", endpoint, method);
        JsonObject placeholder = new JsonObject().put("status", "ok").put("method", method);
        return Future.succeededFuture(placeholder);
    }
}

