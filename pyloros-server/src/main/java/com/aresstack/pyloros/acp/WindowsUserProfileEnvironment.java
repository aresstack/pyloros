package com.aresstack.pyloros.acp;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Restore user-profile locations for ACP subprocesses started by service hosts
 * that supply only a minimal Windows environment.
 */
final class WindowsUserProfileEnvironment {

    private WindowsUserProfileEnvironment() {
    }

    static void restore(Map<String, String> environment, String operatingSystem, String userHome) {
        Objects.requireNonNull(environment, "environment must not be null");
        if (!isWindows(operatingSystem) || userHome == null || userHome.trim().isEmpty()) {
            return;
        }

        try {
            Path profile = Path.of(userHome).toAbsolutePath().normalize();
            if (!Files.isDirectory(profile)) {
                return;
            }

            restoreDirectory(environment, "USERPROFILE", profile);
            restoreDirectory(environment, "HOME", profile);
            restoreDirectory(environment, "APPDATA", profile.resolve("AppData").resolve("Roaming"));
            restoreDirectory(environment, "LOCALAPPDATA", profile.resolve("AppData").resolve("Local"));
        } catch (InvalidPathException ignored) {
            // Preserve the process environment when the profile path is invalid.
        }
    }

    private static boolean isWindows(String operatingSystem) {
        return operatingSystem != null
                && operatingSystem.toLowerCase(Locale.ROOT).contains("windows");
    }

    private static void restoreDirectory(Map<String, String> environment, String name, Path directory) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        String configuredValue = environment.get(name);
        if (configuredValue == null || configuredValue.trim().isEmpty()) {
            environment.put(name, directory.toString());
        }
    }
}
