#!/usr/bin/env python3
"""Validate and package the portable ChatGPT plugin for Pyloros."""

import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlparse
from zipfile import ZIP_DEFLATED, ZipFile

VERSION_PATTERN = re.compile(r"^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$")
FORBIDDEN_CREDENTIAL_FIELDS = {
    "clientSecret",
    "client_secret",
    "oauthClientSecret",
    "OAUTH_CLIENT_SECRET",
    "password",
    "accessToken",
    "refreshToken",
}


def read_manifest(path):
    with path.open("r", encoding="utf-8") as source:
        return json.load(source)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_https_url(value, field):
    parsed = urlparse(value)
    require(parsed.scheme == "https" and bool(parsed.netloc), field + " must be an HTTPS URL")
    require(not parsed.username and not parsed.password, field + " must not contain credentials")
    require(not parsed.fragment, field + " must not contain a URL fragment")


def reject_embedded_credentials(data):
    if isinstance(data, dict):
        for key, value in data.items():
            require(key not in FORBIDDEN_CREDENTIAL_FIELDS, "Credential field is forbidden: " + key)
            reject_embedded_credentials(value)
    elif isinstance(data, list):
        for value in data:
            reject_embedded_credentials(value)


def validate_plugin(plugin):
    require(plugin.get("name") == "pyloros", "Unexpected plugin identity")
    require(plugin.get("$schema") == "https://agent-plugins.org/schemas/1.0.0/plugin.schema.json",
            "Unexpected plugin schema")
    require(bool(plugin.get("description")), "A plugin description is required")


def validate_mcp(mcp):
    require(mcp.get("$schema") == "https://agent-plugins.org/schemas/1.0.0/mcp.schema.json",
            "Unexpected MCP schema")
    servers = mcp.get("mcpServers")
    require(isinstance(servers, dict) and set(servers) == {"pyloros"},
            "Exactly one Pyloros MCP server is required")

    server = servers["pyloros"]
    require(server.get("type") == "streamable-http", "Pyloros must use Streamable HTTP")
    validate_https_url(server.get("url", ""), "MCP server URL")
    auth = server.get("extensions", {}).get("com.openai", {}).get("auth", {})
    require(auth.get("type") == "oauth", "Pyloros requires OAuth")
    client = auth.get("client", {})
    require(client.get("mode") == "provided", "Use the configured Pyloros OAuth client")
    require(client.get("clientId") == "angel", "OAuth client ID does not match Pyloros")
    require(client.get("tokenEndpointAuthMethod") == "client_secret_basic",
            "Pyloros expects a confidential OAuth client")
    # The service scope, not the generic "mcp": it separates this connector from
    # askai, which has its own scope on the same authorization server.
    require(auth.get("baseScopes") == ["pyloros"], "Pyloros expects the pyloros scope")
    validate_https_url(auth.get("authorizationUrl", ""), "OAuth authorization URL")
    validate_https_url(auth.get("tokenUrl", ""), "OAuth token URL")
    origin = urlparse(server["url"]).netloc
    require(urlparse(auth["authorizationUrl"]).netloc == origin, "OAuth authorization host differs")
    require(urlparse(auth["tokenUrl"]).netloc == origin, "OAuth token host differs")


def build_plugin(source_dir, output_path, version):
    require(bool(VERSION_PATTERN.fullmatch(version)), "Version must use major.minor.patch")
    plugin = read_manifest(source_dir / "plugin.json")
    mcp = read_manifest(source_dir / "mcp.json")
    plugin["version"] = version

    validate_plugin(plugin)
    validate_mcp(mcp)
    reject_embedded_credentials(plugin)
    reject_embedded_credentials(mcp)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output_path, mode="w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
        archive.writestr("plugin.json", json.dumps(plugin, indent=2, ensure_ascii=False) + "\n")
        archive.writestr("mcp.json", json.dumps(mcp, indent=2, ensure_ascii=False) + "\n")

    print("Created {} (version {})".format(output_path, version))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("chatgpt-plugin"))
    parser.add_argument("--output", type=Path, default=Path("build/chatgpt-plugin/pyloros-chatgpt-plugin.zip"))
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    build_plugin(args.source, args.output, args.version)


if __name__ == "__main__":
    main()
