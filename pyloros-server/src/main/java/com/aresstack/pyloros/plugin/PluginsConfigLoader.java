package com.aresstack.pyloros.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * Tolerant Jackson-based loader for {@link PluginsConfig}.
 *
 * <p>Used by the server bootstrap to read the {@code "plugins"} block out of a larger
 * configuration document. The loader is intentionally permissive so that absent or
 * partially specified configuration does not abort the server start; structural errors
 * (malformed JSON, wrong types in the {@code "plugins"} block) are surfaced as
 * {@link PluginConfigurationException} with the underlying parser error attached as
 * cause, satisfying the "ungültige Konfiguration wird gemeldet" criterion.
 */
public final class PluginsConfigLoader {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Parse a root JSON document and return its {@code "plugins"} section, if any. */
    public PluginsConfig loadFromRoot(JsonNode rootNode) {
        if (rootNode == null || rootNode.isNull() || rootNode.isMissingNode()) {
            return PluginsConfig.empty();
        }
        JsonNode pluginsNode = rootNode.get("plugins");
        return loadFromPluginsNode(pluginsNode);
    }

    /** Parse a JSON node that already points at the {@code "plugins"} section. */
    public PluginsConfig loadFromPluginsNode(JsonNode pluginsNode) {
        if (pluginsNode == null || pluginsNode.isNull() || pluginsNode.isMissingNode()) {
            return PluginsConfig.empty();
        }
        try {
            PluginsConfig parsed = JSON.treeToValue(pluginsNode, PluginsConfig.class);
            return parsed == null ? PluginsConfig.empty() : parsed;
        } catch (IOException cause) {
            throw new PluginConfigurationException(null, null,
                    "invalid plugin configuration: " + cause.getMessage(), cause);
        }
    }

    public PluginsConfig loadFromJson(String json) {
        if (json == null || json.isBlank()) {
            return PluginsConfig.empty();
        }
        try {
            return loadFromRoot(JSON.readTree(json));
        } catch (IOException cause) {
            throw new PluginConfigurationException(null, null,
                    "invalid plugin configuration JSON: " + cause.getMessage(), cause);
        }
    }

    public PluginsConfig loadFromStream(InputStream stream) {
        if (stream == null) {
            return PluginsConfig.empty();
        }
        try {
            return loadFromRoot(JSON.readTree(stream));
        } catch (IOException cause) {
            throw new PluginConfigurationException(null, null,
                    "invalid plugin configuration JSON: " + cause.getMessage(), cause);
        }
    }

    public PluginsConfig loadFromReader(Reader reader) {
        if (reader == null) {
            return PluginsConfig.empty();
        }
        try {
            return loadFromRoot(JSON.readTree(reader));
        } catch (IOException cause) {
            throw new PluginConfigurationException(null, null,
                    "invalid plugin configuration JSON: " + cause.getMessage(), cause);
        }
    }
}
