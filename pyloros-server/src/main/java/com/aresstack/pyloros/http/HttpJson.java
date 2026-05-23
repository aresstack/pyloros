package com.aresstack.pyloros.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

import java.util.Map;

public final class HttpJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static void send(RoutingContext context, int statusCode, Object body) {
        try {
            context.response()
                    .setStatusCode(statusCode)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                    .putHeader("Pragma", "no-cache")
                    .end(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException exception) {
            context.fail(exception);
        }
    }

    public static void rpcResult(RoutingContext context, JsonNode id, Object result) {
        send(context, 200, Map.of(
                "jsonrpc", "2.0",
                "id", MAPPER.convertValue(id, Object.class),
                "result", result
        ));
    }

    public static void rpcError(RoutingContext context, JsonNode id, int code, String message) {
        send(context, 200, Map.of(
                "jsonrpc", "2.0",
                "id", MAPPER.convertValue(id, Object.class),
                "error", Map.of("code", code, "message", message)
        ));
    }
}
