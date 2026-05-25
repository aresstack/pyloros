package com.aresstack.pyloros.langchain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Maps LangChain results and failures to MCP {@code ToolResult} shaped maps.
 *
 * <p>Every returned map has the structure expected by Pyloros tool providers:
 * <pre>
 * {
 *   "content": [{"type": "text", "text": "..."}],
 *   "isError": true|false,
 *   "structuredContent": {
 *     "stopReason": "&lt;wire name&gt;",
 *     ... reason specific fields ...
 *   }
 * }
 * </pre>
 *
 * <p>Both the human-readable {@code text} and the {@code structuredContent}
 * error detail are truncated, and a set of common secret-looking substrings
 * (Bearer tokens, {@code password=...}, {@code apiKey=...}, ...) is masked so
 * that the result is safe to forward to clients.
 *
 * <p>This class intentionally has no dependency on LangChain4j or the MCP
 * transport so that it can be unit-tested in isolation (per R5-10).
 */
public final class LangChainToolResultMapper {

    /** Default maximum length of the human-readable {@code text} content. */
    public static final int DEFAULT_MAX_RESULT_CHARS = 12_000;

    /** Maximum length of any individual technical detail string (error messages, partial results, ...). */
    public static final int DEFAULT_MAX_DETAIL_CHARS = 1_000;

    private static final String TRUNCATION_MARKER = "... [truncated]";
    private static final String REDACTED = "***";

    /** Patterns that look like secrets and must be masked in user-facing output. */
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._\\-+/=]+"),
            Pattern.compile("(?i)(password\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(passwd\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(secret\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(api[_\\-]?key\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(token\\s*[:=]\\s*)\\S+"),
            Pattern.compile("(?i)(authorization\\s*[:=]\\s*)\\S+")
    );

    private final int maxResultChars;
    private final int maxDetailChars;

    public LangChainToolResultMapper() {
        this(DEFAULT_MAX_RESULT_CHARS, DEFAULT_MAX_DETAIL_CHARS);
    }

    public LangChainToolResultMapper(int maxResultChars, int maxDetailChars) {
        if (maxResultChars <= 0) {
            throw new IllegalArgumentException("maxResultChars must be > 0");
        }
        if (maxDetailChars <= 0) {
            throw new IllegalArgumentException("maxDetailChars must be > 0");
        }
        this.maxResultChars = maxResultChars;
        this.maxDetailChars = maxDetailChars;
    }

    public int maxResultChars() {
        return maxResultChars;
    }

    public int maxDetailChars() {
        return maxDetailChars;
    }

    // --- Successful outcomes ----------------------------------------------------

    /** Maps a successful LLM run that produced a final answer. */
    public Map<String, Object> completed(String answer, List<String> toolsUsed) {
        String text = sanitizeAndTruncate(answer == null ? "" : answer, maxResultChars);
        Map<String, Object> structured = baseStructured(LangChainStopReason.COMPLETED);
        structured.put("result", text);
        if (toolsUsed != null && !toolsUsed.isEmpty()) {
            structured.put("toolsUsed", List.copyOf(toolsUsed));
        }
        return toolResult(text, false, structured);
    }

    // --- Soft stops -------------------------------------------------------------

    /** Maps a run that hit the {@code maxToolCalls} limit. The partial answer (if any) is returned to the user. */
    public Map<String, Object> maxToolCalls(String partialAnswer, int limit) {
        String partial = partialAnswer == null ? "" : sanitizeAndTruncate(partialAnswer, maxResultChars);
        String text = partial.isEmpty()
                ? "Maximum number of tool calls (" + limit + ") reached before the model produced a final answer."
                : partial;
        Map<String, Object> structured = baseStructured(LangChainStopReason.MAX_TOOL_CALLS);
        structured.put("limit", limit);
        if (!partial.isEmpty()) {
            structured.put("partialResult", partial);
        }
        // isError=false because the user still gets the partial output (see 23.12 mapping).
        return toolResult(text, false, structured);
    }

    // --- Failures (always isError=true) -----------------------------------------

    public Map<String, Object> timeout(LangChainTimeoutException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return errorFor(exception, Map.of());
    }

    public Map<String, Object> modelError(ModelExecutionException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return errorFor(exception, Map.of());
    }

    public Map<String, Object> toolError(ToolExecutionException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return errorFor(exception, Map.of("toolName", exception.toolName()));
    }

    public Map<String, Object> policyDenied(PolicyDeniedException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return errorFor(exception, Map.of("toolName", exception.toolName()));
    }

    public Map<String, Object> cancelled(CancelledException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return errorFor(exception, Map.of());
    }

    /** Dispatch a {@link LangChainException} to the matching mapping method. */
    public Map<String, Object> fromException(LangChainException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        return switch (exception.stopReason()) {
            case TIMEOUT -> timeout((LangChainTimeoutException) exception);
            case MODEL_ERROR -> modelError((ModelExecutionException) exception);
            case TOOL_ERROR -> toolError((ToolExecutionException) exception);
            case POLICY_DENIED -> policyDenied((PolicyDeniedException) exception);
            case CANCELLED -> cancelled((CancelledException) exception);
            case MAX_TOOL_CALLS -> maxToolCalls(null, ((MaxToolCallsExceededException) exception).limit());
            case COMPLETED -> throw new IllegalArgumentException(
                    "COMPLETED is not an error stop reason; use completed(...) instead");
        };
    }

    /** Catch-all mapper for unexpected runtime failures. Returns a model_error result. */
    public Map<String, Object> unexpectedError(Throwable cause) {
        Objects.requireNonNull(cause, "cause must not be null");
        String safeMessage = safeMessage(cause);
        Map<String, Object> structured = baseStructured(LangChainStopReason.MODEL_ERROR);
        structured.put("error", Map.of(
                "code", LangChainStopReason.MODEL_ERROR.wireName(),
                "message", safeMessage));
        return toolResult(safeMessage, true, structured);
    }

    // --- helpers ---------------------------------------------------------------

    private Map<String, Object> errorFor(LangChainException exception, Map<String, Object> extraErrorFields) {
        String safeMessage = safeMessage(exception);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", exception.stopReason().wireName());
        error.put("message", safeMessage);
        error.putAll(extraErrorFields);

        Map<String, Object> structured = baseStructured(exception.stopReason());
        structured.put("error", error);

        return toolResult(safeMessage, true, structured);
    }

    private Map<String, Object> baseStructured(LangChainStopReason stopReason) {
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("stopReason", stopReason.wireName());
        return structured;
    }

    private Map<String, Object> toolResult(String text, boolean isError, Map<String, Object> structuredContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of("type", "text", "text", text)));
        result.put("isError", isError);
        result.put("structuredContent", structuredContent);
        return result;
    }

    /**
     * Returns a short, sanitized message for the given throwable.
     * <p>The throwable's stack trace is intentionally <strong>not</strong>
     * included so technical dumps cannot leak to clients.
     */
    private String safeMessage(Throwable cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        return sanitizeAndTruncate(message, maxDetailChars);
    }

    private static String sanitizeAndTruncate(String value, int maxChars) {
        String sanitized = maskSecrets(value);
        if (sanitized.length() <= maxChars) {
            return sanitized;
        }
        int keep = Math.max(0, maxChars - TRUNCATION_MARKER.length());
        return sanitized.substring(0, keep) + TRUNCATION_MARKER;
    }

    private static String maskSecrets(String value) {
        String result = value;
        for (Pattern pattern : SECRET_PATTERNS) {
            result = pattern.matcher(result).replaceAll("$1" + REDACTED);
        }
        return result;
    }
}
