package com.aresstack.pyloros.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpJson {

    private static final Logger log = LoggerFactory.getLogger(HttpJson.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Fallback used whenever a {@link Throwable} carries no message. Without this, a null
     * message used to reach {@code Map.of(...)} and blow up with a {@link NullPointerException}
     * inside an asynchronous callback, where Vert.x Web can no longer turn it into a 500 — the
     * HTTP response was simply never ended and the client stalled until its proxy timed out.
     */
    static final String DEFAULT_ERROR_MESSAGE = "Internal error without message";

    private HttpJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static void send(RoutingContext context, int statusCode, Object body) {
        String payload;
        try {
            payload = MAPPER.writeValueAsString(body);
        } catch (Exception exception) {
            log.warn("Failed to serialize HTTP response body: {}", errorMessage(exception));
            payload = "{\"error\":\"Response serialization failed\"}";
            statusCode = 500;
        }
        write(context, statusCode, "application/json", payload);
    }

    /**
     * Ends the response with a status code and no body at all. Used for JSON-RPC notifications,
     * which MCP Streamable HTTP requires to be answered with a bodyless {@code 202 Accepted}.
     */
    public static void sendNoBody(RoutingContext context, int statusCode) {
        write(context, statusCode, null, null);
    }

    public static void rpcResult(RoutingContext context, JsonNode id, Object result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        envelope.put("id", convertId(id));
        envelope.put("result", result == null ? Map.of() : result);
        send(context, 200, envelope);
    }

    public static void rpcError(RoutingContext context, JsonNode id, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message == null || message.isBlank() ? DEFAULT_ERROR_MESSAGE : message);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        // A JSON-RPC error may legitimately carry a null id (e.g. the request could not be parsed
        // far enough to know it), so the envelope must tolerate null here.
        envelope.put("id", convertId(id));
        envelope.put("error", error);
        send(context, 200, envelope);
    }

    /**
     * Normalizes a throwable into a non-null, non-blank message suitable for a JSON-RPC error.
     */
    public static String errorMessage(Throwable error) {
        if (error == null) {
            return DEFAULT_ERROR_MESSAGE;
        }
        String message = error.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return error.getClass().getSimpleName();
    }

    private static Object convertId(JsonNode id) {
        if (id == null || id.isNull()) {
            return null;
        }
        try {
            return MAPPER.convertValue(id, Object.class);
        } catch (Exception exception) {
            log.debug("Failed to convert JSON-RPC id, falling back to text: {}", errorMessage(exception));
            return id.asText();
        }
    }

    /**
     * Writes the response defensively: the overriding goal is that every request is terminated
     * exactly once, even when something upstream already wrote to the response or the socket is
     * gone. A stalled, never-ended response is what surfaces as "Error in input stream" clientside.
     */
    private static void write(RoutingContext context, int statusCode, String contentType, String payload) {
        try {
            var response = context.response();
            if (response.ended()) {
                log.debug("Skipping write, response already ended (status={})", statusCode);
                return;
            }
            if (!response.headWritten()) {
                response.setStatusCode(statusCode)
                        .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                        .putHeader("Pragma", "no-cache");
                if (contentType != null) {
                    response.putHeader(HttpHeaders.CONTENT_TYPE, contentType);
                }
            }
            McpHttpCallTrace trace = McpHttpCallTrace.from(context);
            if (trace != null) {
                trace.responseEndSubmitted(statusCode, payload);
            }
            var endFuture = payload == null ? response.end() : response.end(payload);
            if (trace != null) {
                int finalStatus = statusCode;
                endFuture.onComplete(result -> trace.responseEndResult(finalStatus, result));
            }
        } catch (Exception exception) {
            log.warn("Failed to end HTTP response: {}", errorMessage(exception));
            McpHttpCallTrace trace = McpHttpCallTrace.from(context);
            if (trace != null) {
                trace.writeException(exception);
            }
        }
    }
}
