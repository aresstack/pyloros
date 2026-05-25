package com.aresstack.pyloros.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigurationTest {

    @Test
    void missingOptionalConfigurationDoesNotThrow() {
        PluginConfiguration config = PluginConfiguration.empty("example");

        assertTrue(config.isEmpty());
        assertEquals(Optional.empty(), config.getString("anything"));
        assertEquals("fallback", config.getString("anything", "fallback"));
        assertEquals(Optional.empty(), config.getInt("anything"));
        assertEquals(42, config.getInt("anything", 42));
        assertEquals(Optional.empty(), config.getBoolean("anything"));
        assertFalse(config.getBoolean("anything", false));
    }

    @Test
    void requiredStringMissingIsDiagnosed() {
        PluginConfiguration config = PluginConfiguration.empty("example");

        PluginConfigurationException exception = assertThrows(
                PluginConfigurationException.class,
                () -> config.requireString("prefix"));

        assertEquals("example", exception.pluginId());
        assertEquals("prefix", exception.configurationKey());
        assertTrue(exception.getMessage().contains("example"));
        assertTrue(exception.getMessage().contains("prefix"));
    }

    @Test
    void requiredBlankStringIsDiagnosed() {
        PluginConfiguration config = new PluginConfiguration("example", Map.of("prefix", "  "));

        assertThrows(PluginConfigurationException.class, () -> config.requireString("prefix"));
    }

    @Test
    void requiredIntegerWithWrongTypeIsDiagnosed() {
        PluginConfiguration config = new PluginConfiguration("example", Map.of("timeoutMs", "not-a-number"));

        PluginConfigurationException exception = assertThrows(
                PluginConfigurationException.class,
                () -> config.requireInt("timeoutMs"));

        assertEquals("example", exception.pluginId());
        assertEquals("timeoutMs", exception.configurationKey());
    }

    @Test
    void typedAccessorsReadValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("prefix", "example/");
        values.put("timeoutMs", 250);
        values.put("verbose", true);
        values.put("flag", "true");

        PluginConfiguration config = new PluginConfiguration("example", values);

        assertEquals("example/", config.requireString("prefix"));
        assertEquals(250, config.requireInt("timeoutMs"));
        assertTrue(config.requireBoolean("verbose"));
        assertTrue(config.requireBoolean("flag"));
    }

    @Test
    void asMapIsUnmodifiable() {
        PluginConfiguration config = new PluginConfiguration("example",
                new HashMap<>(Map.of("prefix", "p/")));

        assertThrows(UnsupportedOperationException.class,
                () -> config.asMap().put("other", "x"));
    }
}
