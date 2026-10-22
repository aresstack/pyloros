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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance tests for the MCP Streamable HTTP endpoint at the root path.
 *
 * <p>Covers the two protocol deviations that a Streamable HTTP client can observe directly: GET
 * used to return a deprecated HTTP+SSE {@code endpoint} frame instead of declining the stream,
 * and notifications used to be answered with a JSON body instead of a bodyless 202.
 */
class McpTransportConformanceTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private Vertx vertx;
    private HttpServer server;
    private HttpClient httpClient;
    private String baseUrl;
    private boolean authenticated;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        httpClient = HttpClient.newHttpClient();
        authenticated = true;

        ToolProvider provider = new EchoToolProvider();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, ToolNameFormatter.defaultFormatter());
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        new McpRoutes(
                new TestServerConfig(),
                request -> authenticated
                        ? AuthenticationResult.authenticated("test")
                        : AuthenticationResult.rejected(401, "invalid_token", Map.of()),
                toolCatalog,
                toolRouter
        ).mount(router);

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
    void getOnMcpEndpointIsRejectedWithMethodNotAllowed() throws Exception {
        HttpResponse<String> response = get("/");

        assertEquals(405, response.statusCode());
        assertEquals("POST", response.headers().firstValue("Allow").orElse(""));

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertFalse(contentType.contains("text/event-stream"),
                "GET must no longer open a deprecated HTTP+SSE stream");
        assertFalse(response.body().contains("event: endpoint"),
                "GET must no longer emit the legacy endpoint frame");
    }

    @Test
    void getStillRequiresAuthenticationBeforeReportingMethodNotAllowed() throws Exception {
        authenticated = false;

        HttpResponse<String> response = get("/");

        assertEquals(401, response.statusCode());
    }

    @Test
    void notificationIsAcceptedWithoutResponseBody() throws Exception {
        HttpResponse<String> response = post(
                "/",
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}"
        );

        assertEquals(202, response.statusCode());
        assertTrue(response.body().isEmpty(), "202 Accepted must carry no body, was: " + response.body());
        assertTrue(response.headers().firstValue("Content-Type").isEmpty(),
                "a bodyless response must not declare a content type");
    }

    @Test
    void explicitNullIdIsAlsoTreatedAsNotification() throws Exception {
        HttpResponse<String> response = post(
                "/",
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"notifications/initialized\",\"params\":{}}"
        );

        assertEquals(202, response.statusCode());
        assertTrue(response.body().isEmpty());
    }

    @Test
    void postRequestsWithAnIdStillAnswerNormally() throws Exception {
        HttpResponse<String> response = post(
                "/",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}"
        );

        assertEquals(200, response.statusCode());
        JsonNode body = HttpJson.mapper().readTree(response.body());
        assertEquals("2025-03-26", body.path("result").path("protocolVersion").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(RESPONSE_TIMEOUT)
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(RESPONSE_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private record TestServerConfig() implements ServerConfig {
        @Override
        public String mcpProtocolVersion() {
            return "2025-03-26";
        }
    }

    private static final class EchoToolProvider implements ToolProvider {

        @Override
        public String providerId() {
            return "test";
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return listTools(ToolView.PUBLIC);
        }

        @Override
        public Future<List<Map<String, Object>>> listTools(ToolView toolView) {
            return Future.succeededFuture(List.of(Map.of(
                    "name", "echo",
                    "description", "echo test tool",
                    "inputSchema", Map.of("type", "object")
            )));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return Future.succeededFuture(Map.of("content", List.of(), "isError", false));
        }
    }
}
