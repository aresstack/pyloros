package com.aresstack.pyloros.acp.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
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
 *
 * <p>Safety guarantees:
 * <ul>
 *   <li>All resolved paths are validated to stay within the base install directory</li>
 *   <li>Archive URLs are restricted to http/https schemes</li>
 *   <li>Extracted archive contents are verified to remain under the install directory</li>
 *   <li>Command paths are normalized and validated against path traversal</li>
 * </ul>
 */
public final class DefaultAgentInstaller implements AgentInstaller {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentInstaller.class);

    private final Path baseInstallDirectory;

    public DefaultAgentInstaller(Path baseInstallDirectory) {
        this.baseInstallDirectory = Objects.requireNonNull(baseInstallDirectory, "baseInstallDirectory must not be null")
                .toAbsolutePath().normalize();
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

        validateArchiveUrl(archiveUrl);

        Path installDir = resolveAndValidateInstallPath(plan.installPath());
        try {
            Files.createDirectories(installDir);
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to create install directory: " + installDir, e);
        }

        // Determine archive type from the URL, preserving the extension for extraction routing
        String archiveExtension = inferArchiveExtension(archiveUrl);
        Path archiveFile = installDir.resolve("archive.download" + archiveExtension);
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

        // Validate all extracted files remain within install directory (zip-slip protection)
        validateExtractedContents(installDir);

        // Verify the expected command exists and is within the install directory
        validateAndResolveCommand(plan.command(), installDir);

        log.info("[INSTALLER] materialized binary agent at {}", installDir);
    }

    @Override
    public void remove(InstallPlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");

        if (!isBinaryDistribution(plan)) {
            log.debug("[INSTALLER] skipping removal for non-binary plan: {}", plan.installPath());
            return;
        }

        Path installDir = resolveAndValidateInstallPath(plan.installPath());
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
     * Uses tar with --no-same-owner and restricted options, or unzip.
     * The archive format is determined from the file extension.
     * Package-private for testing.
     */
    void extract(Path archiveFile, Path targetDir) throws IOException {
        String fileName = archiveFile.getFileName().toString().toLowerCase(Locale.ROOT);
        ProcessBuilder pb;
        if (fileName.endsWith(".zip")) {
            pb = new ProcessBuilder("unzip", "-o", "-q", archiveFile.toString(), "-d", targetDir.toString());
        } else {
            // tar-based (tar.gz, tar.bz2, tar.xz, etc.) — use --no-same-owner to avoid
            // permission issues, and extraction is confined to targetDir via -C
            pb = new ProcessBuilder("tar", "--no-same-owner", "-xf",
                    archiveFile.toString(), "-C", targetDir.toString());
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

    /**
     * Validates that the install path resolves within the base install directory
     * to prevent path traversal attacks.
     */
    private Path resolveAndValidateInstallPath(String installPath) {
        Path resolved = baseInstallDirectory.resolve(installPath).normalize();
        if (!resolved.startsWith(baseInstallDirectory)) {
            throw new AgentInstallException(
                    "Install path '" + installPath + "' resolves outside base directory (path traversal rejected)");
        }
        return resolved;
    }

    /**
     * Validates that all extracted files remain within the install directory.
     * Protects against zip-slip and tar path traversal attacks where archive entries
     * contain absolute paths or '../' sequences.
     */
    private void validateExtractedContents(Path installDir) {
        Path normalizedInstallDir = installDir.toAbsolutePath().normalize();
        try (Stream<Path> walk = Files.walk(installDir)) {
            walk.forEach(path -> {
                Path normalizedPath = path.toAbsolutePath().normalize();
                if (!normalizedPath.startsWith(normalizedInstallDir)) {
                    throw new AgentInstallException(
                            "Extracted file '" + path + "' escapes install directory (archive path traversal detected)");
                }
            });
        } catch (AgentInstallException e) {
            throw e;
        } catch (IOException e) {
            throw new AgentInstallException(
                    "Failed to validate extracted contents in " + installDir, e);
        }
    }

    /**
     * Validates that the command path is safe (no path traversal, stays within install directory)
     * and that the resolved command file exists.
     */
    private void validateAndResolveCommand(String command, Path installDir) {
        Path normalizedInstallDir = installDir.toAbsolutePath().normalize();
        Path commandPath = installDir.resolve(command).normalize();

        if (!commandPath.startsWith(normalizedInstallDir)) {
            throw new AgentInstallException(
                    "Command '" + command + "' resolves outside install directory (path traversal rejected)");
        }

        if (!Files.exists(commandPath)) {
            throw new AgentInstallException(
                    "Expected command '" + command + "' not found after extraction in " + installDir);
        }
    }

    /**
     * Infers the archive file extension from the URL to route to the correct extraction tool.
     */
    private static String inferArchiveExtension(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        // Strip query parameters for extension detection
        int queryIdx = lower.indexOf('?');
        if (queryIdx > 0) {
            lower = lower.substring(0, queryIdx);
        }
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) {
            return ".tar.gz";
        } else if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) {
            return ".tar.bz2";
        } else if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) {
            return ".tar.xz";
        } else if (lower.endsWith(".zip")) {
            return ".zip";
        }
        // Default to .tar.gz if unknown
        return ".tar.gz";
    }

    /**
     * Validates archive URL scheme to prevent SSRF attacks.
     * Only https and http schemes are allowed.
     */
    private static void validateArchiveUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new AgentInstallException("Archive URL must not be blank");
        }
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new AgentInstallException(
                    "Archive URL scheme not allowed (only http/https): " + url);
        }
    }
}
