package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerAgentHandshakeHandlerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void sessionHandshakeLogicIsTestableWithoutPipeTransport() throws Exception {
        ManagerAgentHandshakeHandler handler = new ManagerAgentHandshakeHandler(OBJECT_MAPPER, new ManagerAgentSessionState());
        CapturingEmitter emitter = new CapturingEmitter(OBJECT_MAPPER);

        ObjectNode newSessionParams = OBJECT_MAPPER.createObjectNode();
        newSessionParams.put("cwd", "/workspace");
        handler.handleSessionNew(OBJECT_MAPPER.valueToTree("1"), newSessionParams, emitter);

        String sessionId = emitter.messages().get(0).path("result").path("sessionId").asText(null);
        assertNotNull(sessionId);

        ObjectNode promptParams = OBJECT_MAPPER.createObjectNode();
        promptParams.put("sessionId", sessionId);
        promptParams.put("prompt", "hello");
        handler.handleSessionPrompt(OBJECT_MAPPER.valueToTree("2"), promptParams, emitter);

        assertEquals(4, emitter.messages().size());
        JsonNode promptResult = emitter.messages().get(1);
        assertEquals("2", promptResult.path("id").asText());
        assertTrue(promptResult.path("result").isObject());

        JsonNode textUpdate = emitter.messages().get(2);
        assertEquals("session/update", textUpdate.path("method").asText());
        assertEquals("text", textUpdate.path("params").path("type").asText());
        assertTrue(textUpdate.path("params").path("text").asText().contains("hello"));

        JsonNode completionUpdate = emitter.messages().get(3);
        assertEquals("session/update", completionUpdate.path("method").asText());
        assertEquals("completion", completionUpdate.path("params").path("type").asText());
        assertTrue(completionUpdate.path("params").path("result").asText().contains("hello"));
    }

    @Test
    void unknownSessionProducesStructuredErrorFromHandler() throws Exception {
        ManagerAgentHandshakeHandler handler = new ManagerAgentHandshakeHandler(OBJECT_MAPPER, new ManagerAgentSessionState());
        CapturingEmitter emitter = new CapturingEmitter(OBJECT_MAPPER);

        ObjectNode promptParams = OBJECT_MAPPER.createObjectNode();
        promptParams.put("sessionId", "missing");
        promptParams.put("prompt", "hello");

        handler.handleSessionPrompt(OBJECT_MAPPER.valueToTree("7"), promptParams, emitter);

        JsonNode error = emitter.messages().get(0).path("error");
        assertEquals(-32001, error.path("code").asInt());
        assertTrue(error.path("message").asText().contains("Unknown sessionId"));
    }

    private static final class CapturingEmitter implements ManagerAgentResponseEmitter {

        private final ObjectMapper objectMapper;
        private final List<ObjectNode> messages = new ArrayList<>();

        private CapturingEmitter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        @Override
        public void sendResult(JsonNode id, JsonNode result) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.set("id", id);
            payload.set("result", result);
            messages.add(payload);
        }

        @Override
        public void sendError(JsonNode id, int code, String message) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("code", code);
            error.put("message", message);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            if (id == null || id.isNull()) {
                payload.putNull("id");
            } else {
                payload.set("id", id);
            }
            payload.set("error", error);
            messages.add(payload);
        }

        @Override
        public void sendSessionUpdate(ObjectNode params) {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("method", "session/update");
            payload.set("params", params);
            messages.add(payload);
        }

        List<ObjectNode> messages() {
            return messages;
        }
    }
}
