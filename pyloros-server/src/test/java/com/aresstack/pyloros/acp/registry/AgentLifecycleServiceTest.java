package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AgentLifecycleServiceTest {

    private static final TargetPlatform LINUX_X86_64 = new TargetPlatform("linux", "x86_64");

    @TempDir
    Path tempDir;

    private InstalledAgentStore store;
    private AgentLifecycleService service;

    @BeforeEach
    void setUp() {
        store = new InstalledAgentStore(tempDir.resolve("data/acp-registry"));
        DistributionResolver resolver = new DistributionResolver(LINUX_X86_64);
        service = new AgentLifecycleService(store, resolver);
    }

    // --- Install tests ---

    @Test
    void installNpxAgent() {
        RegistryAgent agent = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentLifecycleResult.Action.INSTALLED, success.action());
        assertEquals("test-agent", success.agent().agentId());
        assertEquals("1.0.0", success.agent().installedVersion());
        assertEquals("npx", success.agent().distributionType());
        assertEquals("npx", success.agent().resolvedCommand());
        assertTrue(success.agent().resolvedArgs().contains("test-agent-acp@1.0.0"));
        assertTrue(success.agent().enabled());
        assertNotNull(success.agent().installedAt());
        assertNotNull(success.agent().updatedAt());

        // Verify persistence
        Optional<InstalledAgent> stored = store.findById("test-agent");
        assertTrue(stored.isPresent());
        assertEquals("1.0.0", stored.get().installedVersion());
    }

    @Test
    void installUvxAgent() {
        RegistryAgent agent = uvxRegistryAgent("python-agent", "2.0.0", "python-agent-acp==2.0.0");

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals("python-agent", success.agent().agentId());
        assertEquals("uvx", success.agent().distributionType());
        assertEquals("uvx", success.agent().resolvedCommand());
        assertTrue(success.agent().resolvedArgs().contains("python-agent-acp==2.0.0"));
    }

    @Test
    void installBinaryAgent() {
        RegistryAgent agent = binaryRegistryAgent("goose", "1.35.0",
                Map.of("linux-x86_64", new RegistryDistribution.BinaryTarget(
                        "https://example.com/goose-linux.tar.bz2", "./goose", List.of("acp"), null)));

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals("goose", success.agent().agentId());
        assertEquals("binary", success.agent().distributionType());
        assertEquals("./goose", success.agent().resolvedCommand());
        assertEquals(List.of("acp"), success.agent().resolvedArgs());
    }

    @Test
    void reinstallExistingAgentIsRejected() {
        RegistryAgent agent = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(agent);

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals("test-agent", rejected.agentId());
        assertEquals(AgentLifecycleResult.RejectionKind.ALREADY_INSTALLED, rejected.kind());
        assertTrue(rejected.reason().contains("already installed"));
    }

    // --- Update tests ---

    @Test
    void updateToNewerVersion() {
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        RegistryAgent v2 = npxRegistryAgent("test-agent", "2.0.0", "test-agent-acp@2.0.0");
        AgentLifecycleResult result = service.update(v2);

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentLifecycleResult.Action.UPDATED, success.action());
        assertEquals("2.0.0", success.agent().installedVersion());
        assertTrue(success.message().contains("1.0.0"));
        assertTrue(success.message().contains("2.0.0"));

        // Verify the stored version is updated
        Optional<InstalledAgent> stored = store.findById("test-agent");
        assertTrue(stored.isPresent());
        assertEquals("2.0.0", stored.get().installedVersion());
    }

    @Test
    void updatePreservesConfiguredPrefixAndAgentToolView() {
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        // Verify default prefix/view
        InstalledAgent installed = store.findById("test-agent").orElseThrow();
        assertEquals("test-agent/", installed.configuredPrefix());
        assertEquals("agent", installed.agentToolView());

        RegistryAgent v2 = npxRegistryAgent("test-agent", "2.0.0", "test-agent-acp@2.0.0");
        service.update(v2);

        InstalledAgent updated = store.findById("test-agent").orElseThrow();
        assertEquals("test-agent/", updated.configuredPrefix());
        assertEquals("agent", updated.agentToolView());
    }

    @Test
    void updateNotInstalledAgentIsRejected() {
        RegistryAgent agent = npxRegistryAgent("unknown-agent", "1.0.0", "unknown@1.0.0");

        AgentLifecycleResult result = service.update(agent);

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals(AgentLifecycleResult.RejectionKind.NOT_INSTALLED, rejected.kind());
    }

    @Test
    void updateWithSameVersionIsRejected() {
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        AgentLifecycleResult result = service.update(v1);

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals(AgentLifecycleResult.RejectionKind.ALREADY_UP_TO_DATE, rejected.kind());
        assertTrue(rejected.reason().contains("1.0.0"));
    }

    @Test
    void rollbackOnFailedUpdate() {
        // Install the agent normally
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        // Use a store that fails on the second save (the update attempt)
        // but succeeds on the third save (the rollback)
        InstalledAgentStore failingStore = new FailOnSecondSaveStore(
                tempDir.resolve("data/acp-registry"));
        // Pre-populate with the installed agent
        InstalledAgent installed = store.findById("test-agent").orElseThrow();
        failingStore.save(installed);

        AgentLifecycleService failingService = new AgentLifecycleService(
                failingStore, new DistributionResolver(LINUX_X86_64));

        RegistryAgent v2 = npxRegistryAgent("test-agent", "2.0.0", "test-agent-acp@2.0.0");
        AgentLifecycleResult result = failingService.update(v2);

        assertInstanceOf(AgentLifecycleResult.Failed.class, result);
        var failed = (AgentLifecycleResult.Failed) result;
        assertEquals("test-agent", failed.agentId());
        assertTrue(failed.rolledBack());
        assertTrue(failed.rollbackDetail().contains("1.0.0"));

        // Verify previous version is still in the store (rollback succeeded)
        Optional<InstalledAgent> restored = failingStore.findById("test-agent");
        assertTrue(restored.isPresent());
        assertEquals("1.0.0", restored.get().installedVersion());
    }

    // --- Uninstall tests ---

    @Test
    void uninstallInstalledAgent() {
        RegistryAgent agent = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(agent);

        AgentLifecycleResult result = service.uninstall("test-agent");

        assertInstanceOf(AgentLifecycleResult.Success.class, result);
        var success = (AgentLifecycleResult.Success) result;
        assertEquals(AgentLifecycleResult.Action.UNINSTALLED, success.action());
        assertEquals("test-agent", success.agent().agentId());
        assertTrue(success.message().contains("uninstalled"));

        // Verify removal from store
        Optional<InstalledAgent> stored = store.findById("test-agent");
        assertTrue(stored.isEmpty());
    }

    @Test
    void uninstallMissingAgentReturnsStructuredError() {
        AgentLifecycleResult result = service.uninstall("nonexistent-agent");

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals("nonexistent-agent", rejected.agentId());
        assertEquals(AgentLifecycleResult.RejectionKind.NOT_INSTALLED, rejected.kind());
        assertTrue(rejected.reason().contains("not installed"));
    }

    @Test
    void uninstallDisablesProvider() {
        RegistryAgent agent = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(agent);

        service.uninstall("test-agent");

        // After uninstall, agent is completely removed (no longer even disabled)
        assertTrue(store.listAll().isEmpty());
        assertTrue(store.listEnabled().isEmpty());
    }

    // --- Update detection tests ---

    @Test
    void isUpdateAvailableDetectsNewerVersion() {
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        RegistryAgent v2 = npxRegistryAgent("test-agent", "2.0.0", "test-agent-acp@2.0.0");
        assertTrue(service.isUpdateAvailable("test-agent", v2));
    }

    @Test
    void isUpdateAvailableReturnsFalseWhenSameVersion() {
        RegistryAgent v1 = npxRegistryAgent("test-agent", "1.0.0", "test-agent-acp@1.0.0");
        service.install(v1);

        assertFalse(service.isUpdateAvailable("test-agent", v1));
    }

    @Test
    void isUpdateAvailableReturnsFalseWhenNotInstalled() {
        RegistryAgent agent = npxRegistryAgent("unknown", "1.0.0", "unknown@1.0.0");
        assertFalse(service.isUpdateAvailable("unknown", agent));
    }

    // --- Edge cases ---

    @Test
    void installAgentWithUnsupportedPlatformBinaryIsRejected() {
        RegistryAgent agent = binaryRegistryAgent("mac-only-agent", "1.0.0",
                Map.of("darwin-aarch64", new RegistryDistribution.BinaryTarget(
                        "https://example.com/agent-darwin.tar.gz", "./agent", List.of(), null)));

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals(AgentLifecycleResult.RejectionKind.UNSUPPORTED_PLATFORM, rejected.kind());
    }

    @Test
    void installAgentWithNoDistributionIsRejected() {
        RegistryAgent agent = new RegistryAgent(
                "no-dist", "No Distribution", "1.0.0", "An agent without distribution",
                null, null, null, "MIT", null, null);

        AgentLifecycleResult result = service.install(agent);

        assertInstanceOf(AgentLifecycleResult.Rejected.class, result);
        var rejected = (AgentLifecycleResult.Rejected) result;
        assertEquals(AgentLifecycleResult.RejectionKind.UNSUPPORTED_DISTRIBUTION, rejected.kind());
    }

    @Test
    void installSetsDefaultPrefixToAgentIdSlash() {
        RegistryAgent agent = npxRegistryAgent("my-agent", "1.0.0", "my-agent@1.0.0");
        service.install(agent);

        InstalledAgent installed = store.findById("my-agent").orElseThrow();
        assertEquals("my-agent/", installed.configuredPrefix());
    }

    @Test
    void installSetsAgentToolViewToAgent() {
        RegistryAgent agent = npxRegistryAgent("my-agent", "1.0.0", "my-agent@1.0.0");
        service.install(agent);

        InstalledAgent installed = store.findById("my-agent").orElseThrow();
        assertEquals("agent", installed.agentToolView());
    }

    // --- Helper methods ---

    private static RegistryAgent npxRegistryAgent(String id, String version, String packageName) {
        return new RegistryAgent(
                id, id, version, "A test agent",
                null, null, null, "MIT", null,
                new RegistryDistribution(null,
                        new RegistryDistribution.PackageDistribution(packageName, List.of(), null),
                        null));
    }

    private static RegistryAgent uvxRegistryAgent(String id, String version, String packageName) {
        return new RegistryAgent(
                id, id, version, "A test agent",
                null, null, null, "MIT", null,
                new RegistryDistribution(null, null,
                        new RegistryDistribution.PackageDistribution(packageName, List.of(), null)));
    }

    private static RegistryAgent binaryRegistryAgent(String id, String version,
                                                     Map<String, RegistryDistribution.BinaryTarget> targets) {
        return new RegistryAgent(
                id, id, version, "A binary agent",
                null, null, null, "Apache-2.0", null,
                new RegistryDistribution(targets, null, null));
    }

    /**
     * A store subclass that throws on the second save call to simulate a failed update.
     * The first save (pre-population) succeeds; the second (update attempt) fails;
     * the third (rollback) succeeds.
     */
    private static final class FailOnSecondSaveStore extends InstalledAgentStore {
        private int saveCount = 0;

        FailOnSecondSaveStore(Path baseDirectory) {
            super(baseDirectory);
        }

        @Override
        public synchronized InstalledAgent save(InstalledAgent agent) {
            saveCount++;
            if (saveCount == 2) {
                throw new InstalledAgentStoreException(
                        InstalledAgentStoreException.Kind.IO_ERROR,
                        "Simulated write failure during update");
            }
            return super.save(agent);
        }
    }
}
