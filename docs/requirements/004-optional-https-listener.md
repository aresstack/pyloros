# 004 - Optional HTTPS Listener

## Problem

Pyloros currently listens as a plain HTTP server. This is the correct default when Pyloros runs behind Apache or another reverse proxy that terminates TLS.

In some deployments it should also be possible to run Pyloros with HTTPS directly.

## Decision

HTTP remains the default.

HTTPS must be explicitly enabled by configuration.

## Goal

Add an optional HTTPS listener mode for Pyloros without changing the current default behavior.

## Configuration

Default configuration:

```properties
server.ssl.enabled=false
server.ssl.cert.file=server.crt
server.ssl.key.file=server.key