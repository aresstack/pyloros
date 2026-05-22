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
    private final java.util.concurrent.atomic.AtomicBoolean pending = new java.util.concurrent.atomic.AtomicBoolean(false);

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

        // Ensure only one pending call at a time (001-C requirement)
        if (!pending.compareAndSet(false, true)) {
            return Future.failedFuture(new IllegalStateException("Another JSON-RPC call is pending"));
        }

        try {
            String body = new JsonObject()
                    .put("jsonrpc", "2.0")
                    .put("id", System.currentTimeMillis())
                    .put("method", method)
                    .put("params", params == null ? new JsonObject() : params)
                    .encode();

            Promise<JsonObject> promise = Promise.promise();

            client.request(HttpMethod.POST, config.port(), config.host(), endpoint)
                    .onSuccess(req -> {
                        req.putHeader("Content-Type", "application/json");
                        req.putHeader("Accept", "application/json");
                        req.send(Buffer.buffer(body))
                                .onSuccess(response -> {
                                    pending.set(false);
                                    try {
                                        String resp = response.body() == null ? "" : response.body().toString();
                                        JsonObject json = new JsonObject(resp);
                                        if (json.containsKey("result")) {
                                            promise.complete(json.getJsonObject("result"));
                                        } else {
                                            promise.complete(json);
                                        }
                                    } catch (Exception ex) {
                                        promise.fail(ex);
                                    }
                                })
                                .onFailure(err -> {
                                    pending.set(false);
                                    promise.fail(err);
                                });
                    })
                    .onFailure(err -> {
                        pending.set(false);
                        promise.fail(err);
                    });

            return promise.future();
        } catch (Exception ex) {
            pending.set(false);
            return Future.failedFuture(ex);
        }
    }
}

