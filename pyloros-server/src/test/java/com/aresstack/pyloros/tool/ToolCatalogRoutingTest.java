package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCatalogRoutingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void listToolsBuildsStableSnapshotWithSlashNames() {
        RecordingProvider github = new RecordingProvider("github", false, "get_me");
        RecordingProvider intellij = new RecordingProvider("intellij", false, "get_project_modules");
        RecordingProvider index = new RecordingProvider("intellij-index", false, "ide_index_status");
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(
                new PylorosPingToolProvider(),
                intellij,
                github,
                index
        )));

        List<Map<String, Object>> tools = await(toolCatalog.listTools());
        Set<String> names = toolNames(tools);

        assertTrue(names.contains("pyloros__ping"));
        assertTrue(names.contains("github/get_me"));
        assertTrue(names.contains("intellij/get_project_modules"));
        assertTrue(names.contains("intellij-index/ide_index_status"));

        ToolCatalogSnapshot snapshot = toolCatalog.snapshot();
        ToolCatalogEntry githubEntry = snapshot.toolsByExternalName().get("github/get_me");
        assertNotNull(githubEntry);
        assertEquals(new ToolAddress("github", "get_me"), githubEntry.address());
        assertSame(githubEntry, snapshot.toolsByAddress().get(new ToolAddress("github", "get_me")));
        assertEquals("github/get_me", githubEntry.descriptor().get("name"));
        assertEquals(List.of(githubEntry), snapshot.toolsByProviderId().get("github"));
    }

    @Test
    void routerRoutesGithubSlashNameToProviderAndUpstreamTool() {
        RecordingProvider github = new RecordingProvider("github", false, "get_me");
        ToolRouter toolRouter = routerWithCatalog(github);
        JsonNode arguments = JSON.createObjectNode().put("viewer", true);

        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("github/get_me", arguments)));

        assertEquals("get_me", github.lastUpstreamToolName);
        assertEquals(arguments, github.lastArguments);
        assertFalse(Boolean.TRUE.equals(result.get("isError")));
    }

    @Test
    void routerRoutesIntellijSlashNameToProviderAndUpstreamTool() {
        RecordingProvider intellij = new RecordingProvider("intellij", false, "get_project_modules");
        ToolRouter toolRouter = routerWithCatalog(intellij);
        JsonNode arguments = JSON.createObjectNode().put("projectPath", "C:/Projects/pyloros");

        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("intellij/get_project_modules", arguments)));

        assertEquals("get_project_modules", intellij.lastUpstreamToolName);
        assertEquals(arguments, intellij.lastArguments);
        assertFalse(Boolean.TRUE.equals(result.get("isError")));
    }

    @Test
    void routerRoutesHyphenatedProviderIdToProviderAndUpstreamTool() {
        RecordingProvider index = new RecordingProvider("intellij-index", false, "ide_index_status");
        ToolRouter toolRouter = routerWithCatalog(index);
        JsonNode arguments = JSON.createObjectNode().put("projectPath", "C:/Projects/pyloros");

        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("intellij-index/ide_index_status", arguments)));

        assertEquals("ide_index_status", index.lastUpstreamToolName);
        assertEquals(arguments, index.lastArguments);
        assertFalse(Boolean.TRUE.equals(result.get("isError")));
    }

    @Test
    void unknownToolNameReturnsCleanErrorResult() {
        RecordingProvider github = new RecordingProvider("github", false, "get_me");
        ToolRouter toolRouter = routerWithCatalog(github);

        Map<String, Object> result = await(toolRouter.callTool(new McpToolCall("github/does_not_exist", JSON.createObjectNode())));

        assertEquals(Boolean.TRUE, result.get("isError"));
        assertEquals("Tool not found: github/does_not_exist", firstText(result));
    }

    @Test
    void duplicateExternalNameFailsFast() {
        RecordingProvider nativeA = new RecordingProvider("native-a", true, "shared_tool");
        RecordingProvider nativeB = new RecordingProvider("native-b", true, "shared_tool");
        ToolCatalog toolCatalog = new ToolCatalog(new ProviderRegistry(List.of(nativeA, nativeB)));

        CompletionException thrown = assertThrows(CompletionException.class, () -> await(toolCatalog.listTools()));

        assertTrue(thrown.getCause() instanceof IllegalStateException);
        assertEquals("Duplicate external tool name: shared_tool", thrown.getCause().getMessage());
    }

    @Test
    void duplicateProviderIdFailsFast() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new ProviderRegistry(List.of(
                new RecordingProvider("github", false, "get_me"),
                new RecordingProvider("github", false, "search_repositories")
        )));

        assertEquals("Duplicate provider id: github", thrown.getMessage());
    }

    private static ToolRouter routerWithCatalog(ToolProvider provider) {
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(provider));
        ToolCatalog toolCatalog = new ToolCatalog(providerRegistry);
        await(toolCatalog.listTools());
        return new ToolRouter(providerRegistry, toolCatalog);
    }

    private static Set<String> toolNames(List<Map<String, Object>> tools) {
        Set<String> names = new LinkedHashSet<>();
        for (Map<String, Object> tool : tools) {
            names.add((String) tool.get("name"));
        }
        return names;
    }

    private static String firstText(Map<String, Object> result) {
        Object content = result.get("content");
        if (!(content instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        Object first = list.getFirst();
        if (!(first instanceof Map<?, ?> item)) {
            return "";
        }
        Object text = item.get("text");
        return text == null ? "" : text.toString();
    }

    private static <T> T await(Future<T> future) {
        return future.toCompletionStage().toCompletableFuture().join();
    }

    private static final class RecordingProvider implements ToolProvider {

        private final String providerId;
        private final boolean preservesUpstreamToolName;
        private final List<Map<String, Object>> tools;
        private String lastUpstreamToolName;
        private JsonNode lastArguments;

        private RecordingProvider(String providerId, boolean preservesUpstreamToolName, String... upstreamToolNames) {
            this.providerId = providerId;
            this.preservesUpstreamToolName = preservesUpstreamToolName;
            this.tools = new ArrayList<>();
            for (String upstreamToolName : upstreamToolNames) {
                tools.add(Map.of(
                        "name", upstreamToolName,
                        "description", providerId + ":" + upstreamToolName,
                        "inputSchema", Map.of("type", "object")
                ));
            }
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public boolean preservesUpstreamToolName() {
            return preservesUpstreamToolName;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(tools);
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            this.lastUpstreamToolName = upstreamToolName;
            this.lastArguments = arguments;
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", providerId + ":" + upstreamToolName)),
                    "isError", false
            ));
        }
    }
}

