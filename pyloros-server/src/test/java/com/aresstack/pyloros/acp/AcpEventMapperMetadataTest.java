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

    private static JsonNode nestedUpdate(String updateType, String fields) throws Exception {
        return JSON.readTree("{\"method\":\"session/update\",\"params\":{\"update\":{"
                + "\"sessionUpdate\":\"" + updateType + "\"," + fields + "}}}");
    }
}
