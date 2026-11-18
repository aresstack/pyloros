package com.aresstack.pyloros.acp;

import com.aresstack.pyloros.config.ServerConfig;
import com.aresstack.pyloros.http.McpRoutes;
import com.aresstack.pyloros.provider.ProviderRegistry;
import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.security.AuthenticationResult;
import com.aresstack.pyloros.tool.ToolCatalog;
import com.aresstack.pyloros.tool.ToolNameFormatter;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolRouter;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntigravityInjectedMcpInteropTest {

    private static final String AGENT_VIEW = "antigravity-acp-agent-view";
    private static final String TEST_TOKEN = "local-integration-test-token";
    private static final int TIMEOUT_SECONDS = 45;
    private static final java.util.concurrent.atomic.AtomicInteger DIRECT_TOOL_CALLS =
            new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger READ_ONLY_CALLS =
            new java.util.concurrent.atomic.AtomicInteger();

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void createsSessionWithRealPylorosMcpRoutesAndInjectedTools() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            server = startMcpServer(vertx);
            String mcpEndpoint = "http://127.0.0.1:" + server.actualPort()
                    + "/?view=" + AGENT_VIEW;

            assertMcpServerResponds(mcpEndpoint);

            String workspace = workspace();
            AcpProcessConfiguration configuration = new AcpProcessConfiguration(
                    System.getenv("AGY_ACP_EXECUTABLE"),
                    List.of(), workspace, Map.of()
            );
            AcpProcessHandle process = new AcpProcessLauncher().launch(configuration);
            try (AcpJsonRpcConnection connection = new AcpJsonRpcConnection(process.stdout(), process.stdin());
                 AcpAgentClient client = new AcpAgentClient(process, connection)) {

                long startedAt = System.nanoTime();
                client.initialize().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                reportElapsed("initialize", startedAt);

                startedAt = System.nanoTime();
                String sessionId = client.createSession(workspace, List.of(injectedMcpServer(mcpEndpoint)))
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                reportElapsed("session/new with injected MCP server", startedAt);
                assertFalse(sessionId.isBlank());

                startedAt = System.nanoTime();
                JsonNode response = client.sendPrompt(
                                sessionId, "Respond with OK. Do not call tools or modify files.")
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                reportElapsed("session/prompt", startedAt);
                assertEquals("end_turn", response.path("result").path("stopReason").asText());
            }
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void executesRealAntigravityThroughProductionAcpVirtualToolProvider() throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            server = startMcpServer(vertx);
            String localMcpUrl = "http://127.0.0.1:" + server.actualPort() + "/";
            String workspace = workspace();

            AcpVirtualToolProvider provider = createProductionProvider(vertx, localMcpUrl, workspace);
            JsonNode request = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
                    "prompt", "Respond with OK. Do not call tools or modify files.",
                    "cwd", workspace,
                    "timeoutSeconds", 75
            ));

            long startedAt = System.nanoTime();
            Map<String, Object> result = provider.callTool("run_task", request)
                    .toCompletionStage().toCompletableFuture().get(85, TimeUnit.SECONDS);
            reportElapsed("AcpVirtualToolProvider.run_task", startedAt);

            assertEquals(Boolean.FALSE, result.get("isError"), String.valueOf(result));
            Object content = result.get("content");
            assertTrue(content instanceof List, String.valueOf(result));
            Object firstItem = ((List<?>) content).get(0);
            assertTrue(firstItem instanceof Map, String.valueOf(result));
            String payload = String.valueOf(((Map<?, ?>) firstItem).get("text"));
            JsonNode taskResult = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
            System.out.println("AcpVirtualToolProvider result: " + taskResult);
            assertEquals("COMPLETED", taskResult.path("state").asText(), payload);
            assertFalse(taskResult.path("task").path("acpSessionId").asText().isBlank());
            assertTrue(taskResult.path("result").asText().matches("OK\\.?"), payload);
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void completesBackgroundTaskWithInjectedMcpServer() throws Exception {
        assertBackgroundTaskCompletes(0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void completesBackgroundTaskWithLargeToolCatalog() throws Exception {
        assertBackgroundTaskCompletes(260);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void completesBackgroundTaskWithMinimalWindowsServiceEnvironment() throws Exception {
        assertBackgroundTaskCompletes(0, launcherWithMinimalWindowsEnvironment());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void callsEagerMcpToolDirectlyThroughRealAcpAgent() throws Exception {
        DIRECT_TOOL_CALLS.set(0);
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            server = startMcpServer(vertx);
            String workspace = workspace();
            String mcpUrl = "http://127.0.0.1:" + server.actualPort() + "/";
            AcpVirtualToolProvider provider = createProductionProvider(vertx, mcpUrl, workspace);
            JsonNode request = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
                    "prompt", "Call the directly registered MCP tool mcp_pyloros_diagnostic__ping exactly once. "
                            + "This is a read-only test tool which returns pong. Do not use call_mcp_tool, "
                            + "read schema files, execute shell commands or modify any files. "
                            + "Then report the result.",
                    "cwd", workspace,
                    "timeoutSeconds", 75
            ));
            long startedAt = System.nanoTime();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode started = toolPayload(provider.callTool("start_task", request)
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            String taskId = started.path("taskId").asText();
            JsonNode status = started;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (!"WAITING_FOR_PERMISSION".equals(status.path("state").asText())
                    && !"COMPLETED".equals(status.path("state").asText())
                    && System.nanoTime() < deadline) {
                Thread.sleep(300);
                status = toolPayload(provider.callTool("get_task_status",
                        mapper.valueToTree(Map.of("taskId", taskId)))
                        .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            }
            assertEquals("WAITING_FOR_PERMISSION", status.path("state").asText(), status.toString());
            String permissionId = status.path("pendingPermissions").get(0).path("id").asText();
            JsonNode answer = toolPayload(provider.callTool("respond_to_permission", mapper.valueToTree(Map.of(
                    "taskId", taskId, "permissionId", permissionId, "approve", true)))
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            assertTrue(answer.path("approved").asBoolean());

            while (!"COMPLETED".equals(status.path("state").asText()) && System.nanoTime() < deadline) {
                Thread.sleep(300);
                status = toolPayload(provider.callTool("get_task_status",
                        mapper.valueToTree(Map.of("taskId", taskId)))
                        .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            }
            reportElapsed("direct eager MCP tool call with explicit approval", startedAt);
            System.out.println("Direct MCP task result: " + status);
            assertEquals("COMPLETED", status.path("state").asText(), status.toString());
            assertEquals(1, DIRECT_TOOL_CALLS.get(), "The native eager MCP tool must actually execute");
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AGY_ACP_EXECUTABLE", matches = ".+")
    void automaticallyApprovesAndExecutesDirectReadOnlyIntellijTool() throws Exception {
        READ_ONLY_CALLS.set(0);
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            server = startMcpServer(vertx, new ReadOnlyIntellijProvider());
            String workspace = workspace();
            String mcpUrl = "http://127.0.0.1:" + server.actualPort() + "/";
            AcpVirtualToolProvider provider = createProductionProvider(vertx, mcpUrl, workspace);
            JsonNode request = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of(
                    "prompt", "Call the direct MCP tool mcp_pyloros_intellij__get_repositories "
                            + "with projectPath C:/Projects/pyloros exactly once. "
                            + "Do not use call_mcp_tool or run shell commands. Then report the result.",
                    "cwd", workspace,
                    "timeoutSeconds", 75
            ));
            long started = System.nanoTime();
            JsonNode result = toolPayload(provider.callTool("run_task", request)
                    .toCompletionStage().toCompletableFuture().get(85, TimeUnit.SECONDS));
            reportElapsed("direct read-only IntelliJ MCP tool", started);
            System.out.println("Read-only direct tool result: " + result);
            assertEquals("COMPLETED", result.path("state").asText(), result.toString());
            assertEquals(1, READ_ONLY_CALLS.get(), "Known read-only MCP tools must execute without approval");
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static void assertBackgroundTaskCompletes(int additionalTools) throws Exception {
        assertBackgroundTaskCompletes(additionalTools, new AcpProcessLauncher());
    }

    private static void assertBackgroundTaskCompletes(
            int additionalTools, AcpProcessLauncher launcher) throws Exception {
        Vertx vertx = Vertx.vertx();
        HttpServer server = null;
        try {
            server = startMcpServer(vertx, additionalTools);
            String localMcpUrl = "http://127.0.0.1:" + server.actualPort() + "/";
            String workspace = workspace();
            AcpVirtualToolProvider provider = createProductionProvider(vertx, localMcpUrl, workspace, launcher);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            JsonNode request = mapper.valueToTree(Map.of(
                    "prompt", "Respond with OK. Do not call tools or modify files.",
                    "cwd", workspace,
                    "timeoutSeconds", 75
            ));
            long startedAt = System.nanoTime();
            JsonNode started = toolPayload(provider.callTool("start_task", request)
                    .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            String taskId = started.path("taskId").asText();
            assertFalse(taskId.isBlank());

            JsonNode status = started;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(85);
            while ("RUNNING".equals(status.path("state").asText())
                    && System.nanoTime() < deadline) {
                Thread.sleep(500);
                JsonNode statusRequest = mapper.valueToTree(Map.of("taskId", taskId));
                status = toolPayload(provider.callTool("get_task_status", statusRequest)
                        .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS));
            }

            reportElapsed("AcpVirtualToolProvider.start_task", startedAt);
            System.out.println("Background task result: " + status);
            assertEquals("COMPLETED", status.path("state").asText(), status.toString());
            assertFalse(status.path("acpSessionId").asText().isBlank());
        } finally {
            if (server != null) {
                server.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
            }
            vertx.close().toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static AcpVirtualToolProvider createProductionProvider(
            Vertx vertx, String localMcpUrl, String workspace) {
        return createProductionProvider(vertx, localMcpUrl, workspace, new AcpProcessLauncher());
    }

    private static AcpVirtualToolProvider createProductionProvider(
            Vertx vertx, String localMcpUrl, String workspace, AcpProcessLauncher launcher) {
        AcpProviderConfiguration configuration = new AcpProviderConfiguration(
                "antigravity-acp",
                "antigravity-acp/",
                AGENT_VIEW,
                List.of("public"),
                new AcpProcessConfiguration(
                        System.getenv("AGY_ACP_EXECUTABLE"),
                        List.of(),
                        workspace,
                        Map.of("PYLOROS_MCP_URL", localMcpUrl)
                ),
                new AcpExecutionConfiguration(75, 100, 12000, 24000)
        );
        return new AcpVirtualToolProvider(
                vertx, configuration, new InMemoryAgentTaskRepository(),
                launcher, new AcpAuditLogger(), new AcpEventMapper(), TEST_TOKEN
        );
    }

    private static AcpProcessLauncher launcherWithMinimalWindowsEnvironment() {
        return new AcpProcessLauncher(commandLine -> {
            ProcessBuilder builder = new ProcessBuilder(commandLine);
            Map<String, String> environment = builder.environment();
            Map<String, String> inherited = new java.util.HashMap<>(environment);
            environment.clear();
            for (String name : List.of(
                    "JAVA_HOME", "PATH", "USERNAME", "OS", "PROCESSOR_IDENTIFIER", "TMP", "TEMP")) {
                String value = inherited.get(name);
                if (value != null) {
                    environment.put(name, value);
                }
            }
            assertFalse(environment.containsKey("USERPROFILE"));
            assertFalse(environment.containsKey("APPDATA"));
            assertFalse(environment.containsKey("LOCALAPPDATA"));
            return builder;
        });
    }

    private static JsonNode toolPayload(Map<String, Object> result) throws Exception {
        assertEquals(Boolean.FALSE, result.get("isError"), String.valueOf(result));
        Object firstItem = ((List<?>) result.get("content")).get(0);
        String json = String.valueOf(((Map<?, ?>) firstItem).get("text"));
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    }

    private static HttpServer startMcpServer(Vertx vertx) throws Exception {
        return startMcpServer(vertx, 0);
    }

    private static HttpServer startMcpServer(Vertx vertx, int additionalTools) throws Exception {
        return startMcpServer(vertx, new DiagnosticToolProvider(additionalTools));
    }

    private static HttpServer startMcpServer(Vertx vertx, ToolProvider provider) throws Exception {
        ProviderRegistry registry = new ProviderRegistry(List.of(provider));
        ToolView agentView = ToolView.named(AGENT_VIEW);
        ToolCatalog catalog = new ToolCatalog(
                registry, ToolNameFormatter.defaultFormatter(), Set.of(agentView)
        );
        ToolRouter router = new ToolRouter(registry, catalog);

        Router httpRouter = Router.router(vertx);
        httpRouter.route().handler(BodyHandler.create());
        new McpRoutes(
                new DiagnosticServerConfig(),
                request -> AuthenticationResult.rejected(401, "oauth_required", Map.of()),
                catalog, router, Set.of(agentView)
        ).mount(httpRouter);

        return vertx.createHttpServer().requestHandler(httpRouter).listen(0, "127.0.0.1")
                .toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static void assertMcpServerResponds(String endpoint) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("X-Pyloros-Injected-View", "acp")
                .header("X-Pyloros-Injected-View-Token", TEST_TOKEN)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("diagnostic__ping"), response.body());
        System.out.println("Local MCP endpoint verified: " + endpoint);
    }

    private static Map<String, Object> injectedMcpServer(String endpoint) {
        return Map.of(
                "name", "pyloros",
                "type", "http",
                "url", endpoint,
                "headers", List.of(
                        Map.of("name", "X-Pyloros-Injected-View", "value", "acp"),
                        Map.of("name", "X-Pyloros-Injected-View-Token", "value", TEST_TOKEN)
                )
        );
    }

    private static String workspace() {
        String configuredWorkspace = System.getenv("AGY_ACP_WORKSPACE");
        return configuredWorkspace == null || configuredWorkspace.isBlank()
                ? Path.of("").toAbsolutePath().normalize().toString()
                : Path.of(configuredWorkspace).toAbsolutePath().normalize().toString();
    }

    private static void reportElapsed(String phase, long startedAt) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        System.out.println("Antigravity ACP phase " + phase + ": " + elapsedMs + " ms");
    }

    private static final class DiagnosticServerConfig implements ServerConfig {
        @Override
        public String mcpProtocolVersion() {
            return "2025-03-26";
        }

        @Override
        public String mcpInjectedViewToken() {
            return TEST_TOKEN;
        }
    }

    private static final class ReadOnlyIntellijProvider implements ToolProvider {
        @Override
        public String providerId() {
            return "intellij";
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.MCP;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            return Future.succeededFuture(List.of(Map.of(
                    "name", "get_repositories",
                    "description", "Return the repositories of the specified IntelliJ project without changing files",
                    "inputSchema", Map.of(
                            "type", "object",
                            "properties", Map.of("projectPath", Map.of("type", "string"))),
                    "_meta", Map.of("antigravity/alwaysLoad", true)
            )));
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            if ("get_repositories".equals(upstreamToolName)) {
                READ_ONLY_CALLS.incrementAndGet();
            }
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Repository: pyloros")),
                    "isError", false));
        }
    }

    private static final class DiagnosticToolProvider implements ToolProvider {
        private final int additionalTools;

        private DiagnosticToolProvider(int additionalTools) {
            this.additionalTools = additionalTools;
        }

        @Override
        public String providerId() {
            return "diagnostic";
        }

        @Override
        public ProviderType providerType() {
            return ProviderType.MCP;
        }

        @Override
        public Future<List<Map<String, Object>>> listTools() {
            List<Map<String, Object>> tools = new java.util.ArrayList<>();
            tools.add(Map.of(
                    "name", "ping",
                    "description", "Return pong without changing any data",
                    "inputSchema", Map.of("type", "object", "properties", Map.of()),
                    "_meta", Map.of("antigravity/alwaysLoad", true)
            ));
            for (int index = 0; index < additionalTools; index++) {
                tools.add(Map.of(
                        "name", "diagnostic_action_" + index,
                        "description", ("Analyze the project and return a short read-only diagnostic summary. ").repeat(12),
                        "inputSchema", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "projectPath", Map.of("type", "string", "description", "Absolute project root path"),
                                        "pattern", Map.of("type", "string", "description", "Optional source code pattern")
                                )
                        )
                ));
            }
            return Future.succeededFuture(tools);
        }

        @Override
        public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
            if ("ping".equals(upstreamToolName)) {
                DIRECT_TOOL_CALLS.incrementAndGet();
            }
            return Future.succeededFuture(Map.of(
                    "content", List.of(Map.of("type", "text", "text", "pong")),
                    "isError", false
            ));
        }
    }
}
