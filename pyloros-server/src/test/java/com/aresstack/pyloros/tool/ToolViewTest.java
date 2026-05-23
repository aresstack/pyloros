package com.aresstack.pyloros.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolViewTest {

    @Test
    void reusesKnownViewInstances() {
        assertSame(ToolView.PUBLIC, ToolView.named("public"));
        assertSame(ToolView.AGENT, ToolView.named("agent"));
        assertSame(ToolView.ADMIN, ToolView.named("admin"));
        assertSame(ToolView.INTERNAL, ToolView.named("internal"));
        assertSame(ToolView.LLM_AGENT, ToolView.named("llm-agent"));
    }

    @Test
    void supportsCustomViews() {
        ToolView customView = ToolView.named("partner-agent");

        assertEquals("partner-agent", customView.name());
        assertEquals(customView, ToolView.named("partner-agent"));
        assertNotSame(customView, ToolView.named("partner-agent"));
    }

    @Test
    void rejectsBlankViewNames() {
        assertThrows(IllegalArgumentException.class, () -> ToolView.named(" "));
    }

    @Test
    void identifiesPublicView() {
        assertTrue(ToolView.PUBLIC.isPublic());
    }
}
    