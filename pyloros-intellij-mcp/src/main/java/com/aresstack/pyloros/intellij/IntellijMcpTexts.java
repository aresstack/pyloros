package com.aresstack.pyloros.intellij;

final class IntellijMcpTexts {

    static final String RECOMMENDED_SETUP = String.join("\n",
            "# Recommended IntelliJ MCP Setup for Pyloros",
            "",
            "Install these IntelliJ plugins:",
            "",
            "- MCP Steroid: https://plugins.jetbrains.com/plugin/30019-mcp-steroid",
            "- IDE Index MCP Server: https://plugins.jetbrains.com/plugin/29174-ide-index-mcp-server",
            "- MCP Server AI Companion: https://plugins.jetbrains.com/plugin/31060-mcp-server-ai-companion",
            "",
            "Also enable the built-in IntelliJ MCP Server in IntelliJ Settings. The AI Companion plugin can use that built-in server, but it must be enabled first.",
            "",
            "Recommended MCP servers:",
            "",
            "- github: GitHub/Copilot MCP endpoint for repository, issues, pull requests, and code search.",
            "- intellij: built-in IntelliJ MCP Server at http://localhost:64343/sse.",
            "- intellij-index: IDE Index MCP Server at http://127.0.0.1:29170/index-mcp/streamable-http.",
            "- mcp-steroid: MCP Steroid endpoint at http://localhost:6315/mcp.",
            "",
            "Keep secrets local. Do not commit tokens. Use environment variables or an ignored local config file."
    );

    static final String CONFIG_TEMPLATE = String.join("\n",
            "{",
            "  \"servers\": {",
            "    \"github\": {",
            "      \"type\": \"http\",",
            "      \"url\": \"https://api.githubcopilot.com/mcp/\",",
            "      \"requestInit\": {",
            "        \"headers\": {",
            "          \"Authorization\": \"Bearer ${GITHUB_MCP_TOKEN}\"",
            "        }",
            "      }",
            "    },",
            "    \"intellij\": {",
            "      \"type\": \"sse\",",
            "      \"url\": \"http://localhost:64343/sse\",",
            "      \"headers\": {",
            "        \"IJ_MCP_SERVER_PROJECT_PATH\": \"C:/Projects/pyloros\"",
            "      }",
            "    },",
            "    \"intellij-index\": {",
            "      \"url\": \"http://127.0.0.1:29170/index-mcp/streamable-http\"",
            "    },",
            "    \"mcp-steroid\": {",
            "      \"type\": \"http\",",
            "      \"url\": \"http://localhost:6315/mcp\"",
            "    }",
            "  }",
            "}"
    );

    private IntellijMcpTexts() {
    }
}
