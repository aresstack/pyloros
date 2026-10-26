package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
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
        return postJsonRpc(method, params, config.responseTimeoutMillis());
    }

    /**
     * @param responseTimeoutMillis how long to wait for the answer, overriding the configured
     *                              default so a call that was asked to run long is not cut short
     */
    public Future<JsonObject> postJsonRpc(String method, JsonObject params, long responseTimeoutMillis) {
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
            sseSession.registerPendingResponse(id, promise, responseTimeoutMillis);

            // Every terminal outcome retires the registration, not only the SSE one. A synchronous
            // body, an HTTP error status and a failed POST all settle the promise here, and an entry
            // left behind keeps its timeout timer armed for the whole window — up to an hour now
            // that a tool's own deadline can extend it. Hanging this on the future instead of the
            // individual call sites makes that hold for outcomes added later, too.
            promise.future().onComplete(ignored -> sseSession.releasePendingResponse(id));

            log.info("IdeaJsonRpcClient POST {} -> {}:{}{} (id={}, timeout={} ms)",
                    method, config.host(), config.port(), endpoint, id, responseTimeoutMillis);
            client.request(HttpMethod.POST, config.port(), config.host(), endpoint)
                    .onSuccess(req -> {
                        req.putHeader("Content-Type", "application/json");
                        req.putHeader("Accept", "application/json");
                        config.headers().forEach(req::putHeader);
                        req.send(Buffer.buffer(body))
                                .onSuccess(response -> handleHttpResponse(method, id, response, promise))
                                .onFailure(promise::tryFail);
                    })
                    .onFailure(promise::tryFail);

            return promise.future();
        } catch (Exception ex) {
            return Future.failedFuture(ex);
        }
    }

    /**
     * Evaluates the POST response.
     *
     * <p>IDEA MCP normally acknowledges the POST with 202 and an empty body, then delivers the real
     * JSON-RPC response over the SSE channel. Two cases still have to be acted on here:
     * a non-2xx status means the request never reached the tool, and a body that already carries a
     * JSON-RPC envelope is a valid synchronous answer. Neither must be left to the SSE timeout.
     *
     * <p>{@code response.body()} is a {@code Future<Buffer>} — it has to be composed, never
     * stringified. Doing the latter yielded the literal {@code Future{unresolved}} for every call.
     */
    private void handleHttpResponse(String method, String id, HttpClientResponse response, Promise<JsonObject> promise) {
        int status = response.statusCode();
        response.body()
                .onFailure(err -> {
                    log.warn("IdeaJsonRpcClient could not read {} response body (id={}, status={}): {}",
                            method, id, status, err.getMessage());
                    if (status < 200 || status >= 300) {
                        promise.tryFail(new IllegalStateException(
                                "IDEA MCP returned HTTP " + status + " for " + method));
                    }
                })
                .onSuccess(buffer -> {
                    String resp = buffer == null ? "" : buffer.toString();
                    if (status < 200 || status >= 300) {
                        log.warn("IdeaJsonRpcClient {} rejected with HTTP {} (id={}): {}",
                                method, status, id, excerpt(resp));
                        promise.tryFail(new IllegalStateException(
                                "IDEA MCP returned HTTP " + status + " for " + method + ": " + excerpt(resp)));
                        return;
                    }
                    if (resp.isBlank()) {
                        log.debug("IdeaJsonRpcClient {} acknowledged with HTTP {} and empty body (id={}), awaiting SSE response",
                                method, status, id);
                        return;
                    }

                    JsonObject json;
                    try {
                        json = new JsonObject(resp);
                    } catch (Exception ex) {
                        // Not a JSON-RPC envelope, so the real answer still has to arrive over SSE.
                        log.debug("IdeaJsonRpcClient {} returned a non-JSON body (id={}), awaiting SSE response: {}",
                                method, id, excerpt(resp));
                        return;
                    }

                    if (!isResponseEnvelopeFor(id, json)) {
                        // Valid JSON, but not an answer to this request. Completing the call with it
                        // would discard the real answer that is still on its way over SSE.
                        log.debug("IdeaJsonRpcClient {} returned a body that is not a JSON-RPC response for id {}, "
                                + "awaiting SSE response: {}", method, id, excerpt(resp));
                        return;
                    }

                    log.debug("IdeaJsonRpcClient synchronous response for {} (id={}, {} chars)",
                            method, id, resp.length());
                    if (json.containsKey("error")) {
                        promise.tryFail(new IllegalStateException("IDEA MCP error for " + method + ": "
                                + UpstreamText.describeError(json.getValue("error"))));
                    } else if (json.getValue("result") instanceof JsonObject result) {
                        promise.tryComplete(result);
                    } else {
                        promise.tryComplete(json);
                    }
                });
    }

    /**
     * @return whether {@code json} is a JSON-RPC response to request {@code id}
     *
     * <p>Correlation was missing here: any JSON object was accepted as the result, so a proxy's
     * {@code {"status":"ok"}} or an envelope belonging to another request could settle the call.
     */
    private static boolean isResponseEnvelopeFor(String id, JsonObject json) {
        if (!json.containsKey("result") && !json.containsKey("error")) {
            return false;
        }
        Object responseId = json.getValue("id");
        return responseId != null && id.equals(String.valueOf(responseId));
    }

    private static String excerpt(String body) {
        return UpstreamText.excerpt(body);
    }
}

