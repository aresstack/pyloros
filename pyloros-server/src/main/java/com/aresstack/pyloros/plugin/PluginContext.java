package com.aresstack.pyloros.plugin;

/**
 * Minimal context exposed to a {@link com.aresstack.pyloros.plugin plugin} so that it
 * can read its configuration block.
 *
 * <p>Only the configuration-relevant slice is defined here; additional core services
 * (e.g. {@code McpClientFactory}, {@code PolicyRegistry}) will be added in the
 * {@code R4-04} milestone once the plugin core is integrated end-to-end. Keeping the
 * surface narrow now satisfies R4-03's acceptance criterion <em>"Plugin-Konfiguration
 * ist über {@code PluginContext} lesbar"</em> without prejudging later decisions.
 */
public interface PluginContext {

    /** Unique id of the plugin this context belongs to. */
    String pluginId();

    /** Read-only view of the plugin-specific configuration block. */
    PluginConfiguration configuration();
}
