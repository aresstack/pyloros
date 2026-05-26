package com.aresstack.pyloros.manageragent;

import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Objects;

final class ManagerAgentJsonRpcDispatcher {

    private final ManagerAgentHandshakeHandler handshakeHandler;

    ManagerAgentJsonRpcDispatcher(ManagerAgentHandshakeHandler handshakeHandler) {
        this.handshakeHandler = Objects.requireNonNull(handshakeHandler, "handshakeHandler must not be null");
    }

    void dispatch(JsonNode request, ManagerAgentResponseEmitter emitter) throws IOException {
        JsonNode id = request.get("id");
        if (!"2.0".equals(text(request, "jsonrpc"))) {
            emitter.sendError(id, AcpErrorCodes.INVALID_REQUEST, "Invalid Request: jsonrpc must be '2.0'");
            return;
        }
        if (id == null || id.isNull()) {
            emitter.sendError(null, AcpErrorCodes.INVALID_REQUEST, "Invalid Request: id is required");
            return;
        }

        String method = text(request, "method");
        if (method == null) {
            emitter.sendError(id, AcpErrorCodes.INVALID_REQUEST, "Invalid Request: method is required");
            return;
        }

        JsonNode paramsNode = request.get("params");
        if (paramsNode == null || paramsNode.isNull()) {
            paramsNode = JsonNodeFactory.instance.objectNode();
        }
        if (!paramsNode.isObject()) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: expected object");
            return;
        }
        ObjectNode params = (ObjectNode) paramsNode;

        switch (method) {
            case "session/new" -> handshakeHandler.handleSessionNew(id, params, emitter);
            case "session/prompt" -> handshakeHandler.handleSessionPrompt(id, params, emitter);
            default -> emitter.sendError(id, AcpErrorCodes.METHOD_NOT_FOUND, "Method not found: " + method);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String normalized = value.asText().trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
