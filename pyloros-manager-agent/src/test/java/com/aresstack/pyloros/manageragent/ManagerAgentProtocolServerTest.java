package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerAgentProtocolServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private ExecutorService executor;
    private java.io.PipedOutputStream clientWriterPipe;
    private java.io.PipedInputStream clientReaderPipe;

    @AfterEach
    void tearDown() throws IOException {
        if (clientWriterPipe != null) {
            clientWriterPipe.close();
        }
        if (clientReaderPipe != null) {
            clientReaderPipe.close();
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void handlesSessionNewAndPromptWithCompletionUpdate() throws Exception {
        Session session = startServer();

        send(session.writer(), """
                {"jsonrpc":"2.0","id":"1","method":"session/new","params":{"cwd":"/workspace","mcpServers":{"pyloros":{"url":"http://127.0.0.1:8081/pyloros?view=agent"}}}}
                """);
        JsonNode newSessionResponse = receive(session.reader());
        String sessionId = newSessionResponse.path("result").path("sessionId").asText(null);
        assertNotNull(sessionId);

        send(session.writer(), """
                {"jsonrpc":"2.0","id":"2","method":"session/prompt","params":{"sessionId":"%s","prompt":"hello"}}
                """.formatted(sessionId));
        JsonNode promptResponse = receive(session.reader());
        assertEquals("2", promptResponse.path("id").asText());
        assertTrue(promptResponse.path("result").isObject());
        assertTrue(promptResponse.path("error").isMissingNode());

        JsonNode textUpdate = receive(session.reader());
        assertEquals("session/update", textUpdate.path("method").asText());
        assertEquals("text", textUpdate.path("params").path("type").asText());
        assertTrue(textUpdate.path("params").path("text").asText().contains("hello"));

        JsonNode completionUpdate = receive(session.reader());
        assertEquals("session/update", completionUpdate.path("method").asText());
        assertEquals("completion", completionUpdate.path("params").path("type").asText());
        assertTrue(completionUpdate.path("params").path("result").asText().contains("hello"));
    }

    @Test
    void returnsStructuredErrorsForInvalidRequests() throws Exception {
        Session session = startServer();

        send(session.writer(), "not-json");
        JsonNode parseError = receive(session.reader());
        assertEquals(-32700, parseError.path("error").path("code").asInt());
        assertTrue(parseError.path("id").isNull());

        send(session.writer(), """
                {"jsonrpc":"2.0","id":"3","method":"session/new","params":{}}
                """);
        JsonNode invalidParams = receive(session.reader());
        assertEquals(-32602, invalidParams.path("error").path("code").asInt());
        assertEquals("3", invalidParams.path("id").asText());

        send(session.writer(), """
                {"jsonrpc":"2.0","id":"4","method":"unknown/method","params":{}}
                """);
        JsonNode methodNotFound = receive(session.reader());
        assertEquals(-32601, methodNotFound.path("error").path("code").asInt());
        assertEquals("4", methodNotFound.path("id").asText());
    }

    private Session startServer() throws IOException {
        java.io.PipedInputStream serverIn = new java.io.PipedInputStream(64 * 1024);
        clientWriterPipe = new java.io.PipedOutputStream(serverIn);

        clientReaderPipe = new java.io.PipedInputStream(64 * 1024);
        java.io.PipedOutputStream serverOut = new java.io.PipedOutputStream(clientReaderPipe);

        LineDelimitedJsonRpcTransport transport = new LineDelimitedJsonRpcTransport(OBJECT_MAPPER, serverIn, serverOut);
        ManagerAgentHandshakeHandler handshakeHandler = new ManagerAgentHandshakeHandler(
                OBJECT_MAPPER, new ManagerAgentSessionState(), new StubMcpGateway());
        ManagerAgentJsonRpcDispatcher dispatcher = new ManagerAgentJsonRpcDispatcher(handshakeHandler);
        ManagerAgentProtocolServer server = new ManagerAgentProtocolServer(transport, dispatcher);
        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            try {
                server.run();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientWriterPipe, StandardCharsets.UTF_8));
        BufferedReader reader = new BufferedReader(new InputStreamReader(clientReaderPipe, StandardCharsets.UTF_8));
        return new Session(writer, reader);
    }

    private static void send(BufferedWriter writer, String requestLine) throws IOException {
        writer.write(requestLine.trim());
        writer.newLine();
        writer.flush();
    }

    private static JsonNode receive(BufferedReader reader) throws IOException {
        return OBJECT_MAPPER.readTree(reader.readLine());
    }

    private record Session(BufferedWriter writer, BufferedReader reader) {
    }

    private static final class StubMcpGateway implements ManagerAgentMcpGateway {
        @Override
        public JsonNode toolsList(McpServer server) {
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", "list");
            response.set("result", OBJECT_MAPPER.valueToTree(Map.of("tools", List.of(Map.of("name", "safe__status")))));
            return response;
        }

        @Override
        public JsonNode toolsCall(McpServer server, String toolName, JsonNode arguments) {
            ObjectNode response = OBJECT_MAPPER.createObjectNode();
            response.put("jsonrpc", "2.0");
            response.put("id", "call");
            response.set("result", OBJECT_MAPPER.valueToTree(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "ok")),
                    "isError", false
            )));
            return response;
        }
    }
}
