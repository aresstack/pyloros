package com.aresstack.pyloros.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolNameSeparatorResolverTest {

    private static final String PROPERTY_NAME = "mcp.tool-name-separator";

    private final ToolNameSeparatorResolver resolver = new ToolNameSeparatorResolver();

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROPERTY_NAME);
    }

    @Test
    void defaultsToDoubleUnderscoreWhenNoOverridesArePresent() {
        assertEquals("__", resolver.resolve(new String[0]));
    }

    @Test
    void usesSystemPropertyWhenCliOverrideIsMissing() {
        System.setProperty(PROPERTY_NAME, "/");

        assertEquals("/", resolver.resolve(new String[0]));
    }

    @Test
    void usesCliOverrideBeforeSystemProperty() {
        System.setProperty(PROPERTY_NAME, "::");

        assertEquals("/", resolver.resolve(new String[]{"--tool-name-separator=/"}));
    }

    @Test
    void ignoresBlankCliValueAndFallsBackToSystemProperty() {
        System.setProperty(PROPERTY_NAME, "::");

        assertEquals("::", resolver.resolve(new String[]{"--tool-name-separator=   "}));
    }
}

