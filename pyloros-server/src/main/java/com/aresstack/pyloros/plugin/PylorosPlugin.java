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
 * For each plugin instance the host:
 * <ol>
 *   <li>calls {@link #descriptor()} to obtain stable metadata (including the
 *       unique {@link PluginDescriptor#id() plugin id});</li>
 *   <li>builds a {@link PluginContext} bound to that plugin and calls
 *       {@link #initialize(PluginContext)} once, before any contribution is
 *       collected;</li>
 *   <li>calls {@link #contribute(PluginContext)} to collect the plugin's
 *       {@link PluginContribution};</li>
 *   <li>validates the contribution (for example by checking for duplicate
 *       plugin ids) and produces a {@link PluginContributionResult} per
 *       plugin.</li>
 * </ol>
 *
 * <p>Plugins must keep {@link #descriptor()} cheap and side-effect free; it
 * should return the same value on repeated calls. The default
 * {@link #initialize(PluginContext)} implementation is a no-op so simple
 * plugins do not have to override it.
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
 *     public PluginContribution contribute(PluginContext context) {
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
     * Lifecycle hook invoked by the host once before
     * {@link #contribute(PluginContext)} is called.
     *
     * <p>Implementations may use the supplied {@link PluginContext} to look
     * up host services and prepare internal state. The default implementation
     * is a no-op so plugins that do not need initialization do not have to
     * override it.
     *
     * <p>Hosts must invoke this method at most once per plugin instance, and
     * must invoke it before {@link #contribute(PluginContext)}. Plugins
     * should not perform any heavy work before {@code initialize} is called.
     *
     * @param context the runtime context for this plugin; never {@code null}
     */
    default void initialize(PluginContext context) {
        // no-op by default
    }

    /**
     * Build this plugin's contribution.
     *
     * <p>The supplied {@link PluginContext} exposes host capabilities the
     * plugin may use while assembling its contribution. Implementations
     * should treat missing host services (see {@link PluginContext#service})
     * gracefully and either degrade or contribute nothing for the affected
     * extension points.
     *
     * <p>Returning {@link PluginContribution#empty()} is valid for plugins
     * that do not (yet) contribute anything to the current set of extension
     * points.
     *
     * @param context the runtime context for this plugin; never {@code null}
     * @return the contribution offered by this plugin; never {@code null}
     */
    PluginContribution contribute(PluginContext context);
}
