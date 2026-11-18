package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AcpTaskPermissionHandlerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void automaticallyApprovesKnownReadOnlyToolExactlyOnce() throws Exception {
        AgentTask task = newRunningTask();
        AcpTaskPermissionHandler handler = new AcpTaskPermissionHandler(
                task, new InMemoryAgentTaskRepository(), Set.of("intellij__get_repositories"));

        Map<String, Object> result = handler.requestPermission(
                permissionRequest("pyloros_intellij__get_repositories")).toCompletableFuture().get();

        assertEquals(Map.of("outcome", Map.of("outcome", "selected", "optionId", "allow")), result);
        assertEquals(AgentTaskState.RUNNING, task.state());
        assertTrue(task.pendingPermissions().isEmpty());
    }

    @Test
    void requiresExplicitApprovalForMutatingTool() throws Exception {
        AgentTask task = newRunningTask();
        AcpTaskPermissionHandler handler = new AcpTaskPermissionHandler(
                task, new InMemoryAgentTaskRepository(), Set.of("intellij__get_repositories"));

        CompletionStage<Map<String, Object>> future = handler.requestPermission(
                permissionRequest("pyloros_intellij__replace_text_in_file"));
        assertFalse(future.toCompletableFuture().isDone());
        assertEquals(AgentTaskState.WAITING_FOR_PERMISSION, task.state());
        String permissionId = task.pendingPermissions().get(0).id();

        assertTrue(handler.respond(permissionId, true));
        assertEquals(Map.of("outcome", Map.of("outcome", "selected", "optionId", "allow")),
                future.toCompletableFuture().get());
        assertEquals(AgentTaskState.RUNNING, task.state());
        assertTrue(task.pendingPermissions().isEmpty());
        assertFalse(handler.respond(permissionId, true));
    }

    @Test
    void doesNotApproveLookalikeToolsFromOtherServers() throws Exception {
        AgentTask task = newRunningTask();
        AcpTaskPermissionHandler handler = new AcpTaskPermissionHandler(
                task, new InMemoryAgentTaskRepository(), Set.of("intellij__get_repositories"));

        CompletionStage<Map<String, Object>> future = handler.requestPermission(
                permissionRequest("untrusted_intellij__get_repositories"));
        String permissionId = task.pendingPermissions().get(0).id();

        assertTrue(handler.respond(permissionId, false));
        assertEquals(Map.of("outcome", Map.of("outcome", "selected", "optionId", "deny")),
                future.toCompletableFuture().get());
        assertEquals(AgentTaskState.RUNNING, task.state());
    }

    @Test
    void cancelsUnansweredPermissionsWhenSessionEnds() throws Exception {
        AgentTask task = newRunningTask();
        AcpTaskPermissionHandler handler = new AcpTaskPermissionHandler(
                task, new InMemoryAgentTaskRepository(), Set.of());

        CompletionStage<Map<String, Object>> future = handler.requestPermission(
                permissionRequest("pyloros_intellij__execute_terminal_command"));
        handler.cancelPending();

        assertEquals(Map.of("outcome", Map.of("outcome", "cancelled")),
                future.toCompletableFuture().get());
    }

    private static AgentTask newRunningTask() {
        AgentTask task = new AgentTask(AgentTaskId.generate(), "antigravity-acp",
                "C:/Projects/pyloros", "Test", 50);
        task.markRunning();
        return task;
    }

    private static JsonNode permissionRequest(String toolTitle) throws Exception {
        return JSON.valueToTree(Map.of(
                "toolCall", Map.of("title", toolTitle, "kind", "other"),
                "options", java.util.List.of(
                        Map.of("kind", "allow_once", "optionId", "allow"),
                        Map.of("kind", "reject_once", "optionId", "deny"))));
    }
}
