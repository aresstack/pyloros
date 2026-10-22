package com.aresstack.pyloros.upstream.mcp;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests from the public MCP endpoint through to a faulty upstream.
 *
 * <p>The chain is the production one: {@link McpRoutes} → {@link ToolRouter} →
 * {@link GenericMcpToolProvider} → {@link StreamableHttpMcpUpstreamClient} → a controllable
 * upstream. What matters here is not just the payload but the <em>latency</em>: the MCP client's
 * "Error in input stream" is what a client reports when its connection dies before an answer
 * arrives, so every test bounds how long Pyloros may take to answer a broken backend.
 */
class McpUpstreamFailureEndToEndTest {

    /** The production default from McpJsonConfigLoader. */
    private static final int PRODUCTION_RESPONSE_TIMEOUT_MILLIS = 60_000;

    /** Any answer must arrive far sooner than a client or proxy would give up. */
    private static final long ACCEPTABLE_ANSWER_MILLIS = 10_000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private Vertx vertx;
    private McpUpstreamTestServer upstream;
    private StreamableHttpMcpUpstreamClient upstreamClient;
    private HttpServer pyloros;
    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (upstreamClient != null) {
            await(upstreamClient.stop());
        }
        if (pyloros != null) {
            await(pyloros.close());
        }
        if (upstream != null) {
            upstream.close();
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    private void mountPyloros() throws Exception {
        McpUpstreamConfig config = upstream.config("up", PRODUCTION_RESPONSE_TIMEOUT_MILLIS);
        upstreamClient = new StreamableHttpMcpUpstreamClient(vertx, config);
        GenericMcpToolProvider provider = new GenericMcpToolProvider(config, upstreamClient);
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
    }

    @Test
    void heldOpenSseUpstreamDoesNotStallThePublicEndpoint() throws Exception {
        // The upstream answers immediately but keeps the SSE stream open, as it is allowed to.
        // Reading such a response as a whole body meant waiting for the full 60s response timeout
        // before Pyloros answered its own client — long past the point where a client or proxy
        // cuts the connection and reports a broken input stream.
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "s1");
                return;
            }
            if ("tools/list".equals(recorded.rpcMethod())) {
                HttpServerResponse response = McpUpstreamTestServer.beginSse(request, null);
                response.write(McpUpstreamTestServer.sseFrame(recorded.rpcId(), toolsResult()));
                return;
            }
            if ("tools/call".equals(recorded.rpcMethod())) {
                HttpServerResponse response = McpUpstreamTestServer.beginSse(request, null);
                response.write(McpUpstreamTestServer.sseFrame(recorded.rpcId(), new JsonObject()
                        .put("content", new JsonArray().add(new JsonObject()
                                .put("type", "text").put("text", "ok")))
                        .put("isError", false)));
                return;
            }
            request.response().setStatusCode(202).end();
        });
        mountPyloros();

        Timed call = timedPostRpc("tools/call", "{\"name\":\"up__echo\",\"arguments\":{}}");

        assertEquals(200, call.statusCode());
        assertEquals("ok", call.body().path("result").path("content").get(0).path("text").asText());
        assertTrue(call.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS,
                "a held-open upstream stream must not stall the public endpoint; took "
                        + call.elapsedMillis() + "ms of a " + PRODUCTION_RESPONSE_TIMEOUT_MILLIS + "ms timeout");
    }

    @Test
    void upstreamDroppingTheConnectionIsAnsweredPromptly() throws Exception {
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "s2");
                return;
            }
            if ("tools/list".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), toolsResult(), null);
                return;
            }
            if ("tools/call".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.dropConnection(request);
                return;
            }
            request.response().setStatusCode(202).end();
        });
        mountPyloros();

        Timed call = timedPostRpc("tools/call", "{\"name\":\"up__echo\",\"arguments\":{}}");

        // A defined answer, promptly, and never a half-written response.
        assertEquals(200, call.statusCode());
        assertTrue(call.body().has("result") || call.body().has("error"),
                "must be a well-formed JSON-RPC envelope: " + call.body());
        assertTrue(call.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS,
                "a dropped upstream connection must be answered promptly; took " + call.elapsedMillis() + "ms");
    }

    @Test
    void upstreamErrorStatusIsAnsweredPromptly() throws Exception {
        AtomicInteger listCount = new AtomicInteger();
        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(), "s3");
                return;
            }
            if ("tools/list".equals(recorded.rpcMethod())) {
                if (listCount.incrementAndGet() == 1) {
                    McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), toolsResult(), null);
                    return;
                }
                request.response().setStatusCode(500).end("boom");
                return;
            }
            if ("tools/call".equals(recorded.rpcMethod())) {
                request.response().setStatusCode(500).end("boom");
                return;
            }
            request.response().setStatusCode(202).end();
        });
        mountPyloros();

        Timed call = timedPostRpc("tools/call", "{\"name\":\"up__echo\",\"arguments\":{}}");

        assertEquals(200, call.statusCode());
        assertTrue(call.body().has("result") || call.body().has("error"));
        assertFalse(call.body().toString().contains("\"message\":null"));
        assertTrue(call.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS,
                "an upstream 5xx must be answered promptly; took " + call.elapsedMillis() + "ms");
    }

    @Test
    void expiredUpstreamSessionRecoversWithoutFailingThePublicCall() throws Exception {
        AtomicInteger initializeCount = new AtomicInteger();
        AtomicInteger callCount = new AtomicInteger();

        upstream = McpUpstreamTestServer.start(vertx, (recorded, request) -> {
            if ("initialize".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject(),
                        "s" + initializeCount.incrementAndGet());
                return;
            }
            if ("tools/list".equals(recorded.rpcMethod())) {
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), toolsResult(), null);
                return;
            }
            if ("tools/call".equals(recorded.rpcMethod())) {
                // The upstream forgot the session exactly once.
                if (callCount.incrementAndGet() == 1) {
                    request.response().setStatusCode(404).end("{\"error\":\"session not found\"}");
                    return;
                }
                McpUpstreamTestServer.respondJsonRpcResult(request, recorded.rpcId(), new JsonObject()
                        .put("content", new JsonArray().add(new JsonObject()
                                .put("type", "text").put("text", "recovered")))
                        .put("isError", false), null);
                return;
            }
            request.response().setStatusCode(202).end();
        });
        mountPyloros();

        Timed call = timedPostRpc("tools/call", "{\"name\":\"up__echo\",\"arguments\":{}}");

        assertEquals(200, call.statusCode());
        assertEquals("recovered", call.body().path("result").path("content").get(0).path("text").asText());
        assertEquals(2, initializeCount.get(), "the expired session must be re-established");
        assertTrue(call.elapsedMillis() < ACCEPTABLE_ANSWER_MILLIS);
    }

    private static JsonObject toolsResult() {
        return new JsonObject().put("tools", new JsonArray().add(new JsonObject()
                .put("name", "echo")
                .put("description", "echo")
                .put("inputSchema", new JsonObject().put("type", "object"))));
    }

    private record Timed(int statusCode, JsonNode body, long elapsedMillis) {
    }

    private Timed timedPostRpc(String method, String params) throws Exception {
        String body = String.format("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}", method, params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                // Deliberately well below the upstream response timeout: if Pyloros waits for the
                // upstream timeout instead of answering, this request fails rather than passing slowly.
                .timeout(Duration.ofMillis(ACCEPTABLE_ANSWER_MILLIS + 5_000))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        return new Timed(response.statusCode(), JSON.readTree(response.body()), elapsedMillis);
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(20, TimeUnit.SECONDS);
    }

    private record TestServerConfig() implements ServerConfig {
        @Override
        public String mcpProtocolVersion() {
            return "2025-03-26";
        }
    }
}
