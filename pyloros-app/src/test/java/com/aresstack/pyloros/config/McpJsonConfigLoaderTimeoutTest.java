package com.aresstack.pyloros.config;

import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timeouts resolved from {@code mcp.json}.
 *
 * <p>Both timeouts were compiled-in constants, so the 60 s response limit applied to every upstream
 * and could only be changed by rebuilding. That is the wrong granularity for a gateway that fronts
 * both a fast index server and an IDE running Gradle builds, so they became per-server settings with
 * the former constants as fallbacks.
 */
class McpJsonConfigLoaderTimeoutTest {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 60000;

    @TempDir
    Path tempDir;

    @Test
    void perServerTimeoutsOverrideTheDefaults() throws Exception {
        List<McpUpstreamConfig> upstreams = resolve("""
                {
                  "servers": {
                    "intellij": {
                      "type": "sse",
                      "url": "http://127.0.0.1:64342/sse",
                      "connectTimeoutMillis": 2500,
                      "responseTimeoutMillis": 600000
                    }
                  }
                }
                """);

        assertEquals(1, upstreams.size());
        assertEquals(2500, upstreams.get(0).connectTimeoutMillis());
        assertEquals(600000, upstreams.get(0).responseTimeoutMillis());
    }

    @Test
    void absentTimeoutsFallBackToTheDefaults() throws Exception {
        List<McpUpstreamConfig> upstreams = resolve("""
                {
                  "servers": {
                    "intellij": {
                      "type": "sse",
                      "url": "http://127.0.0.1:64342/sse"
                    }
                  }
                }
                """);

        assertEquals(1, upstreams.size());
        assertEquals(DEFAULT_CONNECT_TIMEOUT_MS, upstreams.get(0).connectTimeoutMillis());
        assertEquals(DEFAULT_RESPONSE_TIMEOUT_MS, upstreams.get(0).responseTimeoutMillis());
    }

    @Test
    void nonPositiveTimeoutsFallBackInsteadOfDisablingTheTimeout() throws Exception {
        // A zero response timeout would mean "wait forever" in IdeaSseSession, which is a worse
        // failure than the limit being too short, so it must not be reachable by configuration.
        List<McpUpstreamConfig> upstreams = resolve("""
                {
                  "servers": {
                    "intellij": {
                      "type": "sse",
                      "url": "http://127.0.0.1:64342/sse",
                      "connectTimeoutMillis": 0,
                      "responseTimeoutMillis": -1
                    }
                  }
                }
                """);

        assertEquals(1, upstreams.size());
        assertEquals(DEFAULT_CONNECT_TIMEOUT_MS, upstreams.get(0).connectTimeoutMillis());
        assertEquals(DEFAULT_RESPONSE_TIMEOUT_MS, upstreams.get(0).responseTimeoutMillis());
    }

    @Test
    void timeoutsAreResolvedPerServerNotGlobally() throws Exception {
        List<McpUpstreamConfig> upstreams = resolve("""
                {
                  "servers": {
                    "intellij": {
                      "type": "sse",
                      "url": "http://127.0.0.1:64342/sse",
                      "responseTimeoutMillis": 900000
                    },
                    "index": {
                      "type": "http",
                      "url": "http://127.0.0.1:8080/mcp"
                    }
                  }
                }
                """);

        assertEquals(2, upstreams.size());
        McpUpstreamConfig intellij = byId(upstreams, "intellij");
        McpUpstreamConfig index = byId(upstreams, "index");

        assertEquals(900000, intellij.responseTimeoutMillis());
        assertEquals(DEFAULT_RESPONSE_TIMEOUT_MS, index.responseTimeoutMillis(),
                "a longer limit for the IDE must not leak into unrelated upstreams");
    }

    private List<McpUpstreamConfig> resolve(String mcpJson) throws Exception {
        Path configFile = tempDir.resolve("mcp.json");
        Files.writeString(configFile, mcpJson);

        McpJsonConfigLoader loader = new McpJsonConfigLoader();
        Optional<LoadedMcpJsonConfig> loaded = loader.load(new String[]{"--mcp-config=" + configFile});
        assertTrue(loaded.isPresent(), "the test config has to be found at " + configFile);

        return loader.resolveUpstreams(loaded.get());
    }

    private static McpUpstreamConfig byId(List<McpUpstreamConfig> upstreams, String providerId) {
        return upstreams.stream()
                .filter(upstream -> providerId.equals(upstream.providerId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no upstream for " + providerId));
    }
}
