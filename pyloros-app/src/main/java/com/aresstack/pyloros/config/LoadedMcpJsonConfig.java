package com.aresstack.pyloros.config;

import java.nio.file.Path;

public record LoadedMcpJsonConfig(
        Path path,
        McpJsonConfig config
) {
}
