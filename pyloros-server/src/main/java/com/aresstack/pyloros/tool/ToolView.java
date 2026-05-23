package com.aresstack.pyloros.tool;

import java.util.Objects;

public final class ToolView {

    public static final ToolView PUBLIC = new ToolView("public");
    public static final ToolView AGENT = new ToolView("agent");
    public static final ToolView ADMIN = new ToolView("admin");
    public static final ToolView INTERNAL = new ToolView("internal");
    public static final ToolView LLM_AGENT = new ToolView("llm-agent");

    private final String name;

    private ToolView(String name) {
        this.name = validate(name);
    }

    public static ToolView named(String name) {
        String validatedName = validate(name);
        if (PUBLIC.name.equals(validatedName)) {
            return PUBLIC;
        }
        if (AGENT.name.equals(validatedName)) {
            return AGENT;
        }
        if (ADMIN.name.equals(validatedName)) {
            return ADMIN;
        }
        if (INTERNAL.name.equals(validatedName)) {
            return INTERNAL;
        }
        if (LLM_AGENT.name.equals(validatedName)) {
            return LLM_AGENT;
        }
        return new ToolView(validatedName);
    }

    public String name() {
        return name;
    }

    public boolean isPublic() {
        return PUBLIC.equals(this);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ToolView)) {
            return false;
        }
        ToolView that = (ToolView) other;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    private static String validate(String name) {
        Objects.requireNonNull(name, "name must not be null");
        String trimmedName = name.trim();
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return trimmedName;
    }
}
    