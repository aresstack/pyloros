package com.aresstack.pyloros;

import com.aresstack.pyloros.plugin.PluginContext;
import com.aresstack.pyloros.plugin.PluginContribution;
import com.aresstack.pyloros.plugin.PluginDescriptor;
import com.aresstack.pyloros.plugin.PylorosPlugin;
import com.aresstack.pyloros.provider.ProviderType;
import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;
import com.fasterxml.jackson.databind.JsonNode;
import io.vertx.core.Future;

import java.util.List;
import java.util.Map;

public final class TestBootstrapPlugin implements PylorosPlugin {

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.of("app-bootstrap-test-plugin");
    }

    @Override
    public PluginContribution contribute(PluginContext context) {
        return PluginContribution.ofToolProviders(new ToolProvider() {
            @Override
            public String providerId() {
                return "app-bootstrap-test-provider";
            }

            @Override
            public ProviderType providerType() {
                return ProviderType.MCP;
            }

            @Override
            public List<ToolView> exposedViews() {
                return List.of(ToolView.PUBLIC);
            }

            @Override
            public Future<List<Map<String, Object>>> listTools() {
                return Future.succeededFuture(List.of(Map.of(
                        "name", "bootstrap_echo",
                        "description", "Bootstrap test tool",
                        "inputSchema", Map.of("type", "object")
                )));
            }

            @Override
            public Future<Map<String, Object>> callTool(String upstreamToolName, JsonNode arguments) {
                return Future.succeededFuture(Map.of("isError", false));
            }
        });
    }
}
