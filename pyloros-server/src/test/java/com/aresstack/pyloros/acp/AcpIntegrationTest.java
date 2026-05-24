package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcpIntegrationTest {

    @Test
    void testSuccessfulSession() throws Exception {
        try (FakeAcpAgent agent = new FakeAcpAgent();
             ClientContext clientContext = newClient(agent)) {
            agent.setBehavior("success");
            agent.start();

            AgentTask task = newTask(100);
            String sessionId = clientContext.client().createSession("/workspace").get(5, TimeUnit.SECONDS);
            clientContext.client().sendPrompt(sessionId, "hello").get(5, TimeUnit.SECONDS);

            applyNextEvent(task, clientContext.client());
            applyNextEvent(task, clientContext.client());

            assertEquals("fake-session-1", sessionId);
            assertEquals(AgentTaskState.COMPLETED, task.state());
            assertEquals("completed: hello", task.result());
            assertEquals(1, task.events().size());
        }
    }

    @Test
    void testErrorSession() throws Exception {
        try (FakeAcpAgent agent = new FakeAcpAgent();
             ClientContext clientContext = newClient(agent)) {
            agent.setBehavior("error");
            agent.start();

            AgentTask task = newTask(10);
            String sessionId = clientContext.client().createSession("/workspace").get(5, TimeUnit.SECONDS);
            clientContext.client().sendPrompt(sessionId, "hello").get(5, TimeUnit.SECONDS);

            applyNextEvent(task, clientContext.client());

            assertEquals(AgentTaskState.FAILED, task.state());
            assertEquals("simulated failure", task.error());
        }
    }

    @Test
    void testPermissionEvent() throws Exception {
        try (FakeAcpAgent agent = new FakeAcpAgent();
             ClientContext clientContext = newClient(agent)) {
            agent.setBehavior("permission");
            agent.start();

            AgentTask task = newTask(10);
            String sessionId = clientContext.client().createSession("/workspace").get(5, TimeUnit.SECONDS);
            clientContext.client().sendPrompt(sessionId, "hello").get(5, TimeUnit.SECONDS);

            applyNextEvent(task, clientContext.client());

            assertEquals(AgentTaskState.WAITING_FOR_PERMISSION, task.state());
            assertEquals(1, task.pendingPermissions().size());
            assertEquals("perm-1", task.pendingPermissions().getFirst().id());
            assertEquals("Approve access", task.pendingPermissions().getFirst().description());
        }
    }

    @Test
    void testLargeOutput() throws Exception {
        try (FakeAcpAgent agent = new FakeAcpAgent();
             ClientContext clientContext = newClient(agent)) {
            agent.setBehavior("large_output");
            agent.start();

            AgentTask task = newTask(100);
            String sessionId = clientContext.client().createSession("/workspace").get(5, TimeUnit.SECONDS);
            clientContext.client().sendPrompt(sessionId, "hello").get(5, TimeUnit.SECONDS);

            while (task.state() != AgentTaskState.COMPLETED) {
                applyNextEvent(task, clientContext.client());
            }

            assertEquals(AgentTaskState.COMPLETED, task.state());
            assertEquals("large-output-complete", task.result());
            assertEquals(50, task.events().size());
            assertEquals("chunk-0", task.events().getFirst());
            assertEquals("chunk-49", task.events().getLast());
        }
    }

    private static void applyNextEvent(AgentTask task, AcpAgentClient client) throws Exception {
        JsonNode event = client.receiveEvent().get(5, TimeUnit.SECONDS);
        new AcpEventMapper().applyEvent(task, event, 2_000);
    }

    private static AgentTask newTask(int maxEvents) {
        AgentTask task = new AgentTask(AgentTaskId.generate(), "copilot", "/workspace", "prompt", maxEvents);
        task.markRunning();
        return task;
    }

    private static ClientContext newClient(FakeAcpAgent agent) throws IOException {
        AcpProcessHandle processHandle = agent.processHandle();
        AcpJsonRpcConnection connection = new AcpJsonRpcConnection(processHandle.stdout(), processHandle.stdin());
        return new ClientContext(processHandle, connection, new AcpAgentClient(processHandle, connection));
    }

    private record ClientContext(AcpProcessHandle processHandle, AcpJsonRpcConnection connection, AcpAgentClient client) implements AutoCloseable {
        @Override
        public void close() {
            assertNotNull(processHandle);
            assertNotNull(connection);
            assertFalse(client == null);
            client.close();
        }
    }
}
