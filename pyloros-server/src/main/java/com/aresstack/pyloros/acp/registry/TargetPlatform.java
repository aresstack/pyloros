package com.aresstack.pyloros.acp.registry;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents an OS/architecture combination using the ACP Registry platform key
 * vocabulary (e.g. {@code linux-x86_64}, {@code darwin-aarch64}, {@code windows-x86_64}).
 */
public record TargetPlatform(String os, String arch) {

    public TargetPlatform {
        os = requireText(os, "os").toLowerCase(Locale.ROOT);
        arch = requireText(arch, "arch").toLowerCase(Locale.ROOT);
    }

    /**
     * Detects the current platform using JVM system properties and normalizes
     * to the upstream ACP Registry key vocabulary.
     */
    public static TargetPlatform current() {
        return new TargetPlatform(normalizeOs(System.getProperty("os.name")),
                normalizeArch(System.getProperty("os.arch")));
    }

    /**
     * Returns the platform key as used in the ACP Registry binary distribution map
     * (e.g. {@code "linux-x86_64"}, {@code "darwin-aarch64"}).
     */
    public String platformKey() {
        return os + "-" + arch;
    }

    static String normalizeOs(String osName) {
        Objects.requireNonNull(osName, "osName must not be null");
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("linux")) return "linux";
        if (lower.contains("mac") || lower.contains("darwin")) return "darwin";
        if (lower.contains("win")) return "windows";
        return lower.replaceAll("\\s+", "-");
    }

    static String normalizeArch(String archName) {
        Objects.requireNonNull(archName, "archName must not be null");
        String lower = archName.toLowerCase(Locale.ROOT);
        if (lower.equals("amd64") || lower.equals("x86_64")) return "x86_64";
        if (lower.equals("aarch64") || lower.equals("arm64")) return "aarch64";
        if (lower.equals("x86") || lower.equals("i386") || lower.equals("i686")) return "x86";
        return lower;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
