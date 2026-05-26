package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagerAgentInjectedMcpSmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void promptFlowUsesInjectedMcpServerForToolsListAndSafeToolsCall() throws Exception {
        try (FakeMcpEndpoint mcpEndpoint = new FakeMcpEndpoint();
             SessionHarness harness = SessionHarness.start(new ManagerAgentMcpGateway.HttpManagerAgentMcpGateway(
                     OBJECT_MAPPER, HttpClient.newHttpClient()))) {
            mcpEndpoint.start();

            send(harness.writer(), """
                    {"jsonrpc":"2.0","id":"1","method":"session/new","params":{"cwd":"/workspace","mcpServers":{"pyloros":{"url":"%s","headers":{"Authorization":"Bearer token"}}}}}
                    """.formatted(mcpEndpoint.url()));
            JsonNode newSessionResponse = receive(harness.reader());
            String sessionId = newSessionResponse.path("result").path("sessionId").asText(null);
            assertNotNull(sessionId);

            send(harness.writer(), """
                    {"jsonrpc":"2.0","id":"2","method":"session/prompt","params":{"sessionId":"%s","prompt":"hello"}}
                    """.formatted(sessionId));
            JsonNode promptResponse = receive(harness.reader());
            assertEquals("2", promptResponse.path("id").asText());
            assertTrue(promptResponse.path("result").isObject());

            JsonNode textUpdate = receive(harness.reader());
            assertEquals("session/update", textUpdate.path("method").asText());
            assertEquals("text", textUpdate.path("params").path("type").asText());
            assertTrue(textUpdate.path("params").path("text").asText().contains("tools/list + tools/call succeeded"));

            JsonNode completionUpdate = receive(harness.reader());
            assertEquals("session/update", completionUpdate.path("method").asText());
            assertEquals("completion", completionUpdate.path("params").path("type").asText());
            assertTrue(completionUpdate.path("params").path("result").asText().contains("called safe tool: pyloros__ping"));
            assertTrue(completionUpdate.path("params").path("result").asText().contains("pong"));

            assertEquals(List.of("tools/list", "tools/call"), mcpEndpoint.requestMethods());
            assertEquals("Bearer token", mcpEndpoint.authorizationHeaders().getFirst());
            JsonNode callRequest = mcpEndpoint.requests().get(1);
            assertEquals("pyloros__ping", callRequest.path("params").path("name").asText());
            assertTrue(callRequest.path("params").path("arguments").isObject());
        }
    }

    private static void send(BufferedWriter writer, String requestLine) throws IOException {
        writer.write(requestLine.trim());
        writer.newLine();
        writer.flush();
    }

    private static JsonNode receive(BufferedReader reader) throws IOException {
        return OBJECT_MAPPER.readTree(reader.readLine());
    }

    private record SessionHarness(BufferedWriter writer, BufferedReader reader, ExecutorService executor,
                                  java.io.PipedOutputStream clientWriterPipe, java.io.PipedInputStream clientReaderPipe)
            implements AutoCloseable {

        private static SessionHarness start(ManagerAgentMcpGateway gateway) throws IOException {
            java.io.PipedInputStream serverIn = new java.io.PipedInputStream(64 * 1024);
            java.io.PipedOutputStream clientWriterPipe = new java.io.PipedOutputStream(serverIn);

            java.io.PipedInputStream clientReaderPipe = new java.io.PipedInputStream(64 * 1024);
            java.io.PipedOutputStream serverOut = new java.io.PipedOutputStream(clientReaderPipe);

            LineDelimitedJsonRpcTransport transport = new LineDelimitedJsonRpcTransport(OBJECT_MAPPER, serverIn, serverOut);
            ManagerAgentHandshakeHandler handshakeHandler = new ManagerAgentHandshakeHandler(
                    OBJECT_MAPPER, new ManagerAgentSessionState(), gateway);
            ManagerAgentJsonRpcDispatcher dispatcher = new ManagerAgentJsonRpcDispatcher(handshakeHandler);
            ManagerAgentProtocolServer server = new ManagerAgentProtocolServer(transport, dispatcher);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(() -> {
                try {
                    server.run();
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientWriterPipe, StandardCharsets.UTF_8));
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientReaderPipe, StandardCharsets.UTF_8));
            return new SessionHarness(writer, reader, executor, clientWriterPipe, clientReaderPipe);
        }

        @Override
        public void close() throws IOException {
            clientWriterPipe.close();
            clientReaderPipe.close();
            executor.shutdownNow();
        }
    }

    private static final class FakeMcpEndpoint implements AutoCloseable {

        private final HttpServer server;
        private final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<>());
        private final List<String> methods = Collections.synchronizedList(new ArrayList<>());
        private final List<String> authorizationHeaders = Collections.synchronizedList(new ArrayList<>());

        private FakeMcpEndpoint() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.server.createContext("/pyloros", this::handle);
        }

        private void start() {
            server.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/pyloros?view=agent";
        }

        private List<JsonNode> requests() {
            return requests;
        }

        private List<String> requestMethods() {
            return methods;
        }

        private List<String> authorizationHeaders() {
            return authorizationHeaders;
        }

        private void handle(HttpExchange exchange) throws IOException {
            JsonNode request = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            requests.add(request);
            methods.add(request.path("method").asText());
            authorizationHeaders.add(Objects.toString(exchange.getRequestHeaders().getFirst("Authorization"), ""));

            JsonNode response = switch (request.path("method").asText()) {
                case "tools/list" -> OBJECT_MAPPER.valueToTree(Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asText(),
                        "result", Map.of(
                                "tools", List.of(
                                        Map.of("name", "dangerous__delete_everything"),
                                        Map.of("name", "pyloros__ping")
                                )
                        )
                ));
                case "tools/call" -> OBJECT_MAPPER.valueToTree(Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asText(),
                        "result", Map.of(
                                "content", List.of(Map.of("type", "text", "text", "pong")),
                                "isError", false
                        )
                ));
                default -> OBJECT_MAPPER.valueToTree(Map.of(
                        "jsonrpc", "2.0",
                        "id", request.path("id").asText(),
                        "error", Map.of("code", -32601, "message", "Method not found")
                ));
            };

            byte[] body = OBJECT_MAPPER.writeValueAsBytes(response);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
