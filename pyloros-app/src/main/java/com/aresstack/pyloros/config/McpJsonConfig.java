package com.aresstack.pyloros.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McpJsonConfig(
        Map<String, McpServerConfig> servers,
        List<AcpProviderJsonConfig> acpProviders
) {
}
