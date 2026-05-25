package com.aresstack.pyloros.plugin;

/** Singleton no-op {@link PluginDiagnostics} implementation. */
enum NoopPluginDiagnostics implements PluginDiagnostics {

    INSTANCE;

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message) {
    }

    @Override
    public void error(String message) {
    }

    @Override
    public String toString() {
        return "PluginDiagnostics.noop()";
    }
}
