package com.aresstack.pyloros.http;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Size and dynamics of the {@code tools/list} response at the public MCP endpoint.
 *
 * <p>Calibrated against a measurement taken from the live instance: 421,795 UTF-8 bytes, 259 tools
 * across 9 providers, per-tool bytes min 183 / median 1,318 / mean 1,627 / max 21,121, one
 * description of 19,709 bytes, and 126 of 259 tools containing non-ASCII characters. The generator
 * below reproduces that shape so the transport is exercised at production scale.
 *
 * <p>This is an investigation, not a fix: no size limit, tool cap or fragmentation is introduced.
 * The tests assert that a response of this size is delivered complete and parseable, and that a
 * catalog refresh cannot produce a partial one.
 */
class ToolsListResponseSizeTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Measured on the live instance. */
    private static final int LIVE_TOOL_COUNT = 259;
    private static final int LIVE_RESPONSE_BYTES = 421_795;
    private static final int LIVE_LARGEST_DESCRIPTION_BYTES = 19_709;

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(30);

    private Vertx vertx;
    private HttpServer server;
    private HttpClient httpClient;
    private String baseUrl;

    /** Providers whose tool sets can be switched atomically, as a catalog refresh would. */
    private final List<GeneratedToolProvider> providers = new CopyOnWriteArrayList<>();

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

    /** Mounts the real route chain over a provider set shaped like the live catalog. */
    private void mountWithLiveShapedCatalog() throws Exception {
        // Distribution of the live instance, provider by provider.
        providers.add(new GeneratedToolProvider("intellij", 122));
        providers.add(new GeneratedToolProvider("github", 49));
        providers.add(new GeneratedToolProvider("intellij-index", 30));
        providers.add(new GeneratedToolProvider("debugger-mcp-server", 23));
        providers.add(new GeneratedToolProvider("mcp-steroid", 10));
        providers.add(new GeneratedToolProvider("acp-registry", 8));
        providers.add(new GeneratedToolProvider("pyloros-local", 7));
        providers.add(new GeneratedToolProvider("dirac", 5));
        providers.add(new GeneratedToolProvider("deepagents", 5));
        // One outsized tool, as measured.
        providers.get(0).setOversizedDescriptionBytes(LIVE_LARGEST_DESCRIPTION_BYTES);
        mount();
    }

    private void mount() throws Exception {
        ProviderRegistry providerRegistry = new ProviderRegistry(List.copyOf(providers));
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

        server = await(vertx.createHttpServer().requestHandler(router).listen(0));
        baseUrl = "http://127.0.0.1:" + server.actualPort();
    }

    // ---- 1: a large response is transferred completely ---------------------

    @Test
    void largeToolsListIsTransferredCompletelyAndParses() throws Exception {
        mountWithLiveShapedCatalog();

        HttpResponse<byte[]> response = postRpcBytes("tools/list");
        byte[] body = response.body();

        JsonNode parsed = JSON.readTree(body);
        JsonNode tools = parsed.path("result").path("tools");

        long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1);
        List<String> transferEncoding = response.headers().allValues("transfer-encoding");

        System.out.printf(
                "[tools/list] method=tools/list tools=%d bytes=%d contentLength=%d "
                        + "transferEncoding=%s status=%d contentType=%s parsed=%s%n",
                tools.size(), body.length, contentLength, transferEncoding,
                response.statusCode(), response.headers().firstValue("content-type").orElse(""),
                "ok");

        assertEquals(200, response.statusCode());
        assertEquals(LIVE_TOOL_COUNT, tools.size(), "catalog must be shaped like the live instance");
        assertTrue(tools.isArray());

        // Delivered as one complete, length-delimited JSON-RPC response.
        assertEquals(body.length, contentLength,
                "Content-Length must match the body actually received");
        assertTrue(transferEncoding.isEmpty(), "a complete response must not be chunked");

        // Production scale: at least as large as what was measured live.
        assertTrue(body.length >= LIVE_RESPONSE_BYTES * 0.8,
                "generated response should approach live size, was " + body.length);

        // Every tool is intact, not just the envelope.
        for (JsonNode tool : tools) {
            assertTrue(tool.hasNonNull("name"), "tool without name: " + tool);
            assertTrue(tool.hasNonNull("inputSchema"), "tool without inputSchema: " + tool.path("name"));
        }
        assertEquals("2.0", parsed.path("jsonrpc").asText());
        assertEquals(1, parsed.path("id").asInt());
    }

    // ---- 2: unicode, escapes and specials survive byte-exactly ------------

    @Test
    void unicodeEscapesAndSpecialCharactersSurviveIntact() throws Exception {
        mountWithLiveShapedCatalog();

        HttpResponse<byte[]> response = postRpcBytes("tools/list");
        JsonNode tools = JSON.readTree(response.body()).path("result").path("tools");

        int nonAsciiTools = 0;
        for (JsonNode tool : tools) {
            String description = tool.path("description").asText();
            assertFalse(description.contains("�"),
                    "replacement character in " + tool.path("name").asText());
            if (description.codePoints().anyMatch(codePoint -> codePoint > 127)) {
                nonAsciiTools++;
            }
        }

        System.out.printf("[tools/list] nonAsciiTools=%d of %d%n", nonAsciiTools, tools.size());
        assertTrue(nonAsciiTools > tools.size() / 3,
                "the generated catalog should be as unicode-heavy as the live one: " + nonAsciiTools);

        // The exact payload each generator produced must come back unchanged.
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            String expected = GeneratedToolProvider.expectedDescriptionFor(name);
            if (expected != null) {
                assertEquals(expected, tool.path("description").asText(),
                        "description altered in transit for " + name);
            }
        }
    }

    // ---- 3 and 4: refreshes during a request ------------------------------

    @Test
    void catalogSwitchWhileEveryProviderIsInFlightYieldsOneConsistentGeneration() throws Exception {
        mountWithLiveShapedCatalog();

        // Deterministic: every provider is held inside listTools before the generation is switched,
        // so each one necessarily builds from generation 2. No timing assumptions.
        java.util.concurrent.CountDownLatch entered =
                new java.util.concurrent.CountDownLatch(providers.size());
        CompletableFuture<Void> release = new CompletableFuture<>();
        providers.forEach(provider -> provider.gateNextListTools(release, entered));

        CompletableFuture<HttpResponse<byte[]>> inFlight = requestAsync();

        assertTrue(entered.await(20, TimeUnit.SECONDS), "all providers must be in flight");
        providers.forEach(provider -> provider.setGeneration(2));
        release.complete(null);

        HttpResponse<byte[]> response = inFlight.get(30, TimeUnit.SECONDS);
        JsonNode tools = JSON.readTree(response.body()).path("result").path("tools");

        assertEquals(200, response.statusCode());
        assertEquals(LIVE_TOOL_COUNT, tools.size(), "no tools may be lost across a generation switch");
        assertEquals(body(response).length,
                response.headers().firstValueAsLong("content-length").orElse(-1));

        Map<String, Integer> generationsByProvider = generationsByProvider(tools);
        System.out.printf("[tools/list] switchedWhileInFlight generationsByProvider=%s%n",
                generationsByProvider);
        assertEquals(providers.size(), generationsByProvider.size(), "every provider must be represented");
        assertTrue(generationsByProvider.values().stream().allMatch(generation -> generation == 2),
                "every provider must reflect the switched generation: " + generationsByProvider);
    }

    @Test
    void refreshBetweenProviderCallsStillProducesACompleteResponse() throws Exception {
        mountWithLiveShapedCatalog();

        // Deterministic cross-provider skew: the first provider builds, *then* the generation is
        // switched, *then* the rest build. This documents that one tools/list response is not a
        // single-point-in-time snapshot across providers — it is assembled per provider.
        GeneratedToolProvider first = providers.get(0);
        java.util.concurrent.CountDownLatch entered =
                new java.util.concurrent.CountDownLatch(providers.size());
        CompletableFuture<Void> releaseFirst = new CompletableFuture<>();
        CompletableFuture<Void> releaseRest = new CompletableFuture<>();

        first.gateNextListTools(releaseFirst, entered);
        providers.subList(1, providers.size())
                .forEach(provider -> provider.gateNextListTools(releaseRest, entered));

        CompletableFuture<HttpResponse<byte[]>> inFlight = requestAsync();

        assertTrue(entered.await(20, TimeUnit.SECONDS), "all providers must be in flight");
        releaseFirst.complete(null);
        first.awaitBuilt();
        providers.forEach(provider -> provider.setGeneration(2));
        releaseRest.complete(null);

        HttpResponse<byte[]> response = inFlight.get(30, TimeUnit.SECONDS);
        JsonNode tools = JSON.readTree(response.body()).path("result").path("tools");

        Map<String, Integer> generationsByProvider = generationsByProvider(tools);
        System.out.printf("[tools/list] skewedRefresh generationsByProvider=%s%n", generationsByProvider);

        // The response is complete and valid regardless of the skew — that is what matters to a
        // client. Each provider's own tool set is internally consistent.
        assertEquals(200, response.statusCode());
        assertEquals(LIVE_TOOL_COUNT, tools.size(), "no tools may be lost");
        assertEquals(body(response).length,
                response.headers().firstValueAsLong("content-length").orElse(-1));
        assertEquals(1, generationsByProvider.get(first.providerId().replace('-', '_')),
                "the first provider built before the switch");
        assertTrue(generationsByProvider.values().stream().anyMatch(generation -> generation == 2),
                "later providers built after the switch: " + generationsByProvider);
    }

    /** @return generation per provider, asserting each provider's own set is not torn. */
    private Map<String, Integer> generationsByProvider(JsonNode tools) {
        Map<String, Integer> generationsByProvider = new LinkedHashMap<>();
        for (JsonNode tool : tools) {
            String name = tool.path("name").asText();
            String provider = name.substring(0, name.indexOf("__"));
            int generation = tool.path("_generation").asInt(-1);
            Integer seen = generationsByProvider.putIfAbsent(provider, generation);
            assertTrue(seen == null || seen == generation,
                    "provider " + provider + " returned a torn tool set: " + seen + " and " + generation);
        }
        return generationsByProvider;
    }

    private CompletableFuture<HttpResponse<byte[]>> requestAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return postRpcBytes("tools/list");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static byte[] body(HttpResponse<byte[]> response) {
        return response.body();
    }

    @Test
    void rapidSuccessiveRefreshesLoseNoTools() throws Exception {
        mountWithLiveShapedCatalog();

        // Mimics a burst of list_changed notifications: flip generations while requests run.
        for (int round = 0; round < 6; round++) {
            int generation = round + 2;
            providers.forEach(provider -> provider.setGeneration(generation));

            HttpResponse<byte[]> response = postRpcBytes("tools/list");
            JsonNode tools = JSON.readTree(response.body()).path("result").path("tools");

            assertEquals(200, response.statusCode(), "round " + round);
            assertEquals(LIVE_TOOL_COUNT, tools.size(), "tools lost in round " + round);
            assertFalse(JSON.readTree(response.body()).has("error"), "error in round " + round);
        }
    }

    @Test
    void concurrentRequestsDuringARefreshAllReturnCompleteResponses() throws Exception {
        mountWithLiveShapedCatalog();

        // Every provider is held inside listTools, so all six requests are queued behind the same
        // unfinished refresh before the generation is switched.
        java.util.concurrent.CountDownLatch entered =
                new java.util.concurrent.CountDownLatch(providers.size());
        CompletableFuture<Void> release = new CompletableFuture<>();
        providers.forEach(provider -> provider.gateNextListTools(release, entered));

        int concurrency = 6;
        List<CompletableFuture<HttpResponse<byte[]>>> calls = new ArrayList<>();
        for (int index = 0; index < concurrency; index++) {
            calls.add(requestAsync());
        }

        assertTrue(entered.await(20, TimeUnit.SECONDS), "all providers must be in flight");
        providers.forEach(provider -> provider.setGeneration(3));
        release.complete(null);

        for (int index = 0; index < concurrency; index++) {
            HttpResponse<byte[]> response = calls.get(index).get(30, TimeUnit.SECONDS);
            JsonNode parsed = JSON.readTree(response.body());
            assertEquals(200, response.statusCode(), "call " + index);
            assertEquals(LIVE_TOOL_COUNT, parsed.path("result").path("tools").size(), "call " + index);
            assertEquals(response.body().length,
                    response.headers().firstValueAsLong("content-length").orElse(-1),
                    "truncated response for call " + index);
        }
    }

    // ---- 6: the test client must detect a truncated response --------------

    @Test
    void aTruncatedResponseIsDetectedAndNotAcceptedAsComplete() throws Exception {
        // Guards the premise of every assertion above: if the client silently accepted short
        // bodies, "transferred completely" would be unverifiable.
        byte[] payload = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":[" + "x".repeat(4096) + "]}}")
                .getBytes(StandardCharsets.UTF_8);

        HttpServer truncating = await(vertx.createHttpServer().requestHandler(request ->
                request.bodyHandler(ignored -> {
                    request.response()
                            .putHeader("Content-Type", "application/json")
                            .putHeader("Content-Length", String.valueOf(payload.length));
                    // Announce the full length but deliver less, then kill the connection.
                    byte[] partial = new byte[payload.length / 2];
                    System.arraycopy(payload, 0, partial, 0, partial.length);
                    request.response().write(io.vertx.core.buffer.Buffer.buffer(partial));
                    request.connection().close();
                })).listen(0));

        try {
            String url = "http://127.0.0.1:" + truncating.actualPort() + "/";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(CLIENT_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}"))
                    .build();

            try {
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                throw new AssertionError("a truncated body must not be returned as complete, got "
                        + response.body().length + " of " + payload.length + " bytes");
            } catch (java.io.IOException expected) {
                System.out.printf("[truncation] detected as expected: %s%n", expected.getClass().getSimpleName());
                assertNotNull(expected);
            }
        } finally {
            await(truncating.close());
        }
    }

    // ---- helpers ----------------------------------------------------------

    private HttpResponse<byte[]> postRpcBytes(String method) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":{}}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/"))
                .timeout(CLIENT_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
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

    /**
     * Generates tool descriptors sized like the live catalog, with a switchable generation so a
     * catalog refresh can be simulated atomically.
     */
    private static final class GeneratedToolProvider implements ToolProvider {

        /** Shared so a test can assert a description came back byte-identical. */
        private static final Map<String, String> EXPECTED_DESCRIPTIONS = new java.util.concurrent.ConcurrentHashMap<>();

        private static final String UNICODE_SAMPLE =
                "Grüße aus Köln — „Anführungszeichen\", Backslash \\, Tab\t, Emoji 😀, CJK 日本語, quote \"inner\"";

        private final String providerId;
        private final int toolCount;
        private final AtomicInteger generation = new AtomicInteger(1);
        private final AtomicReference<CompletableFuture<Void>> gate = new AtomicReference<>();
        private final AtomicReference<java.util.concurrent.CountDownLatch> entrySignal = new AtomicReference<>();
        private final java.util.concurrent.CountDownLatch built = new java.util.concurrent.CountDownLatch(1);
        private volatile int oversizedDescriptionBytes;

        GeneratedToolProvider(String providerId, int toolCount) {
            this.providerId = providerId;
            this.toolCount = toolCount;
        }

        static String expectedDescriptionFor(String externalName) {
            return EXPECTED_DESCRIPTIONS.get(externalName);
        }

        void setGeneration(int value) {
            generation.set(value);
        }

        void setOversizedDescriptionBytes(int bytes) {
            this.oversizedDescriptionBytes = bytes;
        }

        /**
         * Holds the next {@code listTools} call until {@code release} completes, counting down
         * {@code entrySignal} once it is inside. That makes "a refresh happened while the request
         * was in flight" an explicit event rather than a timing assumption.
         */
        void gateNextListTools(CompletableFuture<Void> release,
                               java.util.concurrent.CountDownLatch entrySignal) {
            this.entrySignal.set(entrySignal);
            gate.set(release);
        }

        void awaitBuilt() throws Exception {
            if (!built.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("provider " + providerId + " never built its tool list");
            }
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return listTools(ToolView.PUBLIC);
        }

        @Override
        public Future<List<Map<String, Object>>> listTools(ToolView toolView) {
            CompletableFuture<Void> release = gate.getAndSet(null);
            if (release == null) {
                return Future.succeededFuture(build());
            }

            // Signal that the provider is inside listTools, then wait for the test to release it.
            java.util.concurrent.CountDownLatch signal = entrySignal.getAndSet(null);
            if (signal != null) {
                signal.countDown();
            }
            Promise<List<Map<String, Object>>> promise = Promise.promise();
            release.whenComplete((ignored, error) -> {
                List<Map<String, Object>> tools = build();
                built.countDown();
                promise.complete(tools);
            });
            return promise.future();
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            return Future.succeededFuture(Map.of("content", List.of(), "isError", false));
        }

        private List<Map<String, Object>> build() {
            int currentGeneration = generation.get();
            List<Map<String, Object>> tools = new ArrayList<>(toolCount);

            for (int index = 0; index < toolCount; index++) {
                String name = "tool_" + index;
                String externalName = providerId.replace('-', '_') + "__" + name;

                // Reproduce the measured spread: most tools around 1.3 KB, a few much larger.
                int descriptionRepeats = switch (index % 10) {
                    case 0 -> 1;
                    case 9 -> 40;
                    default -> 12;
                };
                StringBuilder description = new StringBuilder();
                for (int repeat = 0; repeat < descriptionRepeats; repeat++) {
                    description.append(UNICODE_SAMPLE).append(' ');
                }
                if (index == 0 && oversizedDescriptionBytes > 0) {
                    while (description.toString().getBytes(StandardCharsets.UTF_8).length
                            < oversizedDescriptionBytes) {
                        description.append(UNICODE_SAMPLE).append(' ');
                    }
                }
                String descriptionText = description.toString().trim();
                EXPECTED_DESCRIPTIONS.put(externalName, descriptionText);

                Map<String, Object> properties = new LinkedHashMap<>();
                for (int property = 0; property < 6; property++) {
                    properties.put("arg" + property, Map.of(
                            "type", "string",
                            "description", "Argument " + property + " — " + UNICODE_SAMPLE
                    ));
                }

                Map<String, Object> tool = new LinkedHashMap<>();
                tool.put("name", name);
                tool.put("description", descriptionText);
                tool.put("inputSchema", Map.of("type", "object", "properties", properties));
                tool.put("_generation", currentGeneration);
                tools.add(tool);
            }

            return tools;
        }
    }
}
