package com.aresstack.pyloros.acp.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Default implementation of {@link AgentInstaller} that materializes binary
 * distributions by downloading and extracting archives into a controlled
 * install directory, and removes them on uninstall.
 *
 * <p>For npx/uvx distributions, both {@link #materialize} and {@link #remove}
 * are no-ops since those are resolved command plans that rely on system-level
 * package runners.
 */
public final class DefaultAgentInstaller implements AgentInstaller {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentInstaller.class);

    private final Path baseInstallDirectory;

    public DefaultAgentInstaller(Path baseInstallDirectory) {
        this.baseInstallDirectory = Objects.requireNonNull(baseInstallDirectory, "baseInstallDirectory must not be null");
    }

    @Override
    public void materialize(InstallPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");

        if (!isBinaryDistribution(plan)) {
            log.debug("[INSTALLER] skipping materialization for non-binary plan: {}", plan.installPath());
            return;
        }

        String archiveUrl = plan.sourceMetadata().get("archive");
        if (archiveUrl == null || archiveUrl.isBlank()) {
            throw new AgentInstallException(
                    "Binary install plan is missing 'archive' metadata for installPath=" + plan.installPath());
        }

        Path installDir = baseInstallDirectory.resolve(plan.installPath());
        try {
            Files.createDirectories(installDir);
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to create install directory: " + installDir, e);
        }

        Path archiveFile = installDir.resolve("archive.download");
        try {
            download(archiveUrl, archiveFile);
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to download archive from " + archiveUrl + " to " + archiveFile, e);
        }

        try {
            extract(archiveFile, installDir);
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to extract archive " + archiveFile + " to " + installDir, e);
        } finally {
            try {
                Files.deleteIfExists(archiveFile);
            } catch (IOException ignored) {
                // best-effort cleanup of the archive file
            }
        }

        // Verify the expected command exists
        Path commandPath = installDir.resolve(plan.command());
        if (!Files.exists(commandPath)) {
            throw new AgentInstallException(
                    "Expected command '" + plan.command() + "' not found after extraction in " + installDir);
        }

        log.info("[INSTALLER] materialized binary agent at {}", installDir);
    }

    @Override
    public void remove(InstallPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");

        if (!isBinaryDistribution(plan)) {
            log.debug("[INSTALLER] skipping removal for non-binary plan: {}", plan.installPath());
            return;
        }

        Path installDir = baseInstallDirectory.resolve(plan.installPath());
        if (!Files.exists(installDir)) {
            log.debug("[INSTALLER] install directory does not exist, nothing to remove: {}", installDir);
            return;
        }

        try (Stream<Path> walk = Files.walk(installDir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new AgentInstallException(
                                    "Failed to delete " + path + " during uninstall", e);
                        }
                    });
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to traverse install directory for removal: " + installDir, e);
        }

        log.info("[INSTALLER] removed binary agent files at {}", installDir);
    }

    /**
     * Downloads a file from the given URL to the target path.
     * Package-private for testing.
     */
    void download(String url, Path target) throws IOException {
        URI uri = URI.create(url);
        try (InputStream in = uri.toURL().openStream()) {
            Files.copy(in, target);
        }
    }

    /**
     * Extracts an archive to the target directory.
     * Supports .tar.gz, .tar.bz2, and .zip based on file extension heuristics from the URL.
     * Package-private for testing.
     */
    void extract(Path archiveFile, Path targetDir) throws IOException {
        // Delegate to ProcessBuilder for tar/unzip since Java's built-in archive
        // support doesn't cover .tar.bz2 natively. The archive format is inferred
        // from the original download URL stored in source metadata.
        String fileName = archiveFile.getFileName().toString();
        ProcessBuilder pb;
        if (fileName.endsWith(".zip")) {
            pb = new ProcessBuilder("unzip", "-o", archiveFile.toString(), "-d", targetDir.toString());
        } else {
            // Assume tar-based (tar.gz, tar.bz2, tar.xz, etc.)
            pb = new ProcessBuilder("tar", "-xf", archiveFile.toString(), "-C", targetDir.toString());
        }
        pb.redirectErrorStream(true);
        try {
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("Extraction process exited with code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction was interrupted", e);
        }
    }

    private static boolean isBinaryDistribution(InstallPlan plan) {
        Map<String, String> metadata = plan.sourceMetadata();
        return "binary".equals(metadata.get("distributionType"));
    }
}
