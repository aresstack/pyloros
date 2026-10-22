package com.aresstack.pyloros.upstream.idea;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolRouter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test from the public MCP endpoint through to the IDEA SSE upstream.
 *
 * <p>The chain is the production one: {@link McpRoutes} → {@link ToolRouter} →
 * {@link IdeaToolProvider} → {@link IdeaMcpClient} → {@link IdeaSseSession} → a controllable SSE
 * server. Responses travel back over the SSE stream, matched by request id, exactly as the IntelliJ
 * MCP plugin does it.
 *
 * <p>The question this answers: <em>are failures from the SSE path propagated to the public
 * endpoint?</em> They are not. {@code IdeaMcpClient.listTools()} completes with an empty list on
 * every failure path, so a dropped IDE connection makes tools silently disappear rather than
 * producing a JSON-RPC error or a stalled response. That is worth pinning down, because it means
 * this path cannot produce the hung response that a broken input stream would require.
 */
class IdeaSseEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int RECONNECT_DELAY_MILLIS = 1000;
    private static final long ACCEPTABLE_ANSWER_MILLIS = 10_000;

    private Vertx vertx;
    private HttpServer ideServer;
    private HttpServer pyloros;
    private HttpClient httpClient;
    private String baseUrl;
    private IdeaMcpClient ideaClient;

    private final List<HttpConnection> liveConnections = new CopyOnWriteArrayList<>();
    private final List<HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();

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

        ideServer = await(vertx.createHttpServer().requestHandler(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST) {
                request.bodyHandler(body -> {
                    request.response().setStatusCode(202).end();
                    JsonObject rpc = new JsonObject(body);
                    pushResult(rpc);
                });
                return;
            }

            liveConnections.add(request.connection());
            HttpServerResponse response = request.response()
                    .setChunked(true)
                    .putHeader("Content-Type", "text/event-stream");
            liveStreams.add(response);
            response.write("data: /messages/fake\r\nevent: endpoint\r\n\r\n");
        }).listen(0));

        IdeaMcpConfig ideaConfig = new IdeaMcpConfig(
                URI.create("http://127.0.0.1:" + ideServer.actualPort() + "/sse"),
                Map.of(),
                RECONNECT_DELAY_MILLIS,
                3000
        );
        ideaClient = new IdeaMcpClient(vertx, ideaConfig);
        ideaClient.start();

        IdeaToolProvider provider = new IdeaToolProvider(ideaConfig, ideaClient);
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry, ToolNameFormatter.defaultFormatter());
        ToolRouter toolRouter = new ToolRouter(providerRegistry, toolCatalog);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        new McpRoutes(
                new TestServerConfig(),
                request -> AuthenticationResult.authenticated("test"),
                toolCatalog,
                toolRouter
        ).mount(router);

        pyloros = await(vertx.createHttpServer().requestHandler(router).listen(0));
        baseUrl = "http://127.0.0.1:" + pyloros.actualPort();

        waitUntil(ideaClient::isReady);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ideaClient != null) {
            await(ideaClient.stop());
        }
        if (pyloros != null) {
            await(pyloros.close());
        }
        if (ideServer != null) {
            await(ideServer.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    /** Answers a JSON-RPC request over the SSE stream, as the IntelliJ plugin does. */
    private void pushResult(JsonObject rpc) {
        Object id = rpc.getValue("id");
        String method = rpc.getString("method");
        JsonObject result = "tools/list".equals(method)
                ? new JsonObject().put("tools", new JsonArray().add(new JsonObject()
                        .put("name", "echo")
                        .put("description", "echo test tool")
                        .put("inputSchema", new JsonObject().put("type", "object"))))
                : new JsonObject();

        JsonObject message = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", result);

        for (HttpServerResponse stream : List.copyOf(liveStreams)) {
            try {
                stream.write("data: " + message.encode() + "\r\nevent: message\r\n\r\n");
            } catch (Exception ignored) {
            }
        }
    }

    private void dropLiveConnections() {
        for (HttpConnection connection : List.copyOf(liveConnections)) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
        liveConnections.clear();
        liveStreams.clear();
    }

    @Test
    void toolsFromTheIdeAreExposedAtThePublicEndpoint() throws Exception {
        Timed response = timedPostRpc("tools/list", "{}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().path("result").path("tools").toString().contains("intellij__echo"),
                "IDE tools must be exposed: " + response.body().path("result").path("tools"));
    }

    @Test
    void droppedIdeConnectionYieldsAnEmptyToolListRatherThanAnErrorOrAStall() throws Exception {
        assertTrue(timedPostRpc("tools/list", "{}").body()
                .path("result").path("tools").toString().contains("intellij__echo"));

        dropLiveConnections();
        waitUntil(() -> !ideaClient.isReady());

        Timed response = timedPostRpc("tools/list", "{}");

        // No JSON-RPC error, no stall — the tools simply vanish. This is the real, and silent,
        // observable consequence of an IDE disconnect at the public endpoint.
        assertEquals(200, response.statusCode());
        assertFalse(response.body().has("error"), "the SSE failure must not surface as an RPC error");
        assertEquals(0, response.body().path("result").path("tools").size(),
                "tools are expected to disappear while the IDE is disconnected");
        assertTrue(response.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS,
                "a disconnected IDE must not delay the public endpoint; took "
                        + response.elapsedMillis() + "ms");
    }

    @Test
    void toolsReappearAfterTheIdeConnectionIsReestablished() throws Exception {
        dropLiveConnections();
        waitUntil(() -> !ideaClient.isReady());

        // The session reconnects on its own; the fake IDE accepts the new connection.
        waitUntil(ideaClient::isReady, 10_000);

        Timed response = timedPostRpc("tools/list", "{}");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().path("result").path("tools").toString().contains("intellij__echo"),
                "tools must come back after reconnect: " + response.body().path("result").path("tools"));
    }

    @Test
    void toolCallWhileDisconnectedReturnsAnErrorResultNotAStall() throws Exception {
        assertTrue(timedPostRpc("tools/list", "{}").body()
                .path("result").path("tools").toString().contains("intellij__echo"));

        dropLiveConnections();
        waitUntil(() -> !ideaClient.isReady());

        Timed response = timedPostRpc("tools/call", "{\"name\":\"intellij__echo\",\"arguments\":{}}");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().path("result").path("isError").asBoolean(false)
                        || response.body().has("error"),
                "a call against a disconnected IDE must report an error: " + response.body());
        assertTrue(response.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS,
                "must not wait for the response timeout; took " + response.elapsedMillis() + "ms");
    }

    private record Timed(int statusCode, JsonNode body, long elapsedMillis) {
    }

    private Timed timedPostRpc(String method, String params) throws Exception {
        String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}", method, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .timeout(Duration.ofMillis(ACCEPTABLE_ANSWER_MILLIS + 5_000))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        return new Timed(response.statusCode(), JSON.readTree(response.body()), elapsedMillis);
    }

    private void waitUntil(BooleanCondition condition) throws Exception {
        waitUntil(condition, 5000);
    }

    private void waitUntil(BooleanCondition condition, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within " + timeoutMillis + "ms");
    }

    private interface BooleanCondition {
        boolean test();
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(15, TimeUnit.SECONDS);
    }

    private record TestServerConfig() implements ServerConfig {
        @Override
        public String mcpProtocolVersion() {
            return "2025-03-26";
        }
    }
}
