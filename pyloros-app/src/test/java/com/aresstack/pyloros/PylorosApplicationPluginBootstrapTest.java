package com.aresstack.pyloros;

import com.aresstack.pyloros.plugin.PluginLoadResult;
import com.aresstack.pyloros.plugin.PluginRegistry;
import com.aresstack.pyloros.plugin.PluginStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PylorosApplicationPluginBootstrapTest {

    @Test
    void disabledPluginInRuntimeConfigDoesNotContributeProviderOrTools(@TempDir Path tempDir) throws Exception {
        Path mcpConfig = tempDir.resolve("mcp.json");
        Files.writeString(mcpConfig, """
                {
                  "plugins": {
                    "enabledByDefault": true,
                    "items": [
                      { "id": "app-bootstrap-test-plugin", "enabled": false }
                    ]
                  }
                }
                """);

        PylorosApplication application = new PylorosApplication(new String[]{
                "--mcp-config=" + mcpConfig.toAbsolutePath()
        });

        PluginRegistry registry = application.loadPluginRegistry();
        PluginLoadResult plugin = registry.findById("app-bootstrap-test-plugin").orElseThrow();

        assertEquals(PluginStatus.DISABLED, plugin.status());
        assertFalse(registry.contributedProviders().stream()
                .anyMatch(provider -> provider.providerId().equals("app-bootstrap-test-provider")));
    }
}
