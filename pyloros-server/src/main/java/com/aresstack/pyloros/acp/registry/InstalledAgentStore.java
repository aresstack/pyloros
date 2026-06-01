package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads and writes the installed agents store ({@code data/installed-agents.json}).
 * Thread-safe: reads/writes are synchronized on the store instance.
 */
public final class InstalledAgentStore {

    private static final Logger log = LoggerFactory.getLogger(InstalledAgentStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storePath;
    private Map<String, InstalledAgent> agents;

    public InstalledAgentStore(Path storePath) {
        this.storePath = storePath;
        this.agents = load();
    }

    public synchronized List<InstalledAgent> listAll() {
        return List.copyOf(agents.values());
    }

    public synchronized Optional<InstalledAgent> findById(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    public synchronized void save(InstalledAgent agent) {
        agents.put(agent.id(), agent);
        persist();
    }

    public synchronized boolean remove(String agentId) {
        InstalledAgent removed = agents.remove(agentId);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    private Map<String, InstalledAgent> load() {
        if (!Files.exists(storePath)) {
            return new LinkedHashMap<>();
        }
        try {
            List<InstalledAgent> loaded = MAPPER.readValue(
                    storePath.toFile(),
                    new TypeReference<List<InstalledAgent>>() {});
            Map<String, InstalledAgent> map = new LinkedHashMap<>();
            for (InstalledAgent agent : loaded) {
                map.put(agent.id(), agent);
            }
            log.info("[ACP-REGISTRY] loaded {} installed agents from {}", map.size(), storePath);
            return map;
        } catch (IOException e) {
            log.error("[ACP-REGISTRY] failed to read installed-agents store path={}", storePath, e);
            return new LinkedHashMap<>();
        }
    }

    private void persist() {
        try {
            Files.createDirectories(storePath.getParent());
            MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValue(storePath.toFile(), List.copyOf(agents.values()));
        } catch (IOException e) {
            log.error("[ACP-REGISTRY] failed to persist installed-agents store path={}", storePath, e);
        }
    }
}
