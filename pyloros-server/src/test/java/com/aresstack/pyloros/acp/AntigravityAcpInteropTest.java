package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AntigravityAcpInteropTest {

    private static final int RESPONSE_TIMEOUT_SECONDS = 60;

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void startsAntigravityAndCompletesAnAcpPromptWithoutPylorosServer() throws Exception {
        String workspace = Path.of("").toAbsolutePath().normalize().toString();
        AcpProcessConfiguration configuration = new AcpProcessConfiguration(
                System.getenv("AGY_ACP_EXECUTABLE"),
                List.of(),
                workspace,
                Map.of()
        );

        AcpProcessHandle process = new AcpProcessLauncher().launch(configuration);
        try (AcpJsonRpcConnection connection = new AcpJsonRpcConnection(process.stdout(), process.stdin());
             AcpAgentClient client = new AcpAgentClient(process, connection)) {
            client.initialize().get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            String sessionId = client.createSession(workspace, List.of())
                    .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertFalse(sessionId.isBlank());

            JsonNode response = client.sendPrompt(sessionId,
                            "Respond with OK. Do not call tools or modify files.")
                    .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals("end_turn", response.path("result").path("stopReason").asText());
        }
    }
}
