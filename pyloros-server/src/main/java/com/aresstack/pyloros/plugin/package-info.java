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
 *       plugin implements.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginDescriptor} — stable
 *       metadata about a plugin, including its unique id.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginContribution} — bundle of
 *       extension points a plugin contributes.</li>
 *   <li>{@link com.aresstack.pyloros.plugin.PluginContributionResult} —
 *       per-plugin outcome reported by the host (e.g. accepted or rejected
 *       because of a duplicate plugin id).</li>
 * </ul>
 */
package com.aresstack.pyloros.plugin;
