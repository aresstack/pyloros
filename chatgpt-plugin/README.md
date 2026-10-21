# Pyloros ChatGPT plugin

This directory contains the portable Agent Plugins manifests for the **remote**
Pyloros MCP gateway. This plugin ZIP does **not** contain the executable JAR,
and it does not configure Pyloros' own upstream MCP connections.

## Build

The `Build ChatGPT Plugin` workflow builds on every push to `main`.
Pull requests modifying the manifests or packaging script validate the archive
without publishing a release. A manual run is also supported.

The resulting file is published as `pyloros-chatgpt-plugin.zip` in the
`main-latest` GitHub Release, in addition to a workflow artifact.
Its plugin version uses `0.1.<GitHub Actions run number>` to make repeat builds
uniquely versioned.

To build locally:

```bash
python3 scripts/build_chatgpt_plugin.py --version 0.1.1
```

## Install and authenticate

1. In ChatGPT, open Plugins, then the plugin catalog and **Add > Upload plugin archive**.
2. Upload **pyloros-chatgpt-plugin.zip** itself. A GitHub Actions artifact
   download may wrap that ZIP inside another ZIP; use the release asset instead.
3. Set up the bundled remote MCP server using the OAuth client configuration:
   - Public MCP URL: `https://pyloros.aresstack.de/pyloros`
   - OAuth client ID: `angel`
   - Token endpoint authentication: `client_secret_basic`
   - Scope: `mcp`
4. Enter the OAuth client secret in the ChatGPT connection setup flow when requested.
   This is the value used by Pyloros' `OAUTH_CLIENT_SECRET` environment variable.
   Never commit it, embed it in `mcp.json`, or add it as a GitHub Actions secret.
5. Authorize the connection and check that Pyloros tools are discovered.

Pyloros still runs locally with HTTP behind the HTTPS reverse proxy.
The public origin should be `https://pyloros.aresstack.de`.
The default MCP path is `/pyloros` and the default internal HTTP port is 8081.

**Note:** Publishing a refreshed ZIP to GitHub does not automatically reinstall
or update the ZIP in ChatGPT. Upload new plugin package versions when metadata
or connection settings change. Tools served by the same remote MCP endpoint
can change without rebuilding this client package.

For details, see:
- https://developers.openai.com/plugins/build/plugins
- https://developers.openai.com/plugins/deploy/submission
