package com.aresstack.pyloros.acp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WindowsUserProfileEnvironmentTest {

    @TempDir
    Path userProfile;

    @Test
    void restoresMissingUserProfileLocationsForWindowsService() throws Exception {
        Path roaming = Files.createDirectories(userProfile.resolve("AppData").resolve("Roaming"));
        Path local = Files.createDirectories(userProfile.resolve("AppData").resolve("Local"));
        Map<String, String> environment = new HashMap<>();
        environment.put("JAVA_HOME", "C:\\Java");

        WindowsUserProfileEnvironment.restore(environment, "Windows 11", userProfile.toString());

        assertEquals(userProfile.toString(), environment.get("USERPROFILE"));
        assertEquals(userProfile.toString(), environment.get("HOME"));
        assertEquals(roaming.toString(), environment.get("APPDATA"));
        assertEquals(local.toString(), environment.get("LOCALAPPDATA"));
        assertEquals("C:\\Java", environment.get("JAVA_HOME"));
    }

    @Test
    void preservesExplicitProfilePaths() throws Exception {
        Files.createDirectories(userProfile.resolve("AppData").resolve("Roaming"));
        Files.createDirectories(userProfile.resolve("AppData").resolve("Local"));
        Map<String, String> environment = new HashMap<>();
        environment.put("USERPROFILE", "D:\\CustomProfile");
        environment.put("HOME", "D:\\CustomHome");
        environment.put("APPDATA", "D:\\CustomRoaming");
        environment.put("LOCALAPPDATA", "D:\\CustomLocal");

        WindowsUserProfileEnvironment.restore(environment, "Windows 11", userProfile.toString());

        assertEquals("D:\\CustomProfile", environment.get("USERPROFILE"));
        assertEquals("D:\\CustomHome", environment.get("HOME"));
        assertEquals("D:\\CustomRoaming", environment.get("APPDATA"));
        assertEquals("D:\\CustomLocal", environment.get("LOCALAPPDATA"));
    }

    @Test
    void leavesNonWindowsProcessesUnchanged() {
        Map<String, String> environment = new HashMap<>();

        WindowsUserProfileEnvironment.restore(environment, "Linux", userProfile.toString());

        assertTrue(environment.isEmpty());
    }

    @Test
    void doesNotInventProfilePathsForMissingDirectories() {
        Map<String, String> environment = new HashMap<>();

        WindowsUserProfileEnvironment.restore(environment, "Windows 11",
                userProfile.resolve("missing").toString());

        assertFalse(environment.containsKey("USERPROFILE"));
        assertFalse(environment.containsKey("APPDATA"));
        assertFalse(environment.containsKey("LOCALAPPDATA"));
    }
}
