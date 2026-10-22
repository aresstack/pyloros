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
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
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
