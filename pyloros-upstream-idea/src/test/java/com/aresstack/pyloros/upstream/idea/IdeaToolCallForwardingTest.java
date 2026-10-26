package com.aresstack.pyloros.upstream.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What Pyloros does and does not do with a {@code tools/call}.
 *
 * <p>A tool's execution deadline belongs to the MCP server that runs the tool. Pyloros forwards the
 * arguments as received and waits for an answer for exactly as long as the configured
 * {@code responseTimeoutMillis} says — it does not read, rewrite or extrapolate from an argument
 * like {@code timeout} or {@code timeoutSeconds}.
 *
 * <p>The pair of timeout tests is what pins that down: identical arguments and an identical IDE
 * delay, decided solely by the configured value.
 */
class IdeaToolCallForwardingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** How long the fake IDE takes to answer, in both timeout tests. */
    private static final long IDE_ANSWER_DELAY_MILLIS = 1_500;

    private Vertx vertx;
    private HttpServer ideServer;
    private IdeaMcpClient ideaClient;
    private IdeaToolProvider provider;

    private final CopyOnWriteArrayList<HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();
    private final AtomicReference<JsonObject> receivedRequest = new AtomicReference<>();

    private volatile long answerDelayMillis;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        answerDelayMillis = 0;

        ideServer = await(vertx.createHttpServer().requestHandler(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST) {
                request.bodyHandler(body -> {
                    JsonObject rpc = new JsonObject(body);
                    if ("tools/call".equals(rpc.getString("method"))) {
                        receivedRequest.set(rpc);
                    }
                    request.response().setStatusCode(202).end();
                    if (answerDelayMillis <= 0) {
                        pushResult(rpc.getValue("id"));
                    } else {
                        vertx.setTimer(answerDelayMillis, t -> pushResult(rpc.getValue("id")));
                    }
                });
                return;
            }

            HttpServerResponse response = request.response()
                    .setChunked(true)
                    .putHeader("Content-Type", "text/event-stream");
            liveStreams.add(response);
            response.write("data: /messages/fake\r\nevent: endpoint\r\n\r\n");
        }).listen(0));
    }

    private void startProvider(int responseTimeoutMillis) throws Exception {
        IdeaMcpConfig config = new IdeaMcpConfig(
                URI.create("http://127.0.0.1:" + ideServer.actualPort() + "/sse"),
                Map.of(),
                1000,
                responseTimeoutMillis
        );

        ideaClient = new IdeaMcpClient(vertx, config);
        ideaClient.start();
        waitUntil(ideaClient::isReady);

        provider = new IdeaToolProvider(config, ideaClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ideaClient != null) {
            await(ideaClient.stop());
        }
        if (ideServer != null) {
            await(ideServer.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    @Test
    void argumentsReachTheIdeUnchanged() throws Exception {
        startProvider(5_000);

        String arguments = "{"
                + "\"command\":\"sleep 75\","
                + "\"timeout\":100000,"
                + "\"timeoutSeconds\":600,"
                + "\"executeInShell\":true,"
                + "\"nested\":{\"keep\":[1,2,3]}"
                + "}";

        await(provider.callTool("execute_terminal_command", JSON.readTree(arguments)));

        JsonObject forwarded = receivedRequest.get().getJsonObject("params");
        assertEquals("execute_terminal_command", forwarded.getString("name"));
        // Compared as a whole, so an added, dropped or rewritten member fails the test.
        assertEquals(new JsonObject(arguments), forwarded.getJsonObject("arguments"));
    }

    @Test
    void aDeclaredToolTimeoutDoesNotExtendTheConfiguredWait() throws Exception {
        // The configured wait is shorter than the IDE takes, even though the call declares a far
        // longer deadline of its own. Pyloros must still give up when its own timeout elapses.
        answerDelayMillis = IDE_ANSWER_DELAY_MILLIS;
        startProvider(800);

        Map<String, Object> result = await(provider.callTool("execute_terminal_command",
                JSON.readTree("{\"command\":\"sleep 75\",\"timeout\":100000}")));

        assertTrue((Boolean) result.get("isError"),
                "the declared deadline must not extend the configured wait: " + result);
        assertTrue(firstText(result).contains("Timeout waiting for IDEA SSE response"),
                "expected the configured timeout to apply, got: " + firstText(result));
    }

    @Test
    void aConfiguredTimeoutLongerThanTheCallIsHonoured() throws Exception {
        // Same arguments and the same IDE delay as above; only the configured value differs.
        answerDelayMillis = IDE_ANSWER_DELAY_MILLIS;
        startProvider(5_000);

        long startNanos = System.nanoTime();
        Map<String, Object> result = await(provider.callTool("execute_terminal_command",
                JSON.readTree("{\"command\":\"sleep 75\",\"timeout\":100000}")));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertFalse((Boolean) result.get("isError"), "expected the call to succeed: " + result);
        assertEquals("command finished", firstText(result));
        assertTrue(elapsedMillis >= IDE_ANSWER_DELAY_MILLIS,
                "the call must actually have waited for the IDE; took " + elapsedMillis + "ms");
    }

    @SuppressWarnings("unchecked")
    private static String firstText(Map<String, Object> result) {
        Object content = result.get("content");
        Object first = content instanceof List<?> list ? list.get(0) : ((Object[]) content)[0];
        return String.valueOf(((Map<String, Object>) first).get("text"));
    }

    private void pushResult(Object id) {
        JsonObject message = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("result", new JsonObject()
                        .put("content", new JsonArray().add(new JsonObject()
                                .put("type", "text")
                                .put("text", "command finished")))
                        .put("isError", false));

        for (HttpServerResponse stream : List.copyOf(liveStreams)) {
            try {
                stream.write("data: " + message.encode() + "\r\nevent: message\r\n\r\n");
            } catch (Exception ignored) {
            }
        }
    }

    private void waitUntil(BooleanCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (condition.test()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within 5000ms");
    }

    private interface BooleanCondition {
        boolean test();
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS);
    }
}
