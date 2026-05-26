package com.aresstack.pyloros.manageragent;

import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Objects;

final class ManagerAgentHandshakeHandler {

    private static final int UNKNOWN_SESSION_ID_ERROR_CODE = -32001;

    private final ObjectMapper objectMapper;
    private final ManagerAgentSessionState sessionState;

    ManagerAgentHandshakeHandler(ObjectMapper objectMapper, ManagerAgentSessionState sessionState) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState must not be null");
    }

    void handleSessionNew(JsonNode id, ObjectNode params, ManagerAgentResponseEmitter emitter) throws IOException {
        String cwd = requiredText(params, "cwd");
        if (cwd == null) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: cwd is required");
            return;
        }

        String sessionId = sessionState.createSessionId();
        AcpSchema.NewSessionResponse response = new AcpSchema.NewSessionResponse(sessionId, null, null);
        emitter.sendResult(id, objectMapper.valueToTree(response));
    }

    void handleSessionPrompt(JsonNode id, ObjectNode params, ManagerAgentResponseEmitter emitter) throws IOException {
        String sessionId = requiredText(params, "sessionId");
        if (sessionId == null) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: sessionId is required");
            return;
        }
        if (!sessionState.containsSessionId(sessionId)) {
            emitter.sendError(id, UNKNOWN_SESSION_ID_ERROR_CODE, "Unknown sessionId: " + sessionId);
            return;
        }

        JsonNode promptNode = params.get("prompt");
        if (promptNode == null || promptNode.isNull() || !promptNode.isTextual()) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: prompt must be a string");
            return;
        }
        String prompt = promptNode.asText();

        emitter.sendResult(id, objectMapper.valueToTree(AcpSchema.PromptResponse.endTurn()));

        ObjectNode textUpdate = objectMapper.createObjectNode();
        textUpdate.put("type", "text");
        textUpdate.put("sessionId", sessionId);
        textUpdate.put("text", "processing: " + prompt);
        emitter.sendSessionUpdate(textUpdate);

        ObjectNode completionUpdate = objectMapper.createObjectNode();
        completionUpdate.put("type", "completion");
        completionUpdate.put("sessionId", sessionId);
        completionUpdate.put("result", "completed: " + prompt);
        emitter.sendSessionUpdate(completionUpdate);
    }

    private static String requiredText(ObjectNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String normalized = value.asText().trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
