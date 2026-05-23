package com.aresstack.pyloros.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Local smoke test for MCP aggregation.
 * Verifies only the local Pyloros MCP router and upstream aggregation behavior.
 * It does not verify the ChatGPT api_tool layer.
 *
 * Run: gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
 *
 * Environment:
 *   PYLOROS_SMOKE_ACCESS_TOKEN  (required) - Bearer token for Pyloros
 *   PYLOROS_SMOKE_MCP_URL       (optional) - default: http://127.0.0.1:8081/pyloros
 */
public class McpAggregationSmokeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_MCP_URL = "http://127.0.0.1:8081/pyloros";
    private static final String PYLOROS_PING = "pyloros__ping";
    private static final String INTELLIJ_TOOL = "intellij__get_project_modules";
    private static final String GITHUB_TOOL = "github__get_me";
    private static final String INTELLIJ_INDEX_TOOL = "intellij-index__ide_index_status";

    private final String mcpUrl;
    private final String accessToken;
    private final HttpClient httpClient;

    private boolean success = true;
    private int testsPassed = 0;
    private int testsFailed = 0;

    public McpAggregationSmokeTest(String mcpUrl, String accessToken) {
        this.mcpUrl = mcpUrl;
        this.accessToken = accessToken;
        this.httpClient = HttpClient.newHttpClient();
    }

    public static void main(String[] args) {
        String token = System.getenv("PYLOROS_SMOKE_ACCESS_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("[ERROR] PYLOROS_SMOKE_ACCESS_TOKEN environment variable not set");
            System.exit(1);
        }

        String mcpUrl = System.getenv("PYLOROS_SMOKE_MCP_URL");
        if (mcpUrl == null || mcpUrl.isBlank()) {
            mcpUrl = DEFAULT_MCP_URL;
        }

        McpAggregationSmokeTest test = new McpAggregationSmokeTest(mcpUrl, token);
        test.run();

        if (!test.success) {
            System.exit(1);
        }
    }

    private void run() {
        System.out.println("[SMOKE] Starting MCP Aggregation Smoke Test");
        System.out.println("[SMOKE] MCP URL: " + mcpUrl);
        System.out.println("[SMOKE] Scope: local Pyloros router only (not ChatGPT api_tool)");

        System.out.println();
        System.out.println("[SMOKE] Step 1: Listing all tools...");
        JsonNode toolsResponse = rpcCall("tools/list", "{}");
        if (toolsResponse == null) {
            fail("tools/list failed");
            return;
        }

        JsonNode toolsArray = toolsResponse.path("result").path("tools");
        if (!toolsArray.isArray()) {
            fail("tools/list result.tools is not an array");
            return;
        }

        Set<String> availableTools = new LinkedHashSet<>();
        for (JsonNode tool : toolsArray) {
            availableTools.add(tool.path("name").asText());
        }

        int totalTools = toolsArray.size();
        System.out.println("[SMOKE] Total tools: " + totalTools);

        System.out.println();
        System.out.println("[SMOKE] Step 2: Validating required canonical tool names...");
        requireTool(availableTools, PYLOROS_PING);
        requireTool(availableTools, INTELLIJ_TOOL);
        requireTool(availableTools, GITHUB_TOOL);
        requireTool(availableTools, INTELLIJ_INDEX_TOOL);

        System.out.println();
        System.out.println("[SMOKE] Step 3: Testing " + PYLOROS_PING + "...");
        checkToolCall(PYLOROS_PING, "{}");

        System.out.println();
        System.out.println("[SMOKE] Step 4: Testing " + INTELLIJ_TOOL + "...");
        checkToolCall(INTELLIJ_TOOL, "{}");

        System.out.println();
        System.out.println("[SMOKE] Step 5: Testing " + GITHUB_TOOL + "...");
        checkToolCall(GITHUB_TOOL, "{}");

        System.out.println();
        System.out.println("[SMOKE] Step 6: Testing " + INTELLIJ_INDEX_TOOL + "...");
        checkToolCall(INTELLIJ_INDEX_TOOL, "{}");

        System.out.println();
        System.out.println("[SMOKE] ========== SUMMARY ==========");
        System.out.println("[SMOKE] Total tools: " + totalTools);
        System.out.println("[SMOKE] Tests passed: " + testsPassed);
        System.out.println("[SMOKE] Tests failed: " + testsFailed);
        System.out.println("[SMOKE] Result: " + (success ? "SUCCESS" : "FAILED"));
    }

    private void requireTool(Set<String> availableTools, String toolName) {
        if (availableTools.contains(toolName)) {
            pass(toolName + " found");
            return;
        }
        fail(toolName + " NOT found");
    }

    private void checkToolCall(String toolName, String arguments) {
        JsonNode response = rpcCallTool(toolName, arguments);
        if (response != null) {
            pass(toolName + " call succeeded");
            return;
        }
        fail(toolName + " call failed");
    }

    private JsonNode rpcCall(String method, String params) {
        try {
            String body = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"%s\",\"params\":%s}",
                method, params
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[ERROR] HTTP " + response.statusCode() + " from " + method);
                System.out.println("[ERROR] Response: " + response.body());
                return null;
            }

            return JSON.readTree(response.body());
        } catch (Exception e) {
            System.out.println("[ERROR] " + method + " failed: " + e.getMessage());
            return null;
        }
    }

    private JsonNode rpcCallTool(String toolName, String arguments) {
        try {
            String body = String.format(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"%s\",\"arguments\":%s}}",
                toolName, arguments
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mcpUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[ERROR] HTTP " + response.statusCode() + " calling " + toolName);
                return null;
            }

            JsonNode result = JSON.readTree(response.body());
            if (result.has("error")) {
                System.out.println("[ERROR] tools/call " + toolName + " returned error: " + result.path("error"));
                return null;
            }

            JsonNode rpcResult = result.path("result");
            if (rpcResult.path("isError").asBoolean(false)) {
                System.out.println("[ERROR] tools/call " + toolName + " returned isError=true");
                return null;
            }

            return result;
        } catch (Exception e) {
            System.out.println("[ERROR] tools/call " + toolName + " failed: " + e.getMessage());
            return null;
        }
    }

    private void pass(String message) {
        System.out.println("[PASS] " + message);
        testsPassed++;
    }

    private void fail(String message) {
        System.out.println("[FAIL] " + message);
        testsFailed++;
        success = false;
    }
}
