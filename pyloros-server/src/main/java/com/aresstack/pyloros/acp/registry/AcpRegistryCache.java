package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Local file cache for the last successfully loaded ACP registry.
 */
public final class AcpRegistryCache {

    private static final Logger log = LoggerFactory.getLogger(AcpRegistryCache.class);

    private final ObjectMapper objectMapper;
    private final Path cacheFile;

    public AcpRegistryCache(Path cacheFile) {
        this.cacheFile = cacheFile;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path cacheFile() {
        return cacheFile;
    }

    /**
     * Writes a valid registry to the cache file.
     */
    public void write(AcpRegistry registry) {
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(cacheFile.toFile(), registry);
            log.debug("ACP registry cache written to {}", cacheFile);
        } catch (IOException e) {
            log.warn("Failed to write ACP registry cache to {}: {}", cacheFile, e.getMessage());
        }
    }

    /**
     * Reads the cached registry, if available and valid.
     */
    public Optional<AcpRegistry> read() {
        if (!Files.exists(cacheFile)) {
            return Optional.empty();
        }
        try {
            AcpRegistry registry = objectMapper.readValue(cacheFile.toFile(), AcpRegistry.class);
            if (registry == null || registry.agents() == null) {
                return Optional.empty();
            }
            return Optional.of(registry);
        } catch (IOException e) {
            log.warn("Failed to read ACP registry cache from {}: {}", cacheFile, e.getMessage());
            return Optional.empty();
        }
    }
}
