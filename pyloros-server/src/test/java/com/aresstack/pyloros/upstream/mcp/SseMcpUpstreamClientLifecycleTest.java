package com.aresstack.pyloros.upstream.mcp;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connection lifecycle tests for {@link SseMcpUpstreamClient} against {@link SseTestServer}.
 *
 * <p>This client had the same defects that were fixed in {@code IdeaSseSession}: an unguarded
 * {@code scheduleReconnect()} reached from both the response exception handler and the end handler,
 * and an unconditional clearing of the discovered endpoint that let a late callback from an
 * abandoned connection wipe a healthy one.
 *
 * <p>No sleeps are used to *produce* a fault — faults are triggered by the test. Waiting is only
 * used to observe a state transition, bounded by a deadline.
 */
class SseMcpUpstreamClientLifecycleTest {

    private static final int RECONNECT_DELAY_MILLIS = 1000;

    private Vertx vertx;
    private SseTestServer upstream;
    private SseMcpUpstreamClient client;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();
        upstream = SseTestServer.start(vertx);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            await(client.stop());
        }
        if (upstream != null) {
            upstream.close();
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    private void startClient() throws Exception {
        client = new SseMcpUpstreamClient(vertx, upstream.config("test", RECONNECT_DELAY_MILLIS, 2000));
        await(client.start());
        waitUntil(() -> client.discoveredEndpoint() != null);
    }

    /** Scenario 1: the server kills a live connection; the client notices and re-establishes it. */
    @Test
    void abruptServerSideCloseIsDetectedAndConnectionIsReestablished() throws Exception {
        startClient();
        assertEquals(SseTestServer.ENDPOINT_PATH, client.discoveredEndpoint());
        long firstAttempt = client.activeAttempt();

        upstream.dropLiveConnections();

        // Detected: the endpoint is invalidated...
        waitUntil(() -> client.discoveredEndpoint() == null || client.activeAttempt() != firstAttempt);
        // ...and recovered under a new attempt.
        waitUntil(() -> client.discoveredEndpoint() != null, 10_000);

        assertEquals(SseTestServer.ENDPOINT_PATH, client.discoveredEndpoint());
        assertNotEquals(firstAttempt, client.activeAttempt());
        assertEquals(2, client.attemptCount(), "exactly one reconnect for one drop");
    }

    /** Scenario 2: several drop events for the same connection cause only one reconnect. */
    @Test
    void duplicateDropEventsScheduleOnlyOneReconnect() throws Exception {
        startClient();
        long attempt = client.activeAttempt();

        // A real drop delivers both an exception and an end event for the same attempt.
        client.handleConnectionLost(attempt, "response-exception", new RuntimeException("boom"));
        client.handleConnectionLost(attempt, "response-ended", null);
        client.handleConnectionLost(attempt, "response-exception", new RuntimeException("again"));

        waitUntil(() -> client.attemptCount() == 2);
        // Allow any surplus timer a full delay window to misfire before asserting stability.
        Thread.sleep(RECONNECT_DELAY_MILLIS + 500);
        assertEquals(2, client.attemptCount(), "a single drop must trigger exactly one reconnect");
    }

    /** Scenario 3: a late callback from a superseded attempt must not clear a fresh endpoint. */
    @Test
    void lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint() throws Exception {
        startClient();
        long staleAttempt = client.activeAttempt();

        client.handleConnectionLost(staleAttempt, "response-ended", null);
        waitUntil(() -> client.discoveredEndpoint() != null && client.activeAttempt() != staleAttempt, 10_000);

        long freshAttempt = client.activeAttempt();
        assertNotEquals(staleAttempt, freshAttempt);

        // The abandoned connection reports in far too late.
        client.handleConnectionLost(staleAttempt, "response-exception", new RuntimeException("late"));

        assertNotNull(client.discoveredEndpoint(), "a late callback must not clear the current endpoint");
        assertEquals(SseTestServer.ENDPOINT_PATH, client.discoveredEndpoint());
        assertEquals(freshAttempt, client.activeAttempt(), "the live attempt must remain the owner");
    }

    /** Scenario 4: a rejected connection attempt is retried and then succeeds. */
    @Test
    void rejectedAttemptIsRetriedSuccessfully() throws Exception {
        upstream.rejectNextAttempts(1, 503);

        client = new SseMcpUpstreamClient(vertx, upstream.config("test", RECONNECT_DELAY_MILLIS, 2000));
        await(client.start());

        waitUntil(() -> client.discoveredEndpoint() != null, 10_000);
        assertEquals(SseTestServer.ENDPOINT_PATH, client.discoveredEndpoint());
        assertEquals(2, client.attemptCount(), "one rejected attempt plus one successful retry");
    }

    /** Scenario 5: stopping during a pending reconnect must not start another connection. */
    @Test
    void stopDuringPendingReconnectStartsNoNewConnection() throws Exception {
        startClient();

        client.handleConnectionLost(client.activeAttempt(), "response-ended", null);
        long attemptsAtStop = client.attemptCount();
        int connectionsAtStop = upstream.sseConnectionCount();

        await(client.stop());

        // The reconnect timer was armed when stop() was called; it must be cancelled.
        Thread.sleep(RECONNECT_DELAY_MILLIS + 500);
        assertEquals(attemptsAtStop, client.attemptCount(), "stop() must cancel the pending reconnect");
        assertEquals(connectionsAtStop, upstream.sseConnectionCount(), "no connection after stop()");

        client = null;
    }

    /**
     * Scenario 6: a quiet stream stays valid. An SSE stream may legitimately carry no events for a
     * long time, so the absence of data must never be treated as a failure.
     */
    @Test
    void quietStreamWithoutEventsStaysValid() throws Exception {
        startClient();
        long attempt = client.activeAttempt();
        String endpoint = client.discoveredEndpoint();

        // No events are sent for well beyond the reconnect delay.
        Thread.sleep(RECONNECT_DELAY_MILLIS * 3L);

        assertEquals(endpoint, client.discoveredEndpoint(), "a quiet stream must stay valid");
        assertEquals(attempt, client.activeAttempt(), "no reconnect may be triggered by silence");
        assertEquals(1, client.attemptCount(), "silence must not count as a failure");
        assertEquals(1, upstream.liveStreamCount(), "the original stream must still be open");
    }

    /** A pending request is failed when the connection drops, rather than hanging. */
    @Test
    void pendingRequestIsFailedOnDisconnect() throws Exception {
        startClient();

        // The test server answers POSTs with 202 and never pushes a matching SSE message, so this
        // request stays pending until the connection drops.
        Future<java.util.List<java.util.Map<String, Object>>> pending = client.listTools();
        upstream.dropLiveConnections();

        try {
            await(pending);
            throw new AssertionError("pending request should have failed on disconnect");
        } catch (java.util.concurrent.ExecutionException expected) {
            assertTrue(expected.getMessage().contains("disconnected"),
                    "failure should name the disconnect: " + expected.getMessage());
        }
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
}
