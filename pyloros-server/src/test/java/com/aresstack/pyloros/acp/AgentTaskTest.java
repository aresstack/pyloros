package com.aresstack.pyloros.acp;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskTest {

    @Test
    void testCreation() {
        AgentTask task = newTask("Prompt");

        assertEquals(AgentTaskState.CREATED, task.state());
        assertEquals("copilot", task.providerId());
        assertEquals("/workspace", task.cwd());
        assertEquals("Prompt", task.promptPreview());
        assertNotNull(task.startedAt());
        assertNotNull(task.updatedAt());
        assertNull(task.completedAt());
        assertTrue(task.events().isEmpty());
        assertTrue(task.pendingPermissions().isEmpty());
        assertFalse(task.cancellationRequested());
    }

    @Test
    void testMarkRunning() {
        AgentTask task = newTask("Prompt");

        task.markRunning();

        assertEquals(AgentTaskState.RUNNING, task.state());
    }

    @Test
    void testComplete() {
        AgentTask task = newTask("Prompt");
        task.markRunning();

        task.complete("Done");

        assertEquals(AgentTaskState.COMPLETED, task.state());
        assertEquals("Done", task.result());
        assertNull(task.error());
        assertNotNull(task.completedAt());
    }

    @Test
    void testFail() {
        AgentTask task = newTask("Prompt");
        task.markRunning();

        task.fail("Boom");

        assertEquals(AgentTaskState.FAILED, task.state());
        assertEquals("Boom", task.error());
        assertNull(task.result());
        assertNotNull(task.completedAt());
    }

    @Test
    void testTimeout() {
        AgentTask task = newTask("Prompt");
        task.markRunning();

        task.timeout();

        assertEquals(AgentTaskState.TIMEOUT, task.state());
        assertEquals("Task timed out", task.error());
        assertNull(task.result());
        assertNotNull(task.completedAt());
    }

    @Test
    void testCancel() {
        AgentTask task = newTask("Prompt");
        task.markRunning();

        task.cancel();

        assertEquals(AgentTaskState.CANCELLED, task.state());
        assertTrue(task.cancellationRequested());
        assertNull(task.result());
        assertNull(task.error());
        assertNotNull(task.completedAt());
    }

    @Test
    void testInvalidTransition() {
        AgentTask task = newTask("Prompt");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> task.complete("Done"));

        assertEquals("Cannot transition task from CREATED to COMPLETED", thrown.getMessage());
    }

    @Test
    void testAddEvent() {
        AgentTask task = newTask("Prompt", 3);

        task.addEvent("one");
        task.addEvent("two");

        assertEquals(List.of("one", "two"), task.events());
        assertEquals(3, task.maxEvents());
    }

    @Test
    void testEventLimitEnforced() {
        AgentTask task = newTask("Prompt", 2);

        task.addEvent("one");
        task.addEvent("two");
        task.addEvent("three");

        assertEquals(List.of("two", "three"), task.events());
    }

    @Test
    void testPromptPreview() {
        String prompt = "x".repeat(250);
        AgentTask task = newTask(prompt);

        assertEquals(200, task.promptPreview().length());
        assertEquals(prompt.substring(0, 200), task.promptPreview());
    }

    @Test
    void testPromptHash() {
        String prompt = "Test prompt";
        AgentTask task = newTask(prompt);

        assertEquals(sha256(prompt), task.promptHash());
    }

    @Test
    void testRequestPermission() {
        AgentTask task = newTask("Prompt");
        PendingPermission permission = new PendingPermission("perm-1", "Approve execution", Instant.now());
        task.markRunning();

        task.requestPermission(permission);

        assertEquals(AgentTaskState.WAITING_FOR_PERMISSION, task.state());
        assertEquals(List.of(permission), task.pendingPermissions());
    }

    private static AgentTask newTask(String prompt) {
        return newTask(prompt, 5);
    }

    private static AgentTask newTask(String prompt, int maxEvents) {
        return new AgentTask(AgentTaskId.generate(), "copilot", "/workspace", prompt, maxEvents);
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
