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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression test for the reproduced 60 s cut-off.
 *
 * <p>A terminal command that needed 75 s was sent with {@code timeout: 100000} and still failed
 * after the configured 60 s with {@code Timeout waiting for IDEA SSE response}. The tool's declared
 * deadline had no path into the transport, so the gateway gave up while the command was still
 * running in the IDE.
 *
 * <p>Same shape here on a 1 s scale: the configured timeout stands in for the 60 s, the declared
 * deadline for the 100 s, and the IDE's answer arrives after the configured timeout has passed. The
 * control test runs the identical scenario <em>without</em> the argument and must still fail — the
 * point is that the declared deadline is what makes the difference, not a generally longer wait.
 */
class IdeaLongRunningToolCallTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Stands in for the production 60 s. */
    private static final int CONFIGURED_RESPONSE_TIMEOUT_MILLIS = 1_000;
    /** Stands in for the 75 s the command actually needed: past the configured timeout. */
    private static final long IDE_ANSWER_DELAY_MILLIS = 2_000;
    /** Stands in for the caller's {@code timeout: 100000}. */
    private static final int DECLARED_TOOL_TIMEOUT_MILLIS = 3_000;

    private Vertx vertx;
    private HttpServer ideServer;
    private IdeaMcpClient ideaClient;
    private IdeaToolProvider provider;

    private final CopyOnWriteArrayList<HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();

        ideServer = await(vertx.createHttpServer().requestHandler(request -> {
            if (request.method() == io.vertx.core.http.HttpMethod.POST) {
                request.bodyHandler(body -> {
                    JsonObject rpc = new JsonObject(body);
                    request.response().setStatusCode(202).end();
                    // The IDE takes its time, exactly as a long-running terminal command does.
                    vertx.setTimer(IDE_ANSWER_DELAY_MILLIS, t -> pushResult(rpc.getValue("id")));
                });
                return;
            }

            HttpServerResponse response = request.response()
                    .setChunked(true)
                    .putHeader("Content-Type", "text/event-stream");
            liveStreams.add(response);
            response.write("data: /messages/fake\r\nevent: endpoint\r\n\r\n");
        }).listen(0));

        IdeaMcpConfig config = new IdeaMcpConfig(
                URI.create("http://127.0.0.1:" + ideServer.actualPort() + "/sse"),
                Map.of(),
                1000,
                CONFIGURED_RESPONSE_TIMEOUT_MILLIS
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
    void aToolCallDeclaringALongerDeadlineOutlivesTheConfiguredTimeout() throws Exception {
        String arguments = "{\"command\":\"sleep 75\",\"timeout\":" + DECLARED_TOOL_TIMEOUT_MILLIS + "}";

        long startNanos = System.nanoTime();
        Map<String, Object> result = await(
                provider.callTool("execute_terminal_command", JSON.readTree(arguments)));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertFalse((Boolean) result.get("isError"),
                "the declared deadline has to be honoured: " + result);
        assertEquals("command finished", firstText(result));
        assertTrue(elapsedMillis > CONFIGURED_RESPONSE_TIMEOUT_MILLIS,
                "the answer must have arrived after the configured timeout, otherwise this test "
                        + "proves nothing; took " + elapsedMillis + "ms");
    }

    @Test
    void theSameCallWithoutADeclaredDeadlineStillHitsTheConfiguredTimeout() throws Exception {
        Map<String, Object> result = await(
                provider.callTool("execute_terminal_command", JSON.readTree("{\"command\":\"sleep 75\"}")));

        // Control: without the argument there is nothing to extend, so the configured timeout stands.
        // This is what keeps the test above honest.
        assertTrue((Boolean) result.get("isError"), "expected the configured timeout to apply: " + result);
        assertTrue(firstText(result).contains("Timeout waiting for IDEA SSE response"),
                "expected an SSE timeout, got: " + firstText(result));
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
