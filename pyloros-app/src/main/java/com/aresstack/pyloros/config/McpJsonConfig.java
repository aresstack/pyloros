package com.aresstack.pyloros.config;

import java.util.Map;

public record McpJsonConfig(
        Map<String, McpServerConfig> servers
) {
}
