package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lifecycle tests for {@link IdeaSseSession} against a fake IDEA SSE endpoint.
 *
 * <p>These reproduce the three failure modes of the previous implementation: a single drop
 * scheduling two reconnects (because both the exception and the end handler fired), overlapping
 * connection attempts, and a late callback from an abandoned connection wiping the endpoint that a
 * healthy connection had just discovered.
 */
class IdeaSseSessionLifecycleTest {

    private static final int RECONNECT_DELAY_MILLIS = 1000;

    private Vertx vertx;
    private HttpServer server;
    private IdeaSseSession session;

    private final AtomicInteger connectionCount = new AtomicInteger();
    private final List<HttpConnection> liveConnections = new CopyOnWriteArrayList<>();
    private final List<HttpServerResponse> liveStreams = new CopyOnWriteArrayList<>();
    private final AtomicInteger attemptsToReject = new AtomicInteger();
    private volatile boolean dropImmediately;

    @BeforeEach
    void setUp() throws Exception {
        vertx = Vertx.vertx();

        server = await(vertx.createHttpServer().requestHandler(request -> {
            connectionCount.incrementAndGet();

            int reject = attemptsToReject.getAndUpdate(remaining -> remaining > 0 ? remaining - 1 : 0);
            if (reject > 0) {
                request.response().setStatusCode(503).end("rejected");
                return;
            }

            liveConnections.add(request.connection());

            HttpServerResponse response = request.response()
                    .setChunked(true)
                    .putHeader("Content-Type", "text/event-stream");
            liveStreams.add(response);
            // CRLF and data-before-event, as the real IntelliJ MCP plugin emits them.
            response.write("data: /messages/fake\r\nevent: endpoint\r\n\r\n");

            if (dropImmediately) {
                // Kill the whole connection so the client sees a hard drop, not a clean end.
                request.connection().close();
            }
        }).listen(0));
    }

    /** Kills every live connection: an abrupt drop, triggered by the test rather than waited for. */
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

    @AfterEach
    void tearDown() throws Exception {
        if (session != null) {
            await(session.stop());
        }
        if (server != null) {
            await(server.close());
        }
        if (vertx != null) {
            await(vertx.close());
        }
    }

    private IdeaSseSession newSession() {
        IdeaMcpConfig config = new IdeaMcpConfig(
                URI.create("http://127.0.0.1:" + server.actualPort() + "/sse"),
                Map.of(),
                RECONNECT_DELAY_MILLIS,
                2000
        );
        return new IdeaSseSession(vertx, config);
    }

    @Test
    void discoversEndpointOnConnect() throws Exception {
        session = newSession();
        session.start();

        waitUntil(() -> session.isReady());
        assertEquals("/messages/fake", session.getEndpoint());
        assertEquals(1, connectionCount.get());
    }

    /** Scenario 1: the server kills a live connection; the session notices and re-establishes it. */
    @Test
    void abruptServerSideCloseIsDetectedAndConnectionIsReestablished() throws Exception {
        session = newSession();
        session.start();
        waitUntil(() -> session.isReady());
        long firstAttempt = session.activeAttempt();

        dropLiveConnections();

        // Detected...
        waitUntil(() -> !session.isReady() || session.activeAttempt() != firstAttempt);
        // ...and recovered under a new attempt.
        waitUntil(() -> session.isReady(), 10_000);

        assertEquals("/messages/fake", session.getEndpoint());
        assertNotEquals(firstAttempt, session.activeAttempt());
        assertEquals(2, session.attemptCount(), "exactly one reconnect for one drop");
    }

    /** Scenario 4: a rejected connection attempt is retried and then succeeds. */
    @Test
    void rejectedAttemptIsRetriedSuccessfully() throws Exception {
        attemptsToReject.set(1);

        session = newSession();
        session.start();

        waitUntil(() -> session.isReady(), 10_000);
        assertEquals("/messages/fake", session.getEndpoint());
        assertEquals(2, session.attemptCount(), "one rejected attempt plus one successful retry");
    }

    /**
     * Scenario 6: a quiet stream stays valid. An SSE stream may legitimately carry no events for a
     * long time, so silence must never be treated as a failure.
     */
    @Test
    void quietStreamWithoutEventsStaysValid() throws Exception {
        session = newSession();
        session.start();
        waitUntil(() -> session.isReady());
        long attempt = session.activeAttempt();

        // No events are sent for well beyond the reconnect delay.
        Thread.sleep(RECONNECT_DELAY_MILLIS * 3L);

        assertTrue(session.isReady(), "a quiet stream must stay valid");
        assertEquals("/messages/fake", session.getEndpoint());
        assertEquals(attempt, session.activeAttempt(), "no reconnect may be triggered by silence");
        assertEquals(1, session.attemptCount(), "silence must not count as a failure");
    }

    @Test
    void duplicateDropEventsScheduleOnlyOneReconnect() throws Exception {
        session = newSession();
        session.start();
        waitUntil(() -> session.isReady());

        long attempt = session.activeAttempt();

        // A real drop delivers both an exception and an end event for the same attempt. Replaying
        // both — plus a third for good measure — must still produce exactly one further attempt.
        session.handleConnectionLost(attempt, "response exception", new RuntimeException("boom"));
        session.handleConnectionLost(attempt, "response ended", null);
        session.handleConnectionLost(attempt, "response ended", null);

        waitUntil(() -> session.attemptCount() == 2);
        // Give any surplus reconnect timer time to fire before asserting the count is stable.
        Thread.sleep(RECONNECT_DELAY_MILLIS + 500);
        assertEquals(2, session.attemptCount(), "a single drop must trigger exactly one reconnect");
        assertTrue(connectionCount.get() <= 2, "no extra connections: " + connectionCount.get());
    }

    @Test
    void lateCallbackFromSupersededAttemptDoesNotClearFreshEndpoint() throws Exception {
        session = newSession();
        session.start();
        waitUntil(() -> session.isReady());

        long staleAttempt = session.activeAttempt();

        // Force one reconnect so the session is healthy again under a newer attempt.
        session.handleConnectionLost(staleAttempt, "response ended", null);
        waitUntil(() -> session.isReady() && session.activeAttempt() != staleAttempt);

        long freshAttempt = session.activeAttempt();
        assertNotEquals(staleAttempt, freshAttempt);

        // The abandoned connection now reports in, far too late.
        session.handleConnectionLost(staleAttempt, "response exception", new RuntimeException("late"));

        assertTrue(session.isReady(), "a late callback must not clear the current endpoint");
        assertEquals("/messages/fake", session.getEndpoint());
        assertEquals(freshAttempt, session.activeAttempt(), "the live attempt must stay owner");
    }

    @Test
    void repeatedServerSideDropsDoNotMultiplyConnections() throws Exception {
        dropImmediately = true;
        session = newSession();
        session.start();

        // Three reconnect cycles. With the old unguarded reconnect the attempt count doubled per
        // cycle (both handlers fired), so this would run away instead of advancing one per cycle.
        waitUntil(() -> session.attemptCount() >= 3, 10_000);

        long attempts = session.attemptCount();
        Thread.sleep(RECONNECT_DELAY_MILLIS + 500);
        long afterOneMoreDelay = session.attemptCount();

        assertTrue(afterOneMoreDelay - attempts <= 2,
                "attempts must grow linearly, grew by " + (afterOneMoreDelay - attempts));
    }

    @Test
    void stopCancelsPendingReconnect() throws Exception {
        session = newSession();
        session.start();
        waitUntil(() -> session.isReady());

        session.handleConnectionLost(session.activeAttempt(), "response ended", null);
        long attemptsAtStop = session.attemptCount();

        await(session.stop());

        // The reconnect timer was armed when stop() was called; it must not start a new attempt.
        Thread.sleep(RECONNECT_DELAY_MILLIS + 500);
        assertEquals(attemptsAtStop, session.attemptCount(), "stop() must cancel the reconnect timer");

        session = null;
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
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
