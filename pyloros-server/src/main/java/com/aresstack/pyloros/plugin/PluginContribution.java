package com.aresstack.pyloros.plugin;

import com.aresstack.pyloros.tool.ToolProvider;

import java.util.List;
import java.util.Objects;

/**
 * A bundle of extension points that a {@link PylorosPlugin} contributes to a
 * running Pyloros instance.
 *
 * <p>The set of extension points is intentionally small. Today, plugins can
 * contribute {@link ToolProvider} instances. Additional contribution types may
 * be added in a backwards compatible way as new fields with sensible defaults.
 *
 * <p>A contribution is immutable; pass {@link List#of()} for "no
 * contributions" of a given kind. The class does not depend on Vert.x, HTTP or
 * JSON-RPC types directly.
 *
 * @param toolProviders the {@link ToolProvider}s contributed by the plugin;
 *                      never {@code null}, may be empty, must not contain
 *                      {@code null} entries
 */
public record PluginContribution(List<ToolProvider> toolProviders) {

    private static final PluginContribution EMPTY = new PluginContribution(List.of());

    /**
     * Compact constructor that defensively copies the providers list.
     *
     * @throws NullPointerException if {@code toolProviders} is {@code null} or
     *                              contains a {@code null} entry
     */
    public PluginContribution {
        Objects.requireNonNull(toolProviders, "toolProviders must not be null");
        for (ToolProvider provider : toolProviders) {
            Objects.requireNonNull(provider, "toolProviders must not contain null entries");
        }
        toolProviders = List.copyOf(toolProviders);
    }

    /**
     * @return a shared, immutable contribution that contributes nothing
     */
    public static PluginContribution empty() {
        return EMPTY;
    }

    /**
     * Convenience factory for a contribution that only contributes the given
     * {@link ToolProvider}s.
     *
     * @param toolProviders providers to contribute
     * @return an immutable contribution
     */
    public static PluginContribution ofToolProviders(ToolProvider... toolProviders) {
        Objects.requireNonNull(toolProviders, "toolProviders must not be null");
        return new PluginContribution(List.of(toolProviders));
    }

    /**
     * Convenience factory for a contribution that only contributes the given
     * {@link ToolProvider}s.
     *
     * @param toolProviders providers to contribute
     * @return an immutable contribution
     */
    public static PluginContribution ofToolProviders(List<ToolProvider> toolProviders) {
        return new PluginContribution(toolProviders);
    }

    /**
     * @return {@code true} if this contribution does not contribute anything
     */
    public boolean isEmpty() {
        return toolProviders.isEmpty();
    }
}
