package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents an installed ACP registry agent with its configuration and state.
 */
public record InstalledAgent(
        String id,
        boolean enabled,
        String command,
        List<String> args,
        String prefix,
        List<String> exposeInViews,
        String agentToolView,
        Map<String, String> environment
) {

    public InstalledAgent {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        args = args == null ? List.of() : List.copyOf(args);
        prefix = prefix == null ? id + "__" : prefix;
        exposeInViews = exposeInViews == null ? List.of("public") : List.copyOf(exposeInViews);
        agentToolView = agentToolView == null ? "agent" : agentToolView;
        environment = environment == null ? Map.of() : Map.copyOf(environment);
    }

    public InstalledAgent withEnabled(boolean enabled) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withCommand(String command) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withArgs(List<String> args) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withPrefix(String prefix) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withExposeInViews(List<String> exposeInViews) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withAgentToolView(String agentToolView) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }

    public InstalledAgent withEnvironment(Map<String, String> environment) {
        return new InstalledAgent(id, enabled, command, args, prefix, exposeInViews, agentToolView, environment);
    }
}
