package com.aresstack.pyloros.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Local smoke test for MCP aggregation.
 * Tests that Pyloros correctly aggregates tools from multiple providers.
 *
 * Run: gradlew.bat :pyloros-app:runMcpAggregationSmokeTest
 *
 * Environment:
 *   PYLOROS_SMOKE_ACCESS_TOKEN  (required) - Bearer token for Pyloros
 *   PYLOROS_SMOKE_MCP_URL       (optional) - default: http://127.0.0.1:8081/sse
 */
public class McpAggregationSmokeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_MCP_URL = "http://127.0.0.1:8081/sse";

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

        // Step 1: List all tools
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

        // Analyze tools by namespace
        List<String> pylorosTools = new ArrayList<>();
        List<String> ideaTools = new ArrayList<>();
        List<String> githubTools = new ArrayList<>();

        for (JsonNode tool : toolsArray) {
            String name = tool.path("name").asText();
            if (name.startsWith("github/")) {
                githubTools.add(name);
            } else if (name.startsWith("idea") || name.startsWith("intellij/")) {
                ideaTools.add(name);
            } else if (name.contains("pyloros")) {
                pylorosTools.add(name);
            }
        }

        int totalTools = toolsArray.size();
        System.out.println("[SMOKE] Total tools: " + totalTools);
        System.out.println("[SMOKE]   Native Pyloros: " + pylorosTools.size() + " tools");
        System.out.println("[SMOKE]   IntelliJ: " + ideaTools.size() + " tools");
        System.out.println("[SMOKE]   GitHub: " + githubTools.size() + " tools");

        // Step 2: Validate required tools
        System.out.println();
        System.out.println("[SMOKE] Step 2: Validating required tools...");

        boolean hasPyloros = pylorosTools.stream().anyMatch(t -> t.equals("pyloros__ping"));
        boolean hasIdea = ideaTools.stream().anyMatch(t -> t.equals("idea__get_project_modules") || t.equals("intellij/get_project_modules"));
        boolean hasGithub = !githubTools.isEmpty();

        if (hasPyloros) {
            pass("pyloros__ping found");
        } else {
            fail("pyloros__ping NOT found");
        }

        if (hasIdea) {
            pass("intellij/get_project_modules found");
        } else {
            fail("intellij/get_project_modules NOT found");
        }

        if (hasGithub) {
            pass("GitHub tools found: " + githubTools.size());
        } else {
            fail("No GitHub tools found (GitHub provider may be disabled)");
        }

        // Step 3: Test pyloros__ping call
        System.out.println();
        System.out.println("[SMOKE] Step 3: Testing pyloros__ping call...");
        JsonNode pingResponse = rpcCallTool("pyloros__ping", "{}");
        if (pingResponse != null) {
            pass("pyloros__ping call succeeded");
        } else {
            fail("pyloros__ping call failed");
        }

        // Step 4: Test intellij/get_project_modules call
        System.out.println();
        System.out.println("[SMOKE] Step 4: Testing intellij/get_project_modules call...");
        String ideaTool = selectIntellijTool(ideaTools);
        JsonNode ideaResponse = rpcCallTool(ideaTool, "{}");
        if (ideaResponse != null) {
            pass(ideaTool + " call succeeded");
        } else {
            fail(ideaTool + " call failed");
        }

        // Step 5: Test GitHub tool call
        System.out.println();
        System.out.println("[SMOKE] Step 5: Testing GitHub tool call...");
        if (!githubTools.isEmpty()) {
            String selectedGithubTool = selectReadOnlyGithubTool(githubTools);
            System.out.println("[SMOKE] Selected GitHub tool: " + selectedGithubTool);

            JsonNode githubResponse = rpcCallTool(selectedGithubTool, buildGithubToolArguments(selectedGithubTool));
            if (githubResponse != null) {
                pass(selectedGithubTool + " call succeeded");
            } else {
                fail(selectedGithubTool + " call failed");
            }
        } else {
            System.out.println("[WARN] No GitHub tools available to test (provider may be disabled)");
        }

        // Summary
        System.out.println();
        System.out.println("[SMOKE] ========== SUMMARY ==========");
        System.out.println("[SMOKE] Total tools: " + totalTools);
        System.out.println("[SMOKE] GitHub tools: " + githubTools.size());
        System.out.println("[SMOKE] Tests passed: " + testsPassed);
        System.out.println("[SMOKE] Tests failed: " + testsFailed);
        System.out.println("[SMOKE] Result: " + (success ? "SUCCESS" : "FAILED"));
    }

    private String selectReadOnlyGithubTool(List<String> availableTools) {
        // Prefer safe read-only tools
        String[] safeTools = {
            "github/get_me",
            "github/search_repositories",
            "github/get_file_contents",
            "github/list_branches",
            "github/list_issues",
            "github/search_issues",
            "github/search_pull_requests",
            "github/search_code"
        };

        for (String safe : safeTools) {
            if (availableTools.contains(safe)) {
                return safe;
            }
        }

        // Fall back to first available
        return availableTools.get(0);
    }

    private String selectIntellijTool(List<String> availableTools) {
        String[] preferred = {
            "intellij/get_project_modules",
            "idea__get_project_modules"
        };

        for (String tool : preferred) {
            if (availableTools.contains(tool)) {
                return tool;
            }
        }

        return availableTools.isEmpty() ? "intellij/get_project_modules" : availableTools.get(0);
    }

    private String buildGithubToolArguments(String toolName) {
        return switch (toolName) {
            case "github/get_me" -> "{}";
            case "github/search_repositories" -> "{\"query\":\"topic:mcp\"}";
            case "github/list_branches" -> "{\"owner\":\"github\",\"repo\":\"github-mcp-server\"}";
            case "github/get_file_contents" -> "{\"owner\":\"github\",\"repo\":\"github-mcp-server\",\"path\":\"README.md\"}";
            case "github/list_issues" -> "{\"owner\":\"github\",\"repo\":\"github-mcp-server\"}";
            case "github/search_issues" -> "{\"query\":\"repo:github/github-mcp-server is:issue\"}";
            case "github/search_pull_requests" -> "{\"query\":\"repo:github/github-mcp-server is:pr\"}";
            case "github/search_code" -> "{\"query\":\"mcp in:file language:json repo:github/github-mcp-server\"}";
            default -> "{}";
        };
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
