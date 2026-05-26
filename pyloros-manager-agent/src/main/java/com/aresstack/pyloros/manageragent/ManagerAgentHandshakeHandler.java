package com.aresstack.pyloros.manageragent;

import com.agentclientprotocol.sdk.error.AcpErrorCodes;
import com.agentclientprotocol.sdk.spec.AcpSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

final class ManagerAgentHandshakeHandler {

    private static final int UNKNOWN_SESSION_ID_ERROR_CODE = -32001;
    private static final int MCP_SERVER_CONFIGURATION_ERROR_CODE = -32002;
    private static final int MCP_SERVER_INVOCATION_ERROR_CODE = -32003;
    private static final List<String> SAFE_TOOL_HINTS = List.of("ping", "status");

    private final ObjectMapper objectMapper;
    private final ManagerAgentSessionState sessionState;
    private final ManagerAgentMcpGateway mcpGateway;

    ManagerAgentHandshakeHandler(ObjectMapper objectMapper, ManagerAgentSessionState sessionState) {
        this(objectMapper, sessionState, new ManagerAgentMcpGateway.HttpManagerAgentMcpGateway(objectMapper));
    }

    ManagerAgentHandshakeHandler(ObjectMapper objectMapper, ManagerAgentSessionState sessionState, ManagerAgentMcpGateway mcpGateway) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.sessionState = Objects.requireNonNull(sessionState, "sessionState must not be null");
        this.mcpGateway = Objects.requireNonNull(mcpGateway, "mcpGateway must not be null");
    }

    void handleSessionNew(JsonNode id, ObjectNode params, ManagerAgentResponseEmitter emitter) throws IOException {
        String cwd = requiredText(params, "cwd");
        if (cwd == null) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: cwd is required");
            return;
        }

        ManagerAgentMcpGateway.McpServer mcpServer = parseInjectedMcpServer(params);
        if (mcpServer == null) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS,
                    "Invalid params: mcpServers.pyloros.url is required");
            return;
        }

        String sessionId = sessionState.createSessionId(new ManagerAgentSessionState.SessionContext(mcpServer));
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
        ManagerAgentSessionState.SessionContext sessionContext = sessionState.session(sessionId);
        if (sessionContext == null || sessionContext.server() == null) {
            emitter.sendError(id, MCP_SERVER_CONFIGURATION_ERROR_CODE,
                    "Missing injected MCP endpoint for sessionId: " + sessionId);
            return;
        }

        JsonNode promptNode = params.get("prompt");
        if (promptNode == null || promptNode.isNull() || !promptNode.isTextual()) {
            emitter.sendError(id, AcpErrorCodes.INVALID_PARAMS, "Invalid params: prompt must be a string");
            return;
        }
        String prompt = promptNode.asText();

        JsonNode listResponse;
        String safeToolName;
        JsonNode toolCallResponse;
        try {
            listResponse = mcpGateway.toolsList(sessionContext.server());
            safeToolName = chooseSafeToolName(listResponse.path("result").path("tools"));
            if (safeToolName == null) {
                emitter.sendError(id, MCP_SERVER_INVOCATION_ERROR_CODE,
                        "Injected MCP endpoint did not expose a safe tool candidate");
                return;
            }
            toolCallResponse = mcpGateway.toolsCall(sessionContext.server(), safeToolName, objectMapper.createObjectNode());
        } catch (Exception exception) {
            emitter.sendError(id, MCP_SERVER_INVOCATION_ERROR_CODE,
                    "Failed to use injected MCP endpoint: " + exception.getMessage());
            return;
        }

        emitter.sendResult(id, objectMapper.valueToTree(AcpSchema.PromptResponse.endTurn()));

        ObjectNode textUpdate = objectMapper.createObjectNode();
        textUpdate.put("type", "text");
        textUpdate.put("sessionId", sessionId);
        textUpdate.put("text", "processing: " + prompt + " | tools/list + tools/call succeeded via injected MCP endpoint");
        emitter.sendSessionUpdate(textUpdate);

        ObjectNode completionUpdate = objectMapper.createObjectNode();
        completionUpdate.put("type", "completion");
        completionUpdate.put("sessionId", sessionId);
        completionUpdate.put("result", "completed: " + prompt + " | called safe tool: " + safeToolName
                + " | toolResult: " + toolCallResponse.path("result"));
        emitter.sendSessionUpdate(completionUpdate);
    }

    private ManagerAgentMcpGateway.McpServer parseInjectedMcpServer(ObjectNode params) {
        JsonNode mcpServersNode = params.get("mcpServers");
        if (mcpServersNode == null || mcpServersNode.isNull() || !mcpServersNode.isObject()) {
            return null;
        }

        JsonNode pylorosNode = mcpServersNode.get("pyloros");
        if (pylorosNode == null || pylorosNode.isNull() || !pylorosNode.isObject()) {
            return null;
        }

        String url = requiredText((ObjectNode) pylorosNode, "url");
        if (url == null) {
            return null;
        }
        return new ManagerAgentMcpGateway.McpServer(url,
                ManagerAgentMcpGateway.readHeaders(pylorosNode.get("headers")));
    }

    private String chooseSafeToolName(JsonNode toolsNode) {
        if (!(toolsNode instanceof ArrayNode arrayNode)) {
            return null;
        }

        for (JsonNode toolNode : arrayNode) {
            String toolName = text(toolNode, "name");
            if (toolName != null && "pyloros__ping".equals(toolName)) {
                return toolName;
            }
        }

        for (JsonNode toolNode : arrayNode) {
            String toolName = text(toolNode, "name");
            if (toolName == null) {
                continue;
            }
            String normalized = toolName.toLowerCase();
            for (String hint : SAFE_TOOL_HINTS) {
                if (normalized.contains(hint)) {
                    return toolName;
                }
            }
        }
        return null;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String normalized = value.asText().trim();
        return normalized.isEmpty() ? null : normalized;
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
