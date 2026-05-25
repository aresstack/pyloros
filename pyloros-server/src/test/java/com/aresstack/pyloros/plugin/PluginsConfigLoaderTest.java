package com.aresstack.pyloros.plugin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginsConfigLoaderTest {

    private final PluginsConfigLoader loader = new PluginsConfigLoader();

    @Test
    void parsesIssueExample() {
        String json = """
                {
                  "plugins": {
                    "enabledByDefault": false,
                    "items": [
                      {
                        "id": "example-tools",
                        "enabled": true,
                        "configuration": { "prefix": "example/" }
                      }
                    ]
                  }
                }
                """;

        PluginsConfig config = loader.loadFromJson(json);

        assertFalse(config.effectiveEnabledByDefault());
        assertEquals(1, config.effectiveItems().size());

        PluginActivation activation = new PluginActivationResolver(config).resolve("example-tools");
        assertTrue(activation.enabled());
        assertEquals("example/", activation.configuration().requireString("prefix"));
    }

    @Test
    void missingPluginsSectionYieldsEmptyConfig() {
        PluginsConfig config = loader.loadFromJson("{}");

        assertNotNull(config);
        assertFalse(config.effectiveEnabledByDefault());
        assertTrue(config.effectiveItems().isEmpty());
    }

    @Test
    void nullAndBlankInputsYieldEmptyConfig() {
        assertTrue(loader.loadFromJson(null).effectiveItems().isEmpty());
        assertTrue(loader.loadFromJson("").effectiveItems().isEmpty());
    }

    @Test
    void unknownFieldsAreIgnored() {
        String json = """
                {
                  "plugins": {
                    "enabledByDefault": true,
                    "extraField": 123,
                    "items": [
                      { "id": "p", "enabled": true, "unknown": "value" }
                    ]
                  }
                }
                """;

        PluginsConfig config = loader.loadFromJson(json);

        assertTrue(config.effectiveEnabledByDefault());
        assertEquals(1, config.effectiveItems().size());
        assertEquals("p", config.effectiveItems().get(0).id());
    }

    @Test
    void malformedJsonIsReported() {
        PluginConfigurationException exception = assertThrows(
                PluginConfigurationException.class,
                () -> loader.loadFromJson("{ not json"));

        assertNotNull(exception.getCause());
    }

    @Test
    void wrongTypeInItemsIsReported() {
        String json = """
                {
                  "plugins": {
                    "items": "should-be-a-list"
                  }
                }
                """;

        assertThrows(PluginConfigurationException.class, () -> loader.loadFromJson(json));
    }
}
