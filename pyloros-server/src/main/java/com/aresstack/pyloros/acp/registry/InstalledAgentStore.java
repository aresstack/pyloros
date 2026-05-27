package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persistent store for installed ACP registry agents.
 * Keeps installation state separate from the registry cache.
 * Uses atomic file writes to prevent corruption.
 */
public class InstalledAgentStore {

    private static final Logger log = LoggerFactory.getLogger(InstalledAgentStore.class);
    private static final String STORE_FILE_NAME = "installed-agents.json";

    private final ObjectMapper objectMapper;
    private final Path storeFile;

    public InstalledAgentStore(Path baseDirectory) {
        Objects.requireNonNull(baseDirectory, "baseDirectory must not be null");
        this.storeFile = baseDirectory.resolve(STORE_FILE_NAME);
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path storeFile() {
        return storeFile;
    }

    /**
     * Lists all installed agents.
     */
    public synchronized List<InstalledAgent> listAll() {
        InstalledAgentStoreDocument document = readDocument();
        List<InstalledAgent> result = new ArrayList<>();
        for (InstalledAgentStoreDocument.InstalledAgentEntry entry : document.agents()) {
            try {
                result.add(toInstalledAgent(entry));
            } catch (Exception e) {
                log.warn("Skipping corrupted agent entry '{}': {}", sanitize(entry.agentId()), e.getMessage());
            }
        }
        return List.copyOf(result);
    }

    /**
     * Lists only enabled installed agents.
     */
    public synchronized List<InstalledAgent> listEnabled() {
        return listAll().stream().filter(InstalledAgent::enabled).toList();
    }

    /**
     * Finds an installed agent by its ID.
     */
    public synchronized Optional<InstalledAgent> findById(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        return listAll().stream()
                .filter(a -> a.agentId().equals(agentId.trim()))
                .findFirst();
    }

    /**
     * Installs or updates an agent in the store.
     */
    public synchronized InstalledAgent save(InstalledAgent agent) {
        Objects.requireNonNull(agent, "agent must not be null");

        InstalledAgentStoreDocument document = readDocument();
        List<InstalledAgentStoreDocument.InstalledAgentEntry> updatedEntries = new ArrayList<>();
        boolean found = false;

        for (InstalledAgentStoreDocument.InstalledAgentEntry entry : document.agents()) {
            if (entry.agentId() != null && entry.agentId().equals(agent.agentId())) {
                updatedEntries.add(toEntry(agent));
                found = true;
            } else {
                updatedEntries.add(entry);
            }
        }

        if (!found) {
            updatedEntries.add(toEntry(agent));
        }

        writeDocument(new InstalledAgentStoreDocument(
                InstalledAgentStoreDocument.CURRENT_VERSION, updatedEntries));

        log.info("Saved installed agent '{}'", sanitize(agent.agentId()));
        return agent;
    }

    /**
     * Removes an installed agent by its ID.
     * Returns the removed agent, or empty if not found.
     */
    public synchronized Optional<InstalledAgent> remove(String agentId) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        String trimmedId = agentId.trim();
        Optional<InstalledAgent> existing = findById(trimmedId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        InstalledAgentStoreDocument document = readDocument();
        List<InstalledAgentStoreDocument.InstalledAgentEntry> updatedEntries = new ArrayList<>();
        for (InstalledAgentStoreDocument.InstalledAgentEntry entry : document.agents()) {
            if (entry.agentId() != null && entry.agentId().equals(trimmedId)) {
                continue;
            }
            updatedEntries.add(entry);
        }

        writeDocument(new InstalledAgentStoreDocument(
                InstalledAgentStoreDocument.CURRENT_VERSION, updatedEntries));

        log.info("Removed installed agent '{}'", sanitize(trimmedId));
        return existing;
    }

    /**
     * Enables or disables an agent by ID.
     * Returns the updated agent, or empty if not found.
     */
    public synchronized Optional<InstalledAgent> setEnabled(String agentId, boolean enabled) {
        Objects.requireNonNull(agentId, "agentId must not be null");
        Optional<InstalledAgent> existing = findById(agentId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        InstalledAgent updated = existing.get().withEnabled(enabled);
        save(updated);
        return Optional.of(updated);
    }

    private InstalledAgentStoreDocument readDocument() {
        if (!Files.exists(storeFile)) {
            return new InstalledAgentStoreDocument();
        }

        try {
            InstalledAgentStoreDocument document = objectMapper.readValue(
                    storeFile.toFile(), InstalledAgentStoreDocument.class);
            if (document == null) {
                return new InstalledAgentStoreDocument();
            }
            if (document.version() > InstalledAgentStoreDocument.CURRENT_VERSION) {
                log.warn("Store file version {} is newer than supported version {}",
                        document.version(), InstalledAgentStoreDocument.CURRENT_VERSION);
            }
            return document;
        } catch (IOException e) {
            throw new InstalledAgentStoreException(
                    InstalledAgentStoreException.Kind.CORRUPTED,
                    "Could not read installed agent store: " + storeFile, e);
        }
    }

    private void writeDocument(InstalledAgentStoreDocument document) {
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = storeFile.resolveSibling(storeFile.getFileName() + ".tmp");
            objectMapper.writeValue(tempFile.toFile(), document);
            try {
                Files.move(tempFile, storeFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception moveEx) {
                log.debug("Atomic move not supported, falling back to regular move: {}", moveEx.getMessage());
                Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new InstalledAgentStoreException(
                    InstalledAgentStoreException.Kind.IO_ERROR,
                    "Could not write installed agent store: " + storeFile, e);
        }
    }

    private static InstalledAgent toInstalledAgent(InstalledAgentStoreDocument.InstalledAgentEntry entry) {
        return new InstalledAgent(
                entry.agentId(),
                entry.installedVersion(),
                entry.distributionType(),
                entry.resolvedCommand(),
                entry.resolvedArgs(),
                entry.installPath(),
                entry.sourceRegistryVersion(),
                entry.license(),
                entry.configuredPrefix(),
                entry.agentToolView(),
                entry.enabled(),
                parseInstant(entry.installedAt(), "installedAt"),
                parseInstant(entry.updatedAt(), "updatedAt")
        );
    }

    private static InstalledAgentStoreDocument.InstalledAgentEntry toEntry(InstalledAgent agent) {
        return new InstalledAgentStoreDocument.InstalledAgentEntry(
                agent.agentId(),
                agent.installedVersion(),
                agent.distributionType(),
                agent.resolvedCommand(),
                agent.resolvedArgs(),
                agent.installPath(),
                agent.sourceRegistryVersion(),
                agent.license(),
                agent.configuredPrefix(),
                agent.agentToolView(),
                agent.enabled(),
                agent.installedAt().toString(),
                agent.updatedAt().toString()
        );
    }

    private static Instant parseInstant(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InstalledAgentStoreException(
                    InstalledAgentStoreException.Kind.VALIDATION_ERROR,
                    fieldName + " is missing or blank");
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new InstalledAgentStoreException(
                    InstalledAgentStoreException.Kind.VALIDATION_ERROR,
                    fieldName + " is not a valid ISO instant: " + value, e);
        }
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replaceAll("[^a-zA-Z0-9._\\-/]", "_");
    }
}
