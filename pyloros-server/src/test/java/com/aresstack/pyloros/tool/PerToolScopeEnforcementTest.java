package com.aresstack.pyloros.tool;

import com.aresstack.pyloros.domain.tool.McpToolCall;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tool runs only when the caller holds the scope that tool demands.
 *
 * <p>Before this, the gateway checked a single service-wide scope and threw the scope
 * set away, so any token good enough to reach Pyloros was good enough for every
 * tool. The webserver could issue a finer scope but nothing ever demanded it.
 */
class PerToolScopeEnforcementTest {

    private ToolRouter router;

    @BeforeEach
    void setUp() throws Exception {
        ProviderRegistry registry = new ProviderRegistry(List.of(new ScopedToolProvider()));
        ToolCatalog catalog = new ToolCatalog(registry, ToolNameFormatter.defaultFormatter());
        // Per-tool enforcement is opt-in; this suite is about the enforcing path.
        router = new ToolRouter(registry, catalog, true);
        // Populate the catalogue the router consults for the tool's requirement.
        await(catalog.refreshForView(ToolView.PUBLIC));
    }

    @Test
    void aReadToolRunsWithTheReadScope() throws Exception {
        Map<String, Object> result = call("test__read_status", List.of("pyloros", "pyloros:read"));

        assertFalse((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("read ok"));
    }

    @Test
    void aWriteToolIsDeniedWithoutTheWriteScope() throws Exception {
        Map<String, Object> result = call("test__write_file", List.of("pyloros", "pyloros:read"));

        assertTrue((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("pyloros:write"),
                "the refusal should name the missing scope: " + result);
        assertFalse(result.toString().contains("write ok"), "the tool must not have run");
    }

    @Test
    void aWriteToolRunsWithTheWriteScope() throws Exception {
        Map<String, Object> result = call("test__write_file",
                List.of("pyloros", "pyloros:read", "pyloros:write"));

        assertFalse((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("write ok"));
    }

    @Test
    void aToolWithoutItsOwnRequirementRunsOnTheGatewayScopeAlone() throws Exception {
        // Unchanged behaviour for every existing provider: no declared scope, no extra
        // demand. This is what keeps the migration optional.
        Map<String, Object> result = call("test__plain", List.of("mcp"));

        assertFalse((Boolean) result.get("isError"));
        assertTrue(result.toString().contains("plain ok"));
    }

    @Test
    void anEmptyScopeSetStillBlocksAToolThatDemandsOne() throws Exception {
        assertTrue((Boolean) call("test__write_file", List.of()).get("isError"));
        assertTrue((Boolean) call("test__write_file", null).get("isError"));
    }

    @Test
    void theLegacyTwoArgumentCallStillWorksForCallersWithoutScopes() throws Exception {
        // ACP and the manager agent call this overload; it must not start demanding
        // scopes it has no way of knowing.
        Map<String, Object> result = await(router.callTool(
                new McpToolCall("test__write_file", null), ToolView.PUBLIC));

        assertFalse((Boolean) result.get("isError"));
    }

    private Map<String, Object> call(String toolName, List<String> scopes) throws Exception {
        return await(router.callTool(new McpToolCall(toolName, null), ToolView.PUBLIC, scopes));
    }

    private static <T> T await(Future<T> future) throws Exception {
        return future.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    }

    /** Declares a read, a write and an unrestricted tool. */
    private static final class ScopedToolProvider implements ToolProvider {

        @Override
        public String providerId() {
            return "test";
        }

        @Override
        public String requiredScope(String upstreamToolName) {
            return switch (upstreamToolName) {
                case "read_status" -> "pyloros:read";
                case "write_file" -> "pyloros:write";
                default -> null;
            };
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of(
                    tool("read_status"), tool("write_file"), tool("plain")));
        }

        private static Map<String, Object> tool(String name) {
            return Map.of("name", name, "description", name,
                    "inputSchema", Map.of("type", "object"));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            String text = switch (upstreamToolName) {
                case "read_status" -> "read ok";
                case "write_file" -> "write ok";
                default -> "plain ok";
            };
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", text)),
                    "isError", false));
        }
    }
}
