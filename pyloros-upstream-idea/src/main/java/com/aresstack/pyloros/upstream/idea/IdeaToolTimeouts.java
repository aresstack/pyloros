package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Derives how long Pyloros waits for a {@code tools/call} answer from the call's own arguments.
 *
 * <p>The configured upstream timeout is a transport default: it bounds how long to wait for an IDE
 * that is expected to answer promptly. It is the wrong bound for a tool that was explicitly asked to
 * run longer. A 75 s terminal command sent with {@code timeout: 100000} was still abandoned after
 * the configured 60 s, so the caller got a Pyloros transport timeout instead of the output it had
 * paid for — while the command itself kept running in the IDE.
 *
 * <p>The IntelliJ MCP plugin does not use a single convention, so both spellings are read:
 * {@code execute_terminal_command} declares {@code timeout} in milliseconds,
 * {@code run_gradle_task} declares {@code timeoutSeconds} in seconds.
 *
 * <p>This cannot cover a tool whose deadline is <em>implicit</em>: {@code run_gradle_task} defaults
 * to 600 s when the argument is omitted, and no gateway can know that. For those calls the
 * configured timeout still applies, which is why it also became configurable per upstream in
 * {@code mcp.json} rather than staying a compiled-in 60 s.
 */
final class IdeaToolTimeouts {

    private static final Logger log = LoggerFactory.getLogger(IdeaToolTimeouts.class);

    /**
     * Headroom added on top of the tool's own deadline.
     *
     * <p>The IDE enforces that deadline itself and then returns the partial output as a normal
     * result ("Output with interruption notice if timed out"). Pyloros has to still be waiting in
     * order to pass that on; waiting exactly as long as the tool would turn a usable answer into a
     * transport timeout decided by a race.
     */
    static final long GRACE_MILLIS = 15_000L;

    /** Ceiling on a caller-supplied deadline, so a bad argument cannot pin a promise for days. */
    static final long MAX_TIMEOUT_MILLIS = 3_600_000L;

    private IdeaToolTimeouts() {
    }

    /**
     * @return the response timeout to use for this call; never below {@code configuredTimeoutMillis}
     */
    static long resolve(String toolName, JsonObject arguments, long configuredTimeoutMillis) {
        Long declared = declaredDeadlineMillis(toolName, arguments);
        if (declared == null) {
            return configuredTimeoutMillis;
        }

        // Clamp before adding the headroom: the addition would otherwise overflow on an absurd value.
        long wanted = Math.min(declared, MAX_TIMEOUT_MILLIS - GRACE_MILLIS) + GRACE_MILLIS;
        if (wanted <= configuredTimeoutMillis) {
            return configuredTimeoutMillis;
        }

        if (declared + GRACE_MILLIS > MAX_TIMEOUT_MILLIS) {
            log.warn("Tool {} declared a {} ms deadline, clamping the IDEA response timeout to {} ms",
                    toolName, declared, wanted);
        } else {
            log.info("Tool {} declared a {} ms deadline, extending the IDEA response timeout from {} ms to {} ms",
                    toolName, declared, configuredTimeoutMillis, wanted);
        }
        return wanted;
    }

    private static Long declaredDeadlineMillis(String toolName, JsonObject arguments) {
        if (arguments == null) {
            return null;
        }

        Long millis = positiveLong(toolName, arguments, "timeout");
        if (millis != null) {
            return millis;
        }

        Long seconds = positiveLong(toolName, arguments, "timeoutSeconds");
        if (seconds == null) {
            return null;
        }
        // Convert with the ceiling applied first, so the multiplication cannot overflow.
        return seconds > MAX_TIMEOUT_MILLIS / 1000L ? MAX_TIMEOUT_MILLIS : seconds * 1000L;
    }

    private static Long positiveLong(String toolName, JsonObject arguments, String key) {
        if (!arguments.containsKey(key)) {
            return null;
        }

        Object value = arguments.getValue(key);
        if (value instanceof Number number) {
            long millis = number.longValue();
            if (millis > 0) {
                return millis;
            }
        }

        // Falling back silently here is how the original 60 s limit stayed invisible for so long.
        log.debug("Tool {} carries an unusable '{}' argument ({}), using the configured timeout",
                toolName, key, value);
        return null;
    }
}
