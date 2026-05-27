package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DefaultAgentInstaller} focusing on preflight archive
 * validation that rejects malicious entries before extraction.
 */
class DefaultAgentInstallerTest {

    @TempDir
    Path tempDir;

    private DefaultAgentInstaller installer;

    @BeforeEach
    void setUp() {
        installer = new TestableDefaultAgentInstaller(tempDir);
    }

    @Test
    void materialize_rejectsArchiveWithTraversalEntry_beforeExtraction() {
        // Archive listing returns a traversal entry; extraction must NOT be called
        var testInstaller = new TraversalTestInstaller(tempDir,
                List.of("bin/goose", "../../../etc/passwd"));

        var plan = new InstallPlan("bin/goose", List.of(), "goose",
                Map.of("distributionType", "binary", "archive", "https://example.com/goose.tar.gz"));

        var ex = assertThrows(AgentInstallException.class, () -> testInstaller.materialize(plan));
        assertTrue(ex.getMessage().contains("path traversal rejected, extraction aborted"));
        assertFalse(testInstaller.extractCalled, "extract() should NOT be called when preflight rejects entries");
    }

    @Test
    void materialize_rejectsArchiveWithAbsolutePathEntry_beforeExtraction() {
        var testInstaller = new TraversalTestInstaller(tempDir,
                List.of("/etc/shadow", "bin/goose"));

        var plan = new InstallPlan("bin/goose", List.of(), "goose",
                Map.of("distributionType", "binary", "archive", "https://example.com/goose.tar.gz"));

        var ex = assertThrows(AgentInstallException.class, () -> testInstaller.materialize(plan));
        assertTrue(ex.getMessage().contains("absolute path entry"));
        assertTrue(ex.getMessage().contains("extraction aborted"));
        assertFalse(testInstaller.extractCalled, "extract() should NOT be called when preflight rejects entries");
    }

    @Test
    void materialize_allowsSafeArchiveEntries() throws IOException {
        Path installDir = tempDir.resolve("goose");
        Files.createDirectories(installDir.resolve("bin"));
        Files.writeString(installDir.resolve("bin/goose"), "#!/bin/sh\necho hello");

        var testInstaller = new TraversalTestInstaller(tempDir,
                List.of("bin/", "bin/goose"));

        var plan = new InstallPlan("bin/goose", List.of(), "goose",
                Map.of("distributionType", "binary", "archive", "https://example.com/goose.tar.gz"));

        // Should not throw — entries are safe
        assertDoesNotThrow(() -> testInstaller.materialize(plan));
        assertTrue(testInstaller.extractCalled, "extract() should be called after successful preflight");
    }

    @Test
    void materialize_rejectsDoubleTraversalEntry_beforeExtraction() {
        var testInstaller = new TraversalTestInstaller(tempDir,
                List.of("bin/../../etc/passwd"));

        var plan = new InstallPlan("bin/goose", List.of(), "goose",
                Map.of("distributionType", "binary", "archive", "https://example.com/goose.zip"));

        var ex = assertThrows(AgentInstallException.class, () -> testInstaller.materialize(plan));
        assertTrue(ex.getMessage().contains("path traversal rejected"));
        assertFalse(testInstaller.extractCalled);
    }

    /**
     * Test double that overrides download/extract/listArchiveEntries to avoid
     * real network and process calls while testing the preflight validation logic.
     */
    private static class TraversalTestInstaller extends DefaultAgentInstaller {

        private final List<String> fakeEntries;
        boolean extractCalled = false;

        TraversalTestInstaller(Path baseDir, List<String> fakeEntries) {
            super(baseDir);
            this.fakeEntries = fakeEntries;
        }

        @Override
        void download(String url, Path target) throws IOException {
            // Create a dummy file so validation can proceed
            Files.writeString(target, "fake-archive-content");
        }

        @Override
        List<String> listArchiveEntries(Path archiveFile) {
            return fakeEntries;
        }

        @Override
        void extract(Path archiveFile, Path targetDir) throws IOException {
            extractCalled = true;
            // no-op: just mark that extraction was reached
        }
    }

    /**
     * Minimal test double for non-security tests.
     */
    private static class TestableDefaultAgentInstaller extends DefaultAgentInstaller {

        TestableDefaultAgentInstaller(Path baseDir) {
            super(baseDir);
        }

        @Override
        void download(String url, Path target) throws IOException {
            Files.writeString(target, "fake-archive-content");
        }

        @Override
        List<String> listArchiveEntries(Path archiveFile) {
            return List.of();
        }

        @Override
        void extract(Path archiveFile, Path targetDir) throws IOException {
            // no-op
        }
    }
}
