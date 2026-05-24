package com.aresstack.pyloros.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AcpProviderJsonConfig(
        String id,
        String type,
        String prefix,
        List<String> exposeInViews,
        String agentToolView,
        AcpProcessJsonConfig process,
        AcpExecutionJsonConfig execution
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AcpProcessJsonConfig(
            String command,
            List<String> args,
            String workingDirectory,
            Map<String, String> environment
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AcpExecutionJsonConfig(
            Integer defaultTaskTimeoutSeconds,
            Integer maxEventsPerTask,
            Integer maxEventTextChars,
            Integer maxResultTextChars
    ) {
    }
}
