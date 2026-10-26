package com.aresstack.pyloros.upstream.idea;

import io.vertx.core.json.JsonObject;

/**
 * How much upstream-supplied text may enter a log line or a caller-facing error message.
 *
 * <p>One home for that decision, because the two answer paths kept drifting apart: the HTTP path
 * bounded response bodies while the SSE path still rendered whole JSON-RPC error objects. Tool
 * results run to thousands of lines, and a JSON-RPC error carries an optional, unbounded
 * {@code data} member — either can hold file contents or credentials.
 */
final class UpstreamText {

    /** Bound for a response body. */
    private static final int BODY_LIMIT = 512;

    /** Bound for a rendered JSON-RPC error. Shorter: code and message are all that is wanted. */
    private static final int ERROR_LIMIT = 256;

    private UpstreamText() {
    }

    /** @return {@code body} bounded to a loggable length, with the truncation made visible */
    static String excerpt(String body) {
        return truncate(body, BODY_LIMIT);
    }

    /**
     * @return a bounded rendering of a JSON-RPC {@code error} member
     *
     * <p>Only {@code code} and {@code message}. The {@code data} member is deliberately dropped
     * rather than truncated: it is free-form, and nothing downstream acts on it.
     */
    static String describeError(Object error) {
        if (!(error instanceof JsonObject errorObject)) {
            return truncate(String.valueOf(error), ERROR_LIMIT);
        }
        return truncate("code=" + errorObject.getValue("code")
                + " message=" + errorObject.getString("message", ""), ERROR_LIMIT);
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "… (" + text.length() + " chars total)";
    }
}
