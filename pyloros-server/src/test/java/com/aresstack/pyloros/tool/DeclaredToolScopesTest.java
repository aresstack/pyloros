package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scopes the shipped Pyloros providers actually declare.
 *
 * <p>Only mappings that follow from the code are asserted here: {@code ping} returns a
 * constant string, while {@code skills_save} and {@code skills_delete} modify the
 * Pyloros user directory. Tools proxied from an upstream MCP server carry no
 * declaration, because only that upstream knows whether one of its tools writes.
 *
 * <p>The calls that are expected to pass the gate are made with missing arguments on
 * purpose: that proves the scope check let the call through without the tool touching
 * the real user directory.
 */
class DeclaredToolScopesTest {

    private ToolRouter enforcing;
    private ToolRouter legacy;

    @BeforeEach
    void setUp() throws Exception {
        ProviderRegistry registry = new ProviderRegistry(List.of(
                new PylorosPingToolProvider(), new UserSkillDeleteToolProvider()));
        ToolCatalog catalog = new ToolCatalog(registry, ToolNameFormatter.defaultFormatter());
        enforcing = new ToolRouter(registry, catalog, true);
        legacy = new ToolRouter(registry, catalog, false);
        await(catalog.refreshForView(ToolView.PUBLIC));
    }

    @Test
    void pingNeedsNothingBeyondTheGatewayScope() throws Exception {
        assertNull(new PylorosPingToolProvider().requiredScope(PylorosPingToolProvider.TOOL_NAME));

        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall(PylorosPingToolProvider.TOOL_NAME, null),
                ToolView.PUBLIC, List.of("mcp")));

        assertFalse((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("alive"));
    }

    @Test
    void savingASkillIsDeniedWithoutTheWriteScope() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall(PylorosPingToolProvider.SAVE_TOOL_NAME, null),
                ToolView.PUBLIC, List.of("mcp")));

        assertTrue((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("pyloros:write"),
                "the refusal must name the missing scope: " + result);
        assertFalse(result.toString().contains("Saved skill"), "the tool must not have run");
    }

    @Test
    void savingASkillPassesTheGateWithTheWriteScope() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall(PylorosPingToolProvider.SAVE_TOOL_NAME, null),
                ToolView.PUBLIC, List.of("mcp", "pyloros:write")));

        // Reached the tool, which then rejected the empty argument list itself.
        assertTrue(result.toString().contains("Missing required argument: targetPlatform"),
                "the call must reach the tool: " + result);
    }

    @Test
    void deletingASkillIsDeniedWithoutTheWriteScope() throws Exception {
        Map<String, Object> result = await(enforcing.callTool(
                new McpToolCall(UserSkillDeleteToolProvider.TOOL_NAME, null),
                ToolView.PUBLIC, List.of("mcp")));

        assertTrue((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("pyloros:write"));
    }

    @Test
    void deletingASkillPassesTheGateWithTheWriteScope() {
        // The tool itself rejects the missing arguments, so nothing is deleted.
        Future<Map<String, Object>> call = enforcing.callTool(
                new McpToolCall(UserSkillDeleteToolProvider.TOOL_NAME, null),
                ToolView.PUBLIC, List.of("pyloros:write"));

        Throwable failure = call.toCompletionStage().toCompletableFuture()
                .handle((value, error) -> error).join();
        assertTrue(failure != null && failure.getMessage().contains("targetPlatform"),
                "the call must reach the tool, which then validates its arguments: " + failure);
    }

    @Test
    void withEnforcementOffAnMcpOnlyTokenStillReachesEveryTool() throws Exception {
        // This is the deployed state: the ChatGPT connector requests only "mcp", so
        // turning enforcement on by default would break every writing tool.
        Map<String, Object> result = await(legacy.callTool(
                new McpToolCall(PylorosPingToolProvider.SAVE_TOOL_NAME, null),
                ToolView.PUBLIC, List.of("mcp")));

        assertFalse(result.toString().contains("Permission denied"),
                "no scope check may run while enforcement is off: " + result);
        assertTrue(result.toString().contains("Missing required argument: targetPlatform"));
    }

    @Test
    void anUpstreamProxiedToolCarriesNoDeclaration() {
        // Pyloros cannot know whether a remote tool writes, so it must not guess.
        assertNull(new com.aresstack.pyloros.upstream.mcp.GenericMcpToolProvider(
                null, new NoOpUpstream()).requiredScope("anything"));
    }

    /** Minimal upstream client: the provider is only asked about its scopes here. */
    private static final class NoOpUpstream
            implements com.aresstack.pyloros.upstream.mcp.McpUpstreamClient {

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of());
        }

        @Override
        public Future<io.vertx.core.json.JsonObject> callTool(
                String nativeToolName, io.vertx.core.json.JsonObject arguments) {
            return Future.succeededFuture(new io.vertx.core.json.JsonObject());
        }
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }
}
