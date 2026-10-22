package com.aresstack.pyloros.upstream.mcp;

/**
 * An MCP upstream answered with an HTTP error status.
 *
 * <p>The status is kept separate from the message so callers can react to it — notably a
 * {@code 404}, which MCP Streamable HTTP defines as "this session is gone, start a new one".
 */
public final class McpUpstreamHttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    public McpUpstreamHttpException(int statusCode, String method, String responseBody) {
        super(buildMessage(statusCode, method, responseBody));
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    /** True when the upstream reports the MCP session as unknown and a new one must be created. */
    public boolean isSessionGone() {
        return statusCode == 404;
    }

    private static String buildMessage(int statusCode, String method, String responseBody) {
        StringBuilder message = new StringBuilder("MCP HTTP error ")
                .append(statusCode)
                .append(" for method=")
                .append(method == null ? "unknown" : method);
        if (responseBody != null && !responseBody.isBlank()) {
            String trimmed = responseBody.strip();
            if (trimmed.length() > 200) {
                trimmed = trimmed.substring(0, 200) + "…";
            }
            message.append(": ").append(trimmed);
        }
        return message.toString();
    }
}
