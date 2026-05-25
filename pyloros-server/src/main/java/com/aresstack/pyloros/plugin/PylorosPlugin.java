package com.aresstack.pyloros.plugin;

/**
 * Contract for Pyloros plugins.
 *
 * <p>A {@code PylorosPlugin} contributes capabilities — currently
 * {@link com.aresstack.pyloros.tool.ToolProvider ToolProvider}s — to a running
 * Pyloros instance. Implementations are typically packaged as a separate JAR
 * and discovered via {@link java.util.ServiceLoader}, but this interface does
 * not mandate a particular loading mechanism.
 *
 * <p>The plugin API is intentionally small. It does not depend on Vert.x,
 * HTTP or JSON-RPC types, so that plugin authors can compile against a
 * lightweight surface and so that the host is free to evolve its
 * infrastructure without breaking plugins.
 *
 * <h2>Lifecycle</h2>
 * The host calls {@link #descriptor()} to obtain stable metadata (including
 * the unique {@link PluginDescriptor#id() plugin id}). It then calls
 * {@link #contribute()} to collect the plugin's {@link PluginContribution}.
 * The host validates the contribution (for example by checking for duplicate
 * plugin ids) and produces a {@link PluginContributionResult} per plugin.
 *
 * <p>Plugins must keep both methods cheap and side-effect free. In particular
 * {@link #descriptor()} should return the same value on repeated calls.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class MyPlugin implements PylorosPlugin {
 *     @Override
 *     public PluginDescriptor descriptor() {
 *         return PluginDescriptor.of("com.example.my-plugin", "My Plugin", "1.0.0");
 *     }
 *
 *     @Override
 *     public PluginContribution contribute() {
 *         return PluginContribution.ofToolProviders(new MyToolProvider());
 *     }
 * }
 * }</pre>
 */
public interface PylorosPlugin {

    /**
     * Stable, non-null metadata describing this plugin.
     *
     * <p>Implementations must return the same value on repeated calls. The
     * descriptor's {@link PluginDescriptor#id() id} acts as the plugin's
     * identity and must be unique within a Pyloros instance.
     *
     * @return this plugin's descriptor; never {@code null}
     */
    PluginDescriptor descriptor();

    /**
     * Build this plugin's contribution.
     *
     * <p>Returning {@link PluginContribution#empty()} is valid for plugins
     * that do not (yet) contribute anything to the current set of extension
     * points.
     *
     * @return the contribution offered by this plugin; never {@code null}
     */
    PluginContribution contribute();
}
