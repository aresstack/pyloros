package com.aresstack.pyloros.example.plugin;

import com.aresstack.pyloros.plugin.PluginContext;
import com.aresstack.pyloros.plugin.PluginContribution;
import com.aresstack.pyloros.plugin.PluginDescriptor;
import com.aresstack.pyloros.plugin.PylorosPlugin;

public final class ExampleEchoPlugin implements PylorosPlugin {

    public static final String PLUGIN_ID = "example-echo-plugin";

    @Override
    public PluginDescriptor descriptor() {
        return PluginDescriptor.of(PLUGIN_ID, "Example Echo Plugin", "1.0.0");
    }

    @Override
    public PluginContribution contribute(PluginContext context) {
        return PluginContribution.ofToolProviders(new ExampleEchoToolProvider(context.pluginId()));
    }
}
