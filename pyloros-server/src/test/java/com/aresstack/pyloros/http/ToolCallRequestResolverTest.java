package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallRequestResolverTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void resolveRpcToolCallUsesExplicitToolName() throws Exception {
        McpToolCall call = ToolCallRequestResolver.resolveRpcToolCall(
                JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"intellij-index__ide_index_status\",\"arguments\":{\"projectPath\":\"C:/Projects/pyloros\"}}}")
        );

        assertEquals("intellij-index__ide_index_status", call.name());
        assertEquals("C:/Projects/pyloros", call.arguments().path("projectPath").asText());
    }

    @Test
    void resolveRpcToolCallSupportsAlternativeToolParameter() throws Exception {
        McpToolCall call = ToolCallRequestResolver.resolveRpcToolCall(
                JSON.readTree("{\"method\":\"call_tool\",\"params\":{\"tool\":\"intellij__get_project_modules\",\"input\":{\"a\":1}}}")
        );

        assertEquals("intellij__get_project_modules", call.name());
        assertEquals(1, call.arguments().path("a").asInt());
    }
}
