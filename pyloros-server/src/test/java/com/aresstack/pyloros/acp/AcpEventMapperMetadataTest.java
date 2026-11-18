package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AcpEventMapperMetadataTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void keepsAgentTextAsResultWhenUsageNotificationArrivesLast() throws Exception {
        AgentTask task = new AgentTask(
                AgentTaskId.generate(), "antigravity-acp", "C:/Projects",
                "Respond with OK.", 20
        );
        task.markRunning();
        AcpEventMapper mapper = new AcpEventMapper();

        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"OK.\"}"), 1000);
        mapper.applyEvent(task, nestedUpdate("usage_update",
                "\"used\":13951,\"size\":1048576"), 1000);
        mapper.applyEvent(task, nestedUpdate("available_commands_update",
                "\"availableCommands\":[]"), 1000);
        mapper.applyPromptResponse(task,
                JSON.readTree("{\"result\":{\"stopReason\":\"end_turn\"}}"), 1000);

        assertEquals(AgentTaskState.COMPLETED, task.state());
        assertEquals("OK.", task.result());
        assertEquals(1, task.events().size());
    }

    @Test
    void assemblesAllAgentMessageChunksEvenWhenEventHistoryIsBounded() throws Exception {
        AgentTask task = runningTask(2);
        AcpEventMapper mapper = new AcpEventMapper();

        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"Das Repository \"}"), 5, 500);
        mapper.applyEvent(task, nestedUpdate("agent_thought_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"internal reasoning\"}"), 5, 500);
        mapper.applyEvent(task, nestedUpdate("tool_call_update",
                "\"toolCallId\":\"test-tool\",\"status\":\"completed\""), 5, 500);
        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"heißt pyloros.\"}"), 5, 500);
        mapper.applyEvent(task, nestedUpdate("usage_update",
                "\"used\":1200,\"size\":4096"), 5, 500);
        mapper.applyPromptResponse(task,
                JSON.readTree("{\"result\":{\"stopReason\":\"end_turn\"}}"), 5, 500);

        assertEquals(AgentTaskState.COMPLETED, task.state());
        assertEquals("Das Repository heißt pyloros.", task.result());
        assertEquals(2, task.events().size());
    }

    @Test
    void limitsEntireResponseIndependentlyFromEventPreviewLength() throws Exception {
        AgentTask task = runningTask(3);
        AcpEventMapper mapper = new AcpEventMapper();

        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"abcdef\"}"), 2, 9);
        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"ghijkl\"}"), 2, 9);
        mapper.applyPromptResponse(task,
                JSON.readTree("{\"result\":{\"stopReason\":\"end_turn\"}}"), 2, 9);

        assertEquals("abcdefghi", task.result());
        assertEquals(java.util.List.of("ab", "gh"), task.events());
    }

    @Test
    void prefersExplicitFinalResponseToMessageStream() throws Exception {
        AgentTask task = runningTask(4);
        AcpEventMapper mapper = new AcpEventMapper();

        mapper.applyEvent(task, nestedUpdate("agent_message_chunk",
                "\"content\":{\"type\":\"text\",\"text\":\"Partial\"}"), 20, 50);
        mapper.applyPromptResponse(task, JSON.readTree(
                "{\"result\":{\"stopReason\":\"end_turn\",\"result\":\"Explicit full answer\"}}"), 20, 50);

        assertEquals("Explicit full answer", task.result());
    }

    @Test
    void assemblesLegacyTextNotifications() throws Exception {
        AgentTask task = runningTask(3);
        AcpEventMapper mapper = new AcpEventMapper();

        mapper.applyEvent(task, JSON.readTree(
                "{\"method\":\"session/update\",\"params\":{\"type\":\"text\",\"text\":\"Hello \"}}"), 3, 50);
        mapper.applyEvent(task, JSON.readTree(
                "{\"method\":\"session/update\",\"params\":{\"type\":\"text\",\"text\":\"world\"}}"), 3, 50);
        mapper.applyPromptResponse(task,
                JSON.readTree("{\"result\":{\"stopReason\":\"end_turn\"}}"), 3, 50);

        assertEquals("Hello world", task.result());
    }

    private static AgentTask runningTask(int maximumEvents) {
        AgentTask task = new AgentTask(
                AgentTaskId.generate(), "antigravity-acp", "C:/Projects",
                "Test", maximumEvents);
        task.markRunning();
        return task;
    }

    private static JsonNode nestedUpdate(String updateType, String fields) throws Exception {
        return JSON.readTree("{\"method\":\"session/update\",\"params\":{\"update\":{"
                + "\"sessionUpdate\":\"" + updateType + "\"," + fields + "}}}");
    }
}
