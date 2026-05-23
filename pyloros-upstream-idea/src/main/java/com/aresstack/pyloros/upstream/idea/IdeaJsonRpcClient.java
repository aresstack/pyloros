package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        // For IDEA MCP the HTTP response may be asynchronous (202 Accepted) and the actual JSON-RPC
        // response comes back via the SSE 'message' events. We therefore register a pending promise
        // with the SSE session and wait for the matching SSE message with the same id.
        String id = String.valueOf(System.currentTimeMillis()) + "-" + java.util.UUID.randomUUID();
        String body = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", method)
                .put("params", params == null ? new JsonObject() : params)
                .encode();

        Promise<JsonObject> promise = Promise.promise();

        try {
            // register pending response via SSE session
            sseSession.registerPendingResponse(id, promise, config.responseTimeoutMillis());

            log.info("IdeaJsonRpcClient POST {} -> {}:{}{} (id={})", method, config.host(), config.port(), endpoint, id);
            client.request(HttpMethod.POST, config.port(), config.host(), endpoint)
                    .onSuccess(req -> {
                        req.putHeader("Content-Type", "application/json");
                        req.putHeader("Accept", "application/json");
                        config.headers().forEach(req::putHeader);
                        req.send(Buffer.buffer(body))
                                .onSuccess(response -> {
                                    // If server returns a sync result in body (rare), complete promise.
                                    try {
                                        String resp = response.body() == null ? "" : response.body().toString();
                                        if (resp != null && !resp.isBlank()) {
                                            log.info("IdeaJsonRpcClient synchronous response for {}: {}", method, resp);
                                            JsonObject json = new JsonObject(resp);
                                            if (json.containsKey("result")) {
                                                promise.tryComplete(json.getJsonObject("result"));
                                            } else {
                                                promise.tryComplete(json);
                                            }
                                        }
                                    } catch (Exception ex) {
                                        // ignore here, waiting for SSE
                                    }
                                })
                                .onFailure(promise::tryFail);
                    })
                    .onFailure(promise::tryFail);

            return promise.future();
        } catch (Exception ex) {
            return Future.failedFuture(ex);
        }
    }
}

