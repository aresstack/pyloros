package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The two argument conventions the IntelliJ MCP plugin actually uses, and the edges around them.
 *
 * <p>{@code execute_terminal_command} declares {@code timeout} in milliseconds while
 * {@code run_gradle_task} declares {@code timeoutSeconds} in seconds. Reading only one of them would
 * leave the other silently capped at the transport default.
 */
class IdeaToolTimeoutsTest {

    private static final long CONFIGURED = 60_000L;

    @Test
    void millisecondTimeoutArgumentExtendsTheConfiguredTimeout() {
        // The reproduced failure: a 75 s command sent with timeout: 100000 was cut at 60 s.
        long resolved = IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", 100_000), CONFIGURED);

        assertEquals(100_000L + IdeaToolTimeouts.GRACE_MILLIS, resolved);
    }

    @Test
    void secondsTimeoutArgumentIsConvertedNotTakenAsMilliseconds() {
        // run_gradle_task's 600 s default read as milliseconds would be 600 ms — shorter than the
        // configured timeout, so the bug would have looked like "no change" instead of a unit error.
        long resolved = IdeaToolTimeouts.resolve(
                "run_gradle_task", new JsonObject().put("timeoutSeconds", 600), CONFIGURED);

        assertEquals(600_000L + IdeaToolTimeouts.GRACE_MILLIS, resolved);
    }

    @Test
    void absentTimeoutArgumentKeepsTheConfiguredTimeout() {
        assertEquals(CONFIGURED, IdeaToolTimeouts.resolve("get_vcs_branch", new JsonObject(), CONFIGURED));
        assertEquals(CONFIGURED, IdeaToolTimeouts.resolve("get_vcs_branch", null, CONFIGURED));
    }

    @Test
    void shortToolDeadlineNeverShortensTheConfiguredTimeout() {
        // A 5 s terminal command must not make the transport give up after 5 s; the configured
        // timeout is a floor, not a target.
        long resolved = IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", 5_000), CONFIGURED);

        assertEquals(CONFIGURED, resolved);
    }

    @Test
    void absurdDeadlinesAreClampedRatherThanOverflowing() {
        assertEquals(IdeaToolTimeouts.MAX_TIMEOUT_MILLIS, IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", Long.MAX_VALUE), CONFIGURED));
        assertEquals(IdeaToolTimeouts.MAX_TIMEOUT_MILLIS, IdeaToolTimeouts.resolve(
                "run_gradle_task", new JsonObject().put("timeoutSeconds", Long.MAX_VALUE), CONFIGURED));
    }

    @Test
    void unusableTimeoutArgumentsFallBackToTheConfiguredTimeout() {
        assertEquals(CONFIGURED, IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", "100000"), CONFIGURED));
        assertEquals(CONFIGURED, IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", 0), CONFIGURED));
        assertEquals(CONFIGURED, IdeaToolTimeouts.resolve(
                "execute_terminal_command", new JsonObject().put("timeout", -1), CONFIGURED));
    }
}
