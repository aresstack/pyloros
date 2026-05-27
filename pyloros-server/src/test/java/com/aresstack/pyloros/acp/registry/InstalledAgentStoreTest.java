package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstalledAgentStoreTest {

    @TempDir
    Path tempDir;

    private InstalledAgentStore store;

    @BeforeEach
    void setUp() {
        store = new InstalledAgentStore(tempDir.resolve("data/acp-registry"));
    }

    @Test
    void emptyStoreReturnsEmptyList() {
        List<InstalledAgent> agents = store.listAll();
        assertTrue(agents.isEmpty());
    }

    @Test
    void emptyStoreFindByIdReturnsEmpty() {
        Optional<InstalledAgent> result = store.findById("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void addInstalledAgent() {
        InstalledAgent agent = createAgent("test-agent", "1.0.0", true);

        InstalledAgent saved = store.save(agent);

        assertEquals("test-agent", saved.agentId());
        assertEquals("1.0.0", saved.installedVersion());
        assertTrue(saved.enabled());

        List<InstalledAgent> allAgents = store.listAll();
        assertEquals(1, allAgents.size());
        assertEquals("test-agent", allAgents.get(0).agentId());
    }

    @Test
    void addMultipleAgents() {
        store.save(createAgent("agent-a", "1.0.0", true));
        store.save(createAgent("agent-b", "2.0.0", true));

        List<InstalledAgent> allAgents = store.listAll();
        assertEquals(2, allAgents.size());
    }

    @Test
    void updateInstalledAgent() {
        store.save(createAgent("test-agent", "1.0.0", true));

        InstalledAgent updated = createAgent("test-agent", "2.0.0", true);
        store.save(updated);

        List<InstalledAgent> allAgents = store.listAll();
        assertEquals(1, allAgents.size());
        assertEquals("2.0.0", allAgents.get(0).installedVersion());
    }

    @Test
    void disableAgent() {
        store.save(createAgent("test-agent", "1.0.0", true));

        Optional<InstalledAgent> result = store.setEnabled("test-agent", false);

        assertTrue(result.isPresent());
        assertFalse(result.get().enabled());

        // Agent still exists in store
        List<InstalledAgent> allAgents = store.listAll();
        assertEquals(1, allAgents.size());
        assertFalse(allAgents.get(0).enabled());
    }

    @Test
    void enableAgent() {
        store.save(createAgent("test-agent", "1.0.0", false));

        Optional<InstalledAgent> result = store.setEnabled("test-agent", true);

        assertTrue(result.isPresent());
        assertTrue(result.get().enabled());
    }

    @Test
    void disabledAgentNotInEnabledList() {
        store.save(createAgent("enabled-agent", "1.0.0", true));
        store.save(createAgent("disabled-agent", "1.0.0", false));

        List<InstalledAgent> enabledAgents = store.listEnabled();
        assertEquals(1, enabledAgents.size());
        assertEquals("enabled-agent", enabledAgents.get(0).agentId());
    }

    @Test
    void disableNonExistentAgentReturnsEmpty() {
        Optional<InstalledAgent> result = store.setEnabled("nonexistent", false);
        assertTrue(result.isEmpty());
    }

    @Test
    void corruptedStoreThrowsStructuredException() throws IOException {
        Path storeDir = tempDir.resolve("data/acp-registry");
        Files.createDirectories(storeDir);
        Files.writeString(storeDir.resolve("installed-agents.json"), "not valid json {{{");

        InstalledAgentStoreException ex = assertThrows(
                InstalledAgentStoreException.class, () -> store.listAll());
        assertEquals(InstalledAgentStoreException.Kind.CORRUPTED, ex.kind());
    }

    @Test
    void persistenceAcrossInstances() {
        store.save(createAgent("test-agent", "1.0.0", true));

        // Create a new store instance pointing to the same directory
        InstalledAgentStore newStore = new InstalledAgentStore(tempDir.resolve("data/acp-registry"));
        List<InstalledAgent> agents = newStore.listAll();

        assertEquals(1, agents.size());
        assertEquals("test-agent", agents.get(0).agentId());
    }

    @Test
    void noSecretLeakageInPersistedFields() throws IOException {
        InstalledAgent agent = createAgent("my-agent", "1.0.0", true);
        store.save(agent);

        String fileContent = Files.readString(store.storeFile());

        // Verify no secret/sensitive patterns leak in persisted data
        assertFalse(fileContent.contains("password"));
        assertFalse(fileContent.contains("token"));
        assertFalse(fileContent.contains("secret"));
        // Verify expected non-secret fields are present
        assertTrue(fileContent.contains("my-agent"));
        assertTrue(fileContent.contains("1.0.0"));
        assertTrue(fileContent.contains("npx"));
    }

    @Test
    void corruptedEntrySkippedOthersPreserved() throws IOException {
        // Write a document with one valid and one corrupted entry
        String json = """
                {
                  "version": 1,
                  "agents": [
                    {
                      "agentId": "valid-agent",
                      "installedVersion": "1.0.0",
                      "distributionType": "npx",
                      "resolvedCommand": "npx",
                      "resolvedArgs": [],
                      "installPath": "/install",
                      "sourceRegistryVersion": "v1",
                      "license": "MIT",
                      "configuredPrefix": "valid/",
                      "agentToolView": "agent",
                      "enabled": true,
                      "installedAt": "2024-01-01T00:00:00Z",
                      "updatedAt": "2024-01-01T00:00:00Z"
                    },
                    {
                      "agentId": "",
                      "installedVersion": "",
                      "distributionType": "",
                      "resolvedCommand": "",
                      "resolvedArgs": [],
                      "installPath": "",
                      "sourceRegistryVersion": "",
                      "license": "",
                      "configuredPrefix": "",
                      "agentToolView": "",
                      "enabled": true,
                      "installedAt": "",
                      "updatedAt": ""
                    }
                  ]
                }
                """;

        Path storeDir = tempDir.resolve("data/acp-registry");
        Files.createDirectories(storeDir);
        Files.writeString(storeDir.resolve("installed-agents.json"), json);

        List<InstalledAgent> agents = store.listAll();
        assertEquals(1, agents.size());
        assertEquals("valid-agent", agents.get(0).agentId());
    }

    @Test
    void storeFileCreatedInExpectedLocation() {
        store.save(createAgent("test-agent", "1.0.0", true));
        assertTrue(Files.exists(store.storeFile()));
        assertTrue(store.storeFile().toString().contains("acp-registry"));
    }

    private static InstalledAgent createAgent(String agentId, String version, boolean enabled) {
        Instant now = Instant.now();
        return new InstalledAgent(
                agentId,
                version,
                "npx",
                "npx",
                List.of("--yes", "@acme/" + agentId),
                "/install/" + agentId,
                "v1",
                "MIT",
                agentId + "/",
                "agent",
                enabled,
                now,
                now
        );
    }
}
