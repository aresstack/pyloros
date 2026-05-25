# Plugin development with the example plugin

This repository contains a minimal plugin implementation in:

- `pyloros-example-plugin/`

It demonstrates the supported R4 plugin model (`PylorosPlugin` + `ToolProvider`) and contributes one tool:

- `example-tools__echo` (default separator `__`)
- `example-tools/echo` (when started with `--tool-name-separator=/`)

## 1) Build configuration

Example plugin module build file:

- `pyloros-example-plugin/build.gradle`

Use `java-library` and depend on `:pyloros-server` so the plugin can compile against:

- `com.aresstack.pyloros.plugin.PylorosPlugin`
- `com.aresstack.pyloros.plugin.PluginContext`
- `com.aresstack.pyloros.plugin.PluginContribution`
- `com.aresstack.pyloros.tool.ToolProvider`

## 2) Plugin identity (plugin ID)

The example plugin class is:

- `com.aresstack.pyloros.example.plugin.ExampleEchoPlugin`

Its plugin ID is:

- `example-echo-plugin`

Plugin IDs must be globally unique inside one running Pyloros instance.

## 3) ServiceLoader registration (`META-INF/services`)

To make Pyloros discover your plugin via `ServiceLoader`, add:

- `META-INF/services/com.aresstack.pyloros.plugin.PylorosPlugin`

The file must contain the fully qualified plugin class name, one entry per line.  
Example:

```text
com.aresstack.pyloros.example.plugin.ExampleEchoPlugin
```

## 4) Contributing tools

`ExampleEchoPlugin` contributes `ExampleEchoToolProvider` with:

- `providerId = "example-tools"`
- upstream tool name `echo`

The ToolCatalog external tool name is usually:

- `providerId + "__" + upstreamToolName`
- `example-tools__echo`

If Pyloros is started with `--tool-name-separator=/`, the external name becomes:

- `example-tools/echo`

## 5) Plugin enable/disable configuration (`mcp.json`)

Plugin activation is controlled in `mcp.json` under `plugins`:

```json
{
  "plugins": {
    "enabledByDefault": false,
    "items": [
      {
        "id": "example-echo-plugin",
        "enabled": true,
        "configuration": {
          "note": "optional plugin-specific settings"
        }
      }
    ]
  }
}
```

Rules:

- `enabledByDefault=false` means only explicitly enabled plugins are loaded.
- `enabledByDefault=true` means all discovered plugins are loaded unless explicitly disabled.
- Explicit `enabled` on an item overrides `enabledByDefault`.

## 6) View behavior and naming/collision expectations

- Visibility is controlled by the provider `exposedViews()` (example: `PUBLIC` only).
- `tools/list` only includes providers exposed for the requested view.
- Tool names are exact-match routed through `ToolRouter`.
- If two tools resolve to the same external name, catalog refresh fails with duplicate-name validation.
- Keep tool names deterministic and unique (or set `preservesUpstreamToolName()` carefully if you need raw names).

## 7) Local testing workflow

In this repository, the example plugin is wired as a **test-only dependency** of `pyloros-app`, not a production runtime dependency.
That keeps production startup behavior unchanged unless an operator intentionally adds the plugin JAR to runtime classpath.

From repository root:

```bash
./gradlew --no-daemon :pyloros-example-plugin:compileJava
./gradlew --no-daemon :pyloros-app:test --tests "com.aresstack.pyloros.ExampleEchoPluginIntegrationTest"
```

The integration test verifies:

- plugin is discovered through `ServiceLoader`
- `example-tools__echo` appears in `ToolCatalog`
- `ToolRouter` can call the tool and receives echoed input
- disabled plugin does not expose the tool

## 8) Release 4 smoke test (documented checklist)

Use this smoke test before a Release-4 publish candidate.

### Scope covered

- example plugin path (`pyloros-example-plugin/`)
- `ServiceLoader` discovery path
- plugin enable/disable configuration handling
- `tools/list` visibility through `ToolCatalog`
- `ToolRouter` call path
- expected external tool name (`example-tools__echo` by default, `example-tools/echo` with `/` separator)

### Commands

From repository root:

```bash
./gradlew --no-daemon :pyloros-example-plugin:compileJava
./gradlew --no-daemon :pyloros-server:test --tests "com.aresstack.pyloros.plugin.ServiceLoaderDiscoveryTest"
./gradlew --no-daemon :pyloros-app:test --tests "com.aresstack.pyloros.ExampleEchoPluginIntegrationTest"
```

### Expected result

1. ServiceLoader discovery succeeds:
   - `ServiceLoaderDiscoveryTest` loads valid plugins and isolates invalid entries.
2. Enable/disable configuration works:
   - `ExampleEchoPluginIntegrationTest` verifies enabled plugin load and explicit disable behavior.
3. Catalog visibility is correct:
   - `example-tools__echo` appears in PUBLIC catalog view when enabled.
   - `example-tools__echo` is absent when the plugin is explicitly disabled.
4. Router call works on same path:
   - `ToolRouter` call to `example-tools__echo` returns non-error and echoes input text.
5. External tool naming is deterministic:
   - default external name is `example-tools__echo`
   - with `--tool-name-separator=/` it becomes `example-tools/echo`
