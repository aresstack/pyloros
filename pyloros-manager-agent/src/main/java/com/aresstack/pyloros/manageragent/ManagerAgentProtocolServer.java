package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManagerAgentProtocolServer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();
    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    ManagerAgentProtocolServer(InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    void run() throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            handleLine(line);
        }
    }

    private void handleLine(String line) throws IOException {
        JsonNode request;
        try {
            request = OBJECT_MAPPER.readTree(line);
        } catch (JsonProcessingException exception) {
            sendError(null, -32700, "Parse error");
            return;
        }

        JsonNode id = request.get("id");
        if (!"2.0".equals(text(request, "jsonrpc"))) {
            sendError(id, -32600, "Invalid Request: jsonrpc must be '2.0'");
            return;
        }
        if (id == null || id.isNull()) {
            sendError(null, -32600, "Invalid Request: id is required");
            return;
        }
        String method = text(request, "method");
        if (method == null) {
            sendError(id, -32600, "Invalid Request: method is required");
            return;
        }

        JsonNode params = request.get("params");
        if (params == null || params.isNull()) {
            params = OBJECT_MAPPER.createObjectNode();
        }
        if (!params.isObject()) {
            sendError(id, -32602, "Invalid params: expected object");
            return;
        }

        switch (method) {
            case "session/new" -> handleSessionNew(id, params);
            case "session/prompt" -> handleSessionPrompt(id, params);
            default -> sendError(id, -32601, "Method not found: " + method);
        }
    }

    private void handleSessionNew(JsonNode id, JsonNode params) throws IOException {
        String cwd = text(params, "cwd");
        if (cwd == null) {
            sendError(id, -32602, "Invalid params: cwd is required");
            return;
        }
        String sessionId = UUID.randomUUID().toString();
        sessionIds.add(sessionId);

        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("sessionId", sessionId);
        sendResult(id, result);
    }

    private void handleSessionPrompt(JsonNode id, JsonNode params) throws IOException {
        String sessionId = text(params, "sessionId");
        if (sessionId == null) {
            sendError(id, -32602, "Invalid params: sessionId is required");
            return;
        }
        if (!sessionIds.contains(sessionId)) {
            sendError(id, -32001, "Unknown sessionId: " + sessionId);
            return;
        }

        JsonNode promptNode = params.get("prompt");
        if (promptNode == null || promptNode.isNull() || !promptNode.isTextual()) {
            sendError(id, -32602, "Invalid params: prompt must be a string");
            return;
        }
        String prompt = promptNode.asText();

        ObjectNode result = OBJECT_MAPPER.createObjectNode();
        result.put("accepted", true);
        sendResult(id, result);

        ObjectNode textUpdate = OBJECT_MAPPER.createObjectNode();
        textUpdate.put("type", "text");
        textUpdate.put("sessionId", sessionId);
        textUpdate.put("text", "processing: " + prompt);
        sendSessionUpdate(textUpdate);

        ObjectNode completionUpdate = OBJECT_MAPPER.createObjectNode();
        completionUpdate.put("type", "completion");
        completionUpdate.put("sessionId", sessionId);
        completionUpdate.put("result", "completed: " + prompt);
        sendSessionUpdate(completionUpdate);
    }

    private void sendResult(JsonNode id, JsonNode result) throws IOException {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        write(response);
    }

    private void sendError(JsonNode id, int code, String message) throws IOException {
        ObjectNode error = OBJECT_MAPPER.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null || id.isNull()) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        response.set("error", error);
        write(response);
    }

    private void sendSessionUpdate(ObjectNode params) throws IOException {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("method", "session/update");
        response.set("params", params);
        write(response);
    }

    private void write(JsonNode payload) throws IOException {
        synchronized (writeLock) {
            writer.write(OBJECT_MAPPER.writeValueAsString(payload));
            writer.newLine();
            writer.flush();
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
