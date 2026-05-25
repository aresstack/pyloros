package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChainToolResultMapperTest {

    private final LangChainToolResultMapper mapper = new LangChainToolResultMapper();

    @Test
    void completedProducesStopReasonCompleted() {
        Map<String, Object> result = mapper.completed("Hello world", List.of("provider/tool-a"));

        assertEquals(Boolean.FALSE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("completed", structured.get("stopReason"));
        assertEquals("Hello world", structured.get("result"));
        assertEquals(List.of("provider/tool-a"), structured.get("toolsUsed"));
        assertEquals("Hello world", firstTextContent(result));
    }

    @Test
    void completedWithoutToolsOmitsToolsUsed() {
        Map<String, Object> result = mapper.completed("ok", List.of());
        Map<String, Object> structured = structured(result);
        assertFalse(structured.containsKey("toolsUsed"));
    }

    @Test
    void maxToolCallsIsNotMarkedAsError() {
        Map<String, Object> result = mapper.maxToolCalls("partial answer", 8);

        assertEquals(Boolean.FALSE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("max_tool_calls", structured.get("stopReason"));
        assertEquals(8, structured.get("limit"));
        assertEquals("partial answer", structured.get("partialResult"));
        assertEquals("partial answer", firstTextContent(result));
    }

    @Test
    void maxToolCallsWithoutPartialAnswerYieldsDescriptiveText() {
        Map<String, Object> result = mapper.maxToolCalls(null, 3);
        Map<String, Object> structured = structured(result);
        assertFalse(structured.containsKey("partialResult"));
        assertTrue(((String) firstTextContent(result)).contains("(3)"));
    }

    @Test
    void timeoutMapsToTimeoutStopReason() {
        Map<String, Object> result = mapper.timeout(new LangChainTimeoutException("model took too long"));

        assertEquals(Boolean.TRUE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("timeout", structured.get("stopReason"));
        Map<?, ?> error = (Map<?, ?>) structured.get("error");
        assertEquals("timeout", error.get("code"));
        assertEquals("model took too long", error.get("message"));
    }

    @Test
    void modelErrorIncludesShortMessageAndNoStackTrace() {
        ModelExecutionException ex = new ModelExecutionException(
                "ollama unreachable", new RuntimeException("connection refused"));
        Map<String, Object> result = mapper.modelError(ex);

        assertEquals(Boolean.TRUE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("model_error", structured.get("stopReason"));

        // Text content must not contain a stack trace
        String text = (String) firstTextContent(result);
        assertEquals("ollama unreachable", text);
        assertFalse(text.contains("at java."));
        assertFalse(text.contains("Caused by"));
    }

    @Test
    void toolErrorIncludesToolName() {
        ToolExecutionException ex = new ToolExecutionException("github/list-issues", "boom");
        Map<String, Object> result = mapper.toolError(ex);

        Map<String, Object> structured = structured(result);
        assertEquals("tool_error", structured.get("stopReason"));
        Map<?, ?> error = (Map<?, ?>) structured.get("error");
        assertEquals("github/list-issues", error.get("toolName"));
        assertEquals("boom", error.get("message"));
    }

    @Test
    void policyDeniedMapsToPolicyDeniedStopReason() {
        PolicyDeniedException ex = new PolicyDeniedException("intellij/write-file", "write tools require approval");
        Map<String, Object> result = mapper.policyDenied(ex);

        assertEquals(Boolean.TRUE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("policy_denied", structured.get("stopReason"));
        Map<?, ?> error = (Map<?, ?>) structured.get("error");
        assertEquals("intellij/write-file", error.get("toolName"));
        assertEquals("write tools require approval", error.get("message"));
    }

    @Test
    void cancelledMapsToCancelledStopReason() {
        Map<String, Object> result = mapper.cancelled(new CancelledException("client cancelled"));

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("cancelled", structured(result).get("stopReason"));
    }

    @Test
    void fromExceptionDispatchesByStopReason() {
        assertEquals("model_error",
                structured(mapper.fromException(new ModelExecutionException("x"))).get("stopReason"));
        assertEquals("tool_error",
                structured(mapper.fromException(new ToolExecutionException("t", "y"))).get("stopReason"));
        assertEquals("timeout",
                structured(mapper.fromException(new LangChainTimeoutException("z"))).get("stopReason"));
        assertEquals("policy_denied",
                structured(mapper.fromException(new PolicyDeniedException("t", "no"))).get("stopReason"));
        assertEquals("cancelled",
                structured(mapper.fromException(new CancelledException("bye"))).get("stopReason"));
        assertEquals("max_tool_calls",
                structured(mapper.fromException(new MaxToolCallsExceededException(5, "limit"))).get("stopReason"));
    }

    @Test
    void unexpectedErrorIsMappedToModelError() {
        Map<String, Object> result = mapper.unexpectedError(new IllegalStateException("unexpected"));

        assertEquals(Boolean.TRUE, result.get("isError"));
        Map<String, Object> structured = structured(result);
        assertEquals("model_error", structured.get("stopReason"));
        Map<?, ?> error = (Map<?, ?>) structured.get("error");
        assertEquals("unexpected", error.get("message"));
    }

    @Test
    void unexpectedErrorWithoutMessageFallsBackToClassName() {
        Map<String, Object> result = mapper.unexpectedError(new NullPointerException());
        Map<?, ?> error = (Map<?, ?>) structured(result).get("error");
        assertEquals("NullPointerException", error.get("message"));
    }

    @Test
    void veryLargeAnswerIsTruncated() {
        LangChainToolResultMapper smallMapper = new LangChainToolResultMapper(64, 32);
        String huge = "x".repeat(10_000);

        Map<String, Object> result = smallMapper.completed(huge, List.of());

        String text = (String) firstTextContent(result);
        assertTrue(text.length() <= 64, "text length=" + text.length());
        assertTrue(text.endsWith("... [truncated]"));
        assertEquals(text, structured(result).get("result"));
    }

    @Test
    void veryLargeErrorMessageIsTruncated() {
        LangChainToolResultMapper smallMapper = new LangChainToolResultMapper(2_000, 50);
        String huge = "e".repeat(5_000);

        Map<String, Object> result = smallMapper.modelError(new ModelExecutionException(huge));

        Map<?, ?> error = (Map<?, ?>) structured(result).get("error");
        String message = (String) error.get("message");
        assertTrue(message.length() <= 50, "message length=" + message.length());
        assertTrue(message.endsWith("... [truncated]"));
    }

    @Test
    void secretsAreMasked() {
        String leaky = "Authorization: Bearer abc123secrettoken and password=hunter2 and apiKey=XYZ-987";
        Map<String, Object> result = mapper.completed(leaky, List.of());

        String text = (String) firstTextContent(result);
        assertFalse(text.contains("abc123secrettoken"));
        assertFalse(text.contains("hunter2"));
        assertFalse(text.contains("XYZ-987"));
        assertTrue(text.contains("***"));
    }

    @Test
    void secretsAreMaskedInErrorMessages() {
        Map<String, Object> result = mapper.modelError(
                new ModelExecutionException("auth failed: Bearer abc123secrettoken"));

        String text = (String) firstTextContent(result);
        assertFalse(text.contains("abc123secrettoken"));
        assertTrue(text.contains("***"));
    }

    @Test
    void completedRejectedByFromException() {
        // COMPLETED is not produced by an exception; calling fromException with it should fail loudly.
        assertThrows(IllegalArgumentException.class,
                () -> mapper.fromException(new LangChainException(LangChainStopReason.COMPLETED, "no") {
                }));
    }

    @Test
    void invalidLimitsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LangChainToolResultMapper(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new LangChainToolResultMapper(10, 0));
    }

    @Test
    void stopReasonWireNamesAreStable() {
        assertEquals("completed", LangChainStopReason.COMPLETED.wireName());
        assertEquals("max_tool_calls", LangChainStopReason.MAX_TOOL_CALLS.wireName());
        assertEquals("timeout", LangChainStopReason.TIMEOUT.wireName());
        assertEquals("policy_denied", LangChainStopReason.POLICY_DENIED.wireName());
        assertEquals("tool_error", LangChainStopReason.TOOL_ERROR.wireName());
        assertEquals("model_error", LangChainStopReason.MODEL_ERROR.wireName());
        assertEquals("cancelled", LangChainStopReason.CANCELLED.wireName());

        assertEquals(LangChainStopReason.TIMEOUT, LangChainStopReason.fromWireName("timeout"));
        assertThrows(IllegalArgumentException.class, () -> LangChainStopReason.fromWireName("nope"));
    }

    // helpers --------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> structured(Map<String, Object> result) {
        Map<String, Object> structured = (Map<String, Object>) result.get("structuredContent");
        assertNotNull(structured, "structuredContent must be present");
        return structured;
    }

    @SuppressWarnings("unchecked")
    private static Object firstTextContent(Map<String, Object> result) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertNotNull(content);
        assertEquals(1, content.size());
        Map<String, Object> first = content.get(0);
        assertEquals("text", first.get("type"));
        Object text = first.get("text");
        assertNotNull(text);
        // Suppress unused-warning style noise.
        assertNull(first.get("nonexistent"));
        return text;
    }
}
