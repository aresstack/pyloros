package com.aresstack.pyloros.http;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCallRequestResolverTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void resolvePathToolNameKeepsDoubleUnderscoreName() {
        String resolved = ToolCallRequestResolver.resolvePathToolName("intellij-index__ide_index_status");

        assertEquals("intellij-index__ide_index_status", resolved);
    }

    @Test
    void resolveRpcToolCallFallsBackToPathToolName() throws Exception {
        McpToolCall call = ToolCallRequestResolver.resolveRpcToolCall(
                JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"call_tool\",\"params\":{\"arguments\":{\"a\":1}}}"),
                "intellij-index__ide_index_status"
        );

        assertEquals("intellij-index__ide_index_status", call.name());
        assertEquals(1, call.arguments().path("a").asInt());
    }

    @Test
    void resolveRpcToolCallUsesExplicitNameWhenPresent() throws Exception {
        McpToolCall call = ToolCallRequestResolver.resolveRpcToolCall(
                JSON.readTree("{\"jsonrpc\":\"2.0\",\"method\":\"tools/call\",\"params\":{\"name\":\"intellij-index__ide_index_status\",\"arguments\":{\"projectPath\":\"C:/Projects/pyloros\"}}}"),
                "intellij__other_tool"
        );

        assertEquals("intellij-index__ide_index_status", call.name());
        assertEquals("C:/Projects/pyloros", call.arguments().path("projectPath").asText());
    }

    @Test
    void directPathInvocationDetectedWhenMethodMissing() throws Exception {
        assertTrue(ToolCallRequestResolver.isDirectPathInvocation(
                JSON.readTree("{\"projectPath\":\"C:/Projects/pyloros\"}"),
                "intellij-index__ide_index_status"
        ));

        assertFalse(ToolCallRequestResolver.isDirectPathInvocation(
                JSON.readTree("{\"method\":\"tools/call\"}"),
                "intellij-index__ide_index_status"
        ));
    }

    @Test
    void resolvePathInvocationUsesBodyAsArgumentsWhenNoWrapperFieldsExist() throws Exception {
        McpToolCall call = ToolCallRequestResolver.resolvePathInvocationToolCall(
                JSON.readTree("{\"projectPath\":\"C:/Projects/pyloros\"}"),
                "intellij-index__ide_index_status"
        );

        assertEquals("intellij-index__ide_index_status", call.name());
        assertEquals("C:/Projects/pyloros", call.arguments().path("projectPath").asText());
    }
}

