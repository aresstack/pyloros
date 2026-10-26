package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.security.RequestAuthenticator;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the JSON-RPC error envelope.
 *
 * <p>Before the fix, {@code HttpJson} built its envelopes with {@code Map.of(...)}, which rejects
 * null values. A null JSON-RPC id, a null exception message or a null tool result therefore threw
 * a {@link NullPointerException}. When that happened inside an asynchronous {@code onFailure}
 * callback, Vert.x Web could no longer convert it into a 500: the response was never ended and the
 * client stalled until its proxy cut the connection — observed as "Error in input stream".
 *
 * <p>Every test here therefore asserts both the payload <em>and</em> — via a hard request timeout —
 * that the response is actually completed.
 */
class McpErrorResponseContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INJECTED_VIEW_TOKEN = "test-injected-view-token";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private Vertx vertx;
    private HttpServer server;
    private HttpClient httpClient;
    private String baseUrl;

    /** Overridden per test to choose how the single upstream provider behaves. */
    private Supplier<Future<List<Map<String, Object>>>> listToolsBehavior;
    private Supplier<Future<Map<String, Object>>> callToolBehavior;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        httpClient = HttpClient.newBuilder()
                // Pin HTTP/1.1: the JDK client otherwise attempts an h2c upgrade on plaintext
                // HTTP, which intermittently fails against the HTTP/1.1 server with a
                // ProtocolException under concurrent requests. Production traffic reaches
                // Pyloros over HTTP/1.1 behind the reverse proxy anyway.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        listToolsBehavior = () -> Future.succeededFuture(List.of(Map.of(
                "name", "echo",
                "description", "echo test tool",
                "inputSchema", Map.of("type", "object")
        )));
        callToolBehavior = () -> Future.succeededFuture(Map.of("content", List.of(), "isError", false));

        ToolProvider provider = new BehaviorToolProvider();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, ToolNameFormatter.defaultFormatter());
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        // Test-only probes for the HttpJson contract itself. The null-id error branch in McpRoutes
        // (-32602) is currently unreachable over HTTP because ToolView.named() accepts any
        // non-blank name, so the envelope contract is asserted directly here instead.
        router.post("/probe/null-id-and-message")
                .handler(context -> HttpJson.rpcError(context, null, -32700, null));
        new McpRoutes(new TestServerConfig(), alwaysAuthenticated(), toolCatalog, toolRouter).mount(router);

        server = await(vertx.createHttpServer().requestHandler(router).listen(0));
        baseUrl = "http://127.0.0.1:" + server.actualPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            await(server.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void asynchronousToolCallFailureWithoutMessageStillAnswers() throws Exception {
        // A messageless throwable is the exact trigger of the original NPE-in-callback hang.
        callToolBehavior = () -> Future.failedFuture(new RuntimeException());

        long startNanos = System.nanoTime();
        JsonNode response = postRpc("/", "tools/call", "{\"name\":\"test__echo\",\"arguments\":{}}");
        long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertEquals(-32000, response.path("error").path("code").asInt());
        String message = response.path("error").path("message").asText();
        assertFalse(message.isBlank(), "error message must never be blank");
        assertEquals("RuntimeException", message);

        // The point of the fix: the response is produced by the failure callback, not by the
        // client giving up. Timing it out would also satisfy the assertions above, so bound it.
        assertTrue(elapsedMillis < RESPONSE_TIMEOUT.toMillis() / 2,
                "the response must be ended by the handler, not left for the client to time out; took "
                        + elapsedMillis + "ms of a " + RESPONSE_TIMEOUT.toMillis() + "ms client timeout");
    }

    @Test
    void asynchronousListToolsFailureWithoutMessageStillAnswers() throws Exception {
        listToolsBehavior = () -> Future.failedFuture(new IllegalStateException());

        JsonNode response = postRpc("/", "tools/list", "{}");

        assertEquals(-32000, response.path("error").path("code").asInt());
        assertFalse(response.path("error").path("message").asText().isBlank());
    }

    @Test
    void nullToolResultIsSerializedAsEmptyResult() throws Exception {
        // ToolRouter passes a provider's null result straight through to the envelope.
        callToolBehavior = () -> Future.succeededFuture(null);

        JsonNode response = postRpc("/", "tools/call", "{\"name\":\"test__echo\",\"arguments\":{}}");

        assertTrue(response.path("result").isObject());
        assertEquals(0, response.path("result").size());
        assertFalse(response.has("error"));
    }

    @Test
    void nullRequestIdAndNullMessageAreStillSerialized() throws Exception {
        // Both null values used to throw inside Map.of() and leave the response unfinished.
        HttpResponse<String> raw = post("/probe/null-id-and-message", "{}", Map.of());

        assertEquals(200, raw.statusCode());
        JsonNode response = JSON.readTree(raw.body());
        assertEquals("2.0", response.path("jsonrpc").asText());
        assertTrue(response.has("id"), "id member must be present");
        assertTrue(response.path("id").isNull(), "id member must be JSON null");
        assertEquals(-32700, response.path("error").path("code").asInt());
        assertEquals(HttpJson.DEFAULT_ERROR_MESSAGE, response.path("error").path("message").asText());
    }

    @Test
    void unknownMethodStillProducesWellFormedError() throws Exception {
        JsonNode response = postRpc("/", "no/such/method", "{}");

        assertEquals(-32601, response.path("error").path("code").asInt());
        assertEquals(1, response.path("id").asInt());
    }

    @Test
    void normalizesMissingExceptionMessages() {
        assertEquals(HttpJson.DEFAULT_ERROR_MESSAGE, HttpJson.errorMessage(null));
        assertEquals("RuntimeException", HttpJson.errorMessage(new RuntimeException()));
        assertEquals("IllegalStateException", HttpJson.errorMessage(new IllegalStateException("   ")));
        assertEquals("boom", HttpJson.errorMessage(new RuntimeException("boom")));
    }

    @Test
    void tracedToolResponseRecordsLocalEndWithoutLeakingOutput() throws Exception {
        String sensitiveOutput = "DO_NOT_LOG_TERMINAL_OUTPUT_abc123";
        callToolBehavior = () -> Future.succeededFuture(Map.of(
                "content", List.of(Map.of("type", "text", "text", sensitiveOutput)),
                "isError", false
        ));

        Logger logger = (Logger) LoggerFactory.getLogger(McpHttpCallTrace.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            HttpResponse<String> first = post("/", toolCallJson(sensitiveOutput), Map.of());
            HttpResponse<String> second = post("/", toolCallJson("normal"), Map.of());

            String firstTrace = first.headers().firstValue(McpHttpCallTrace.RESPONSE_HEADER).orElseThrow();
            String secondTrace = second.headers().firstValue(McpHttpCallTrace.RESPONSE_HEADER).orElseThrow();
            UUID.fromString(firstTrace);
            UUID.fromString(secondTrace);
            assertFalse(firstTrace.equals(secondTrace), "each request needs its own correlation ID");
            assertEquals(200, first.statusCode());
            assertTrue(first.body().contains(sensitiveOutput), "the diagnostic must not modify tool output");

            waitForLog(appender, firstTrace, "event=response_end_succeeded");
            String logs = messages(appender);
            assertTrue(logs.contains("trace=" + firstTrace + " event=received tool=test__echo"));
            assertTrue(logs.contains("trace=" + firstTrace + " event=upstream_completed"));
            assertTrue(logs.contains("trace=" + firstTrace + " event=response_end_submitted"));
            assertTrue(logs.contains("trace=" + firstTrace + " event=response_end_succeeded"));
            assertTrue(logs.contains(" status=200 "));
            assertTrue(logs.contains(" bytes="));
            assertFalse(logs.contains(sensitiveOutput), "terminal output must not be logged");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void tracedFailureStillReturnsNormalJsonRpcErrorAndRecordsWrite() throws Exception {
        callToolBehavior = () -> Future.failedFuture(new IllegalStateException("private upstream detail"));

        Logger logger = (Logger) LoggerFactory.getLogger(McpHttpCallTrace.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            HttpResponse<String> response = post("/", toolCallJson(""), Map.of());
            String trace = response.headers().firstValue(McpHttpCallTrace.RESPONSE_HEADER).orElseThrow();
            assertEquals(-32000, JSON.readTree(response.body()).path("error").path("code").asInt());
            waitForLog(appender, trace, "event=response_end_succeeded");
            assertTrue(messages(appender).contains("trace=" + trace + " event=upstream_completed tool=test__echo outcome=failure"));
            assertFalse(messages(appender).contains("private upstream detail"),
                    "error details must not be duplicated in diagnostic logs");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void closingClientWhileToolIsPendingIsReported() throws Exception {
        CountDownLatch toolInvoked = new CountDownLatch(1);
        AtomicReference<Promise<Map<String, Object>>> pending = new AtomicReference<>();
        callToolBehavior = () -> {
            Promise<Map<String, Object>> promise = Promise.promise();
            pending.set(promise);
            toolInvoked.countDown();
            return promise.future();
        };

        Logger logger = (Logger) LoggerFactory.getLogger(McpHttpCallTrace.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String body = toolCallJson("private-command");
            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            try (Socket client = new Socket("127.0.0.1", server.actualPort())) {
                client.setSoTimeout(2000);
                String headers = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n"
                        + "Content-Length: " + bodyBytes.length + "\r\n\r\n";
                client.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
                client.getOutputStream().write(bodyBytes);
                client.getOutputStream().flush();
                assertTrue(toolInvoked.await(3, TimeUnit.SECONDS));
            }
            waitForEvent(appender, "event=connection_closed_before_end");
            String logs = messages(appender);
            assertTrue(logs.contains("tool=test__echo"));
            assertFalse(logs.contains("private-command"));
        } finally {
            // Do not leave a pending asynchronous request in the fixture.
            if (pending.get() != null) {
                pending.get().tryComplete(Map.of("content", List.of(), "isError", false));
            }
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static String toolCallJson(String argument) throws Exception {
        return JSON.writeValueAsString(Map.of("jsonrpc", "2.0", "id", 55, "method", "tools/call",
                "params", Map.of("name", "test__echo", "arguments", Map.of("command", argument))));
    }

    private static String messages(ListAppender<ILoggingEvent> appender) {
        StringBuilder result = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            result.append(event.getFormattedMessage()).append('\n');
        }
        return result.toString();
    }

    private static void waitForLog(ListAppender<ILoggingEvent> appender, String trace, String event)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < end) {
            if (messages(appender).contains("trace=" + trace + " " + event)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(messages(appender).contains("trace=" + trace + " " + event), messages(appender));
    }

    private static void waitForEvent(ListAppender<ILoggingEvent> appender, String event)
            throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < end) {
            if (messages(appender).contains(event)) {
                return;
            }
            Thread.sleep(10);
        }
        assertTrue(messages(appender).contains(event), messages(appender));
    }

    private JsonNode postRpc(String path, String method, String params) throws Exception {
        String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}", method, params);
        HttpResponse<String> response = post(path, body, Map.of());
        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body, Map<String, String> extraHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(RESPONSE_TIMEOUT)
                .header("Content-Type", "application/json");
        extraHeaders.forEach(builder::header);
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static RequestAuthenticator alwaysAuthenticated() {
        return request -> AuthenticationResult.authenticated("test");
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static final class TestServerConfig implements ServerConfig {

        @Override
        public String mcpProtocolVersion() {
            return "2025-03-26";
        }

        @Override
        public String mcpInjectedViewToken() {
            return INJECTED_VIEW_TOKEN;
        }
    }

    private final class BehaviorToolProvider implements ToolProvider {

        @Override
        public String providerId() {
            return "test";
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return listToolsBehavior.get();
        }

        @Override
        public Future<List<Map<String, Object>>> listTools(ToolView toolView) {
            return listToolsBehavior.get();
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return callToolBehavior.get();
        }
    }
}
