package com.aresstack.pyloros.config;

import com.aresstack.pyloros.upstream.mcp.McpUpstreamConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class McpJsonConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(McpJsonConfigLoader.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_RESPONSE_TIMEOUT_MS = 60000;

    public Optional<LoadedMcpJsonConfig> load(String[] args) {
        for (Path candidate : candidatePaths(args)) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                try {
                    McpJsonConfig config = JSON.readValue(candidate.toFile(), McpJsonConfig.class);
                    return Optional.of(new LoadedMcpJsonConfig(candidate.toAbsolutePath().normalize(), config));
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to load mcp.json from " + candidate, exception);
                }
            }
        }
        return Optional.empty();
    }

    public List<McpUpstreamConfig> resolveUpstreams(LoadedMcpJsonConfig loaded) {
        if (loaded == null || loaded.config() == null || loaded.config().servers() == null) {
            return List.of();
        }

        LinkedHashMap<String, McpUpstreamConfig> configs = new LinkedHashMap<>();
        for (Map.Entry<String, McpServerConfig> entry : loaded.config().servers().entrySet()) {
            String providerId = entry.getKey();
            McpServerConfig server = entry.getValue();
            McpUpstreamConfig resolved = resolveServer(providerId, server, loaded.path());
            if (resolved == null) {
                continue;
            }
            McpUpstreamConfig existing = configs.putIfAbsent(providerId, resolved);
            if (existing != null) {
                throw new IllegalStateException("Duplicate provider id in mcp.json: " + providerId);
            }
        }
        return List.copyOf(configs.values());
    }

    private McpUpstreamConfig resolveServer(String providerId, McpServerConfig server, Path sourcePath) {
        if (providerId == null || providerId.isBlank() || server == null || server.url() == null) {
            if (providerId != null && !providerId.isBlank()) {
                log.info("[MCP-CONFIG] skipping provider={} reason=missing-url-or-definition source={}", providerId, sourcePath);
            }
            return null;
        }

        String transport = normalizeTransport(server.type(), server.url().getScheme());
        if (transport == null) {
            log.info("[MCP-CONFIG] skipping provider={} reason=unsupported-transport type={} source={}", providerId, server.type(), sourcePath);
            return null;
        }

        return new McpUpstreamConfig(
                providerId,
                transport,
                server.url(),
                server.resolvedHeaders(),
                DEFAULT_CONNECT_TIMEOUT_MS,
                DEFAULT_RESPONSE_TIMEOUT_MS,
                sourcePath.toString()
        );
    }

    private String normalizeTransport(String type, String urlScheme) {
        String normalizedType = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if ("sse".equals(normalizedType)) {
            return "sse";
        }
        if ("http".equals(normalizedType) || "streamable-http".equals(normalizedType)) {
            return "streamable-http";
        }
        if (normalizedType.isBlank() && ("http".equalsIgnoreCase(urlScheme) || "https".equalsIgnoreCase(urlScheme))) {
            return "streamable-http";
        }
        return null;
    }

    private List<Path> candidatePaths(String[] args) {
        ArrayList<Path> paths = new ArrayList<>();

        Path fromArgs = fromArgs(args);
        if (fromArgs != null) {
            paths.add(fromArgs);
        }

        Path fromSystemProperty = fromSystemProperty();
        if (fromSystemProperty != null && !paths.contains(fromSystemProperty)) {
            paths.add(fromSystemProperty);
        }

        Path fromLocalAppData = fromLocalAppData();
        if (fromLocalAppData != null && !paths.contains(fromLocalAppData)) {
            paths.add(fromLocalAppData);
        }

        Path cwdMcp = Paths.get("mcp.json").toAbsolutePath().normalize();
        if (!paths.contains(cwdMcp)) {
            paths.add(cwdMcp);
        }

        Path dataMcp = Paths.get("data", "mcp.json").toAbsolutePath().normalize();
        if (!paths.contains(dataMcp)) {
            paths.add(dataMcp);
        }

        return List.copyOf(paths);
    }

    private Path fromArgs(String[] args) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith("--mcp-config=")) {
                String value = arg.substring("--mcp-config=".length()).trim();
                if (!value.isBlank()) {
                    return Paths.get(value).toAbsolutePath().normalize();
                }
            }
        }
        return null;
    }

    private Path fromSystemProperty() {
        String value = System.getProperty("mcp.config");
        if (value == null || value.isBlank()) {
            return null;
        }
        return Paths.get(value).toAbsolutePath().normalize();
    }

    private Path fromLocalAppData() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return null;
        }
        return Paths.get(localAppData, "github-copilot", "intellij", "mcp.json").toAbsolutePath().normalize();
    }
}
