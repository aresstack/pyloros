package com.aresstack.pyloros.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.oauth.OAuthService;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
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
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublicEndpointCompatibilityTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ACCESS_TOKEN = "test-token";
    private static final String CANONICAL_PATH = "/pyloros";
    private static final String LEGACY_PATH = "/sse";
    private static final String TOOL_NAME = "test__echo";

    private Vertx vertx;
    private HttpServer server;
    private HttpClient httpClient;
    private String baseUrl;
    private ListAppender<ILoggingEvent> mcpLogs;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        httpClient = HttpClient.newHttpClient();

        Logger logger = (Logger) LoggerFactory.getLogger(McpRoutes.class);
        mcpLogs = new ListAppender<>();
        mcpLogs.start();
        logger.addAppender(mcpLogs);

        ServerConfig config = new TestServerConfig(
                "https://current-car.com",
                CANONICAL_PATH,
                "2025-03-26",
                "angel",
                "change-me",
                ACCESS_TOKEN,
                3600,
                2592000,
                false,
                Path.of("build", "tmp", "compat-refresh-tokens.json").toString()
        );
        OAuthService oauthService = new OAuthService(config);
        ToolProvider provider = new TestToolProvider();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, ToolNameFormatter.defaultFormatter());
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        new MetadataRoutes(config).mount(router);
        new McpRoutes(config, oauthService, toolCatalog, toolRouter).mount(router);

        server = await(vertx.createHttpServer().requestHandler(router).listen(0));
        baseUrl = "http://127.0.0.1:" + server.actualPort();
    }

    @AfterEach
    void tearDown() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(McpRoutes.class);
        if (mcpLogs != null) {
            logger.detachAppender(mcpLogs);
            mcpLogs.stop();
        }
        if (server != null) {
            await(server.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void supportsCanonicalAndLegacyRpcOperations() throws Exception {
        assertInitialize(CANONICAL_PATH);
        assertInitialize(LEGACY_PATH);
        assertToolsList(CANONICAL_PATH);
        assertToolsList(LEGACY_PATH);
        assertResourcesList(CANONICAL_PATH);
        assertResourcesList(LEGACY_PATH);
        assertToolCall(CANONICAL_PATH);
        assertToolCall(LEGACY_PATH);
        assertDirectPathToolCall(CANONICAL_PATH);
        assertDirectPathToolCall(LEGACY_PATH);
    }

    @Test
    void logsDeprecatedFlagByEndpoint() throws Exception {
        postRpc(CANONICAL_PATH, "initialize", "{}");
        postRpc(LEGACY_PATH, "initialize", "{}");

        List<String> messages = mcpLogs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(messages.stream().anyMatch(message -> message.contains("path=/pyloros")
                && message.contains("method=initialize")
                && message.contains("deprecated=false")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("path=/sse")
                && message.contains("method=initialize")
                && message.contains("deprecated=true")));
    }

    @Test
    void advertisesCanonicalMetadataAndKeepsLegacyAliases() throws Exception {
        JsonNode root = getJson("/");
        assertEquals("https://current-car.com/pyloros", root.path("mcp").asText());

        JsonNode canonicalProtected = getJson("/.well-known/oauth-protected-resource/pyloros");
        assertEquals("https://current-car.com/pyloros", canonicalProtected.path("resource").asText());

        JsonNode legacyProtected = getJson("/.well-known/oauth-protected-resource/sse");
        assertEquals("https://current-car.com/sse", legacyProtected.path("resource").asText());

        assertEquals(200, get("/pyloros/.well-known/oauth-authorization-server").statusCode());
        assertEquals(200, get("/sse/.well-known/oauth-authorization-server").statusCode());
    }

    private void assertInitialize(String path) throws Exception {
        JsonNode response = postRpc(path, "initialize", "{}");
        assertEquals("2025-03-26", response.path("result").path("protocolVersion").asText());
    }

    private void assertToolsList(String path) throws Exception {
        JsonNode response = postRpc(path, "tools/list", "{}");
        assertTrue(response.path("result").path("tools").isArray());
        assertTrue(response.path("result").path("tools").toString().contains(TOOL_NAME));
    }

    private void assertResourcesList(String path) throws Exception {
        JsonNode response = postRpc(path, "resources/list", "{}");
        assertTrue(response.path("result").path("resources").isArray());
        assertEquals(0, response.path("result").path("resources").size());
    }

    private void assertToolCall(String path) throws Exception {
        JsonNode response = postRpc(path, "tools/call", "{\"name\":\"test__echo\",\"arguments\":{\"value\":\"ok\"}}");
        assertEquals("ok", response.path("result").path("content").get(0).path("text").asText());
        assertFalse(response.path("result").path("isError").asBoolean(true));
    }

    private void assertDirectPathToolCall(String path) throws Exception {
        HttpResponse<String> response = post(path + "/" + TOOL_NAME, "{\"arguments\":{\"value\":\"ok\"}}");
        assertEquals(200, response.statusCode());
        JsonNode result = JSON.readTree(response.body());
        assertEquals("ok", result.path("content").get(0).path("text").asText());
        assertFalse(result.path("isError").asBoolean(true));
    }

    private JsonNode postRpc(String path, String method, String params) throws Exception {
        String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}", method, params);
        HttpResponse<String> response = post(path, body);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = get(path);
        assertEquals(200, response.statusCode());
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private record TestServerConfig(
            String publicOrigin,
            String mcpPublicPath,
            String mcpProtocolVersion,
            String oauthClientId,
            String oauthClientSecret,
            String fixedAccessToken,
            int oauthAccessTokenTtlSeconds,
            int oauthRefreshTokenTtlSeconds,
            boolean oauthRefreshTokenRotationEnabled,
            String oauthRefreshTokenStorePath
    ) implements ServerConfig {
    }

    private static final class TestToolProvider implements ToolProvider {

        @Override
        public String providerId() {
            return "test";
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of(Map.of(
                    "name", "echo",
                    "description", "echo test tool",
                    "inputSchema", Map.of("type", "object")
            )));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            String value = arguments == null ? "" : arguments.path("value").asText("");
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", value)),
                    "isError", false
            ));
        }
    }
}

