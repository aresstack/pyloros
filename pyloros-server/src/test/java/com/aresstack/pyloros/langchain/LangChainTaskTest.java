package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LangChainTaskTest {

    @Test
    void testCreateSession() {
        LangChainTask task = newTask("Prompt");

        assertEquals(LangChainTaskState.CREATED, task.state());
        assertEquals("pyloros-ai", task.providerId());
        assertEquals("Prompt", task.promptPreview());
        assertEquals(sha256("Prompt"), task.promptHash());
        assertNotNull(task.taskId());
        assertNotNull(task.startedAt());
        assertNotNull(task.updatedAt());
        assertNull(task.completedAt());
        assertNull(task.stopReason());
        assertNull(task.result());
        assertNull(task.error());
        assertEquals(List.of(), task.usedTools());
    }

    @Test
    void testAddToolCall() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();
        LangChainToolCall toolCall = new LangChainToolCall(
                "intellij/get_problems", "abc123", 42L, "success");

        task.addToolCall(toolCall);

        assertEquals(List.of(toolCall), task.usedTools());
    }

    @Test
    void testAddMultipleToolCalls() {
        LangChainTask task = newTask("Prompt");
        LangChainToolCall first = new LangChainToolCall("a/x", "h1", 1L, "success");
        LangChainToolCall second = new LangChainToolCall("a/y", "h2", 2L, "error");

        task.addToolCall(first);
        task.addToolCall(second);

        assertEquals(List.of(first, second), task.usedTools());
    }

    @Test
    void testSaveResult() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();

        task.complete("The answer", LangChainStopReason.COMPLETED);

        assertEquals(LangChainTaskState.COMPLETED, task.state());
        assertEquals("The answer", task.result());
        assertEquals(LangChainStopReason.COMPLETED, task.stopReason());
        assertNull(task.error());
        assertNotNull(task.completedAt());
    }

    @Test
    void testSaveError() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();

        task.fail("Boom", LangChainStopReason.MODEL_ERROR);

        assertEquals(LangChainTaskState.FAILED, task.state());
        assertEquals("Boom", task.error());
        assertEquals(LangChainStopReason.MODEL_ERROR, task.stopReason());
        assertNull(task.result());
        assertNotNull(task.completedAt());
    }

    @Test
    void testSaveStopReasonMaxToolCalls() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();

        task.complete("Partial answer", LangChainStopReason.MAX_TOOL_CALLS);

        assertEquals(LangChainStopReason.MAX_TOOL_CALLS, task.stopReason());
    }

    @Test
    void testSaveStopReasonTimeout() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();

        task.timeout();

        assertEquals(LangChainTaskState.TIMEOUT, task.state());
        assertEquals(LangChainStopReason.TIMEOUT, task.stopReason());
        assertEquals("Task timed out", task.error());
    }

    @Test
    void testCancel() {
        LangChainTask task = newTask("Prompt");
        task.markRunning();

        task.cancel();

        assertEquals(LangChainTaskState.CANCELLED, task.state());
        assertNull(task.result());
        assertNull(task.error());
        assertNotNull(task.completedAt());
    }

    @Test
    void testPromptIsTruncatedNotStoredInFull() {
        String prompt = "x".repeat(500);
        LangChainTask task = newTask(prompt);

        assertEquals(200, task.promptPreview().length());
        assertEquals(prompt.substring(0, 200), task.promptPreview());
        assertNotEquals(prompt, task.promptPreview());
        assertFalse(task.promptPreview().contains("x".repeat(201)));
    }

    @Test
    void testPromptHashIsSha256() {
        String prompt = "Sensitive secret data";

        LangChainTask task = newTask(prompt);

        assertEquals(sha256(prompt), task.promptHash());
        // Hash is deterministic
        assertEquals(task.promptHash(), newTask(prompt).promptHash());
    }

    @Test
    void testInvalidTransitionCompleteBeforeRunning() {
        LangChainTask task = newTask("Prompt");
        task.complete("Done", LangChainStopReason.COMPLETED);

        assertEquals(LangChainTaskState.COMPLETED, task.state());
        // Once completed, cannot transition again
        assertThrows(IllegalStateException.class,
                () -> task.fail("Boom", LangChainStopReason.MODEL_ERROR));
    }

    private static LangChainTask newTask(String prompt) {
        return new LangChainTask(LangChainTaskId.generate(), "pyloros-ai", prompt);
    }

    private static String sha256(String text) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(messageDigest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
