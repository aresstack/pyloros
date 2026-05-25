/**
 * Stable, Java-21 compatible Plugin API for Pyloros.
 *
 * <p>This package defines the contracts plugin authors implement to
 * contribute capabilities (currently
 * {@link com.aresstack.pyloros.tool.ToolProvider ToolProvider}s) to a running
 * Pyloros instance.
 *
 * <p>The API is intentionally minimal and free of infrastructure
 * dependencies: it does not reference Vert.x, HTTP or JSON-RPC types.
 *
 * <h2>Key types</h2>
 * <ul>
 *   <li>{@link com.aresstack.pyloros.plugin.PylorosPlugin} — the contract a
 *       plugin implements. Exposes the lifecycle hooks
 *       {@link com.aresstack.pyloros.plugin.PylorosPlugin#descriptor()},
 *       {@link com.aresstack.pyloros.plugin.PylorosPlugin#initialize(com.aresstack.pyloros.plugin.PluginContext)}
 *       and
 *       {@link com.aresstack.pyloros.plugin.PylorosPlugin#contribute(com.aresstack.pyloros.plugin.PluginContext)}.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginDescriptor} — stable
 *       metadata about a plugin, including its unique id. Pure metadata only
 *       (no runtime status or errors).</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginContext} — minimal,
 *       extensible runtime context handed by the host to a plugin. Exposes a
 *       typed service lookup so that host capabilities can grow in a
 *       backwards compatible way without changing the plugin API.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginContribution} — bundle of
 *       extension points a plugin contributes.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginContributionResult} —
 *       per-plugin outcome reported by the host (e.g. accepted or rejected
 *       because of a duplicate plugin id).</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * For each plugin instance, the host calls
 * {@link com.aresstack.pyloros.plugin.PylorosPlugin#descriptor() descriptor()}
 * to obtain identity and metadata, then
 * {@link com.aresstack.pyloros.plugin.PylorosPlugin#initialize(com.aresstack.pyloros.plugin.PluginContext) initialize(context)}
 * once, then
 * {@link com.aresstack.pyloros.plugin.PylorosPlugin#contribute(com.aresstack.pyloros.plugin.PluginContext) contribute(context)}
 * to collect contributions, and finally reports the host-side outcome via
 * {@link com.aresstack.pyloros.plugin.PluginContributionResult}.
 */
package com.aresstack.pyloros.plugin;
