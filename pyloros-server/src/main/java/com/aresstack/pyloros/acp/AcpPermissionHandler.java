package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface AcpPermissionHandler {

    CompletionStage<Map<String, Object>> requestPermission(JsonNode params);

    static AcpPermissionHandler denyByDefault() {
        return params -> CompletableFuture.completedFuture(Map.of(
                "outcome", Map.of("outcome", "cancelled")));
    }
}
