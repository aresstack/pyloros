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
```

The certificate directory is configured by a Java system property:

```text
-Dpyloros.cert.dir=C:\path\to\certs
```

Expected layout:

```text
<pyloros.cert.dir>\server.crt
<pyloros.cert.dir>\server.key
```

## Behavior

When `server.ssl.enabled=false`:

* Pyloros starts exactly as today.
* It listens with HTTP.
* No certificate files are required.
* Apache reverse proxy should use `http://host:port` as backend target.

When `server.ssl.enabled=true`:

* Pyloros starts an HTTPS server.
* It reads the certificate and private key from the directory configured by `pyloros.cert.dir`.
* If the certificate directory is missing, empty, or invalid, startup should fail with a clear error message.
* If the certificate or key file is missing, startup should fail with a clear error message.

## Suggested implementation

Extend `PylorosConfig` with:

```java
boolean serverSslEnabled
String serverSslCertFile
String serverSslKeyFile
String certificateDirectory
```

Use Vert.x `HttpServerOptions` and `PemKeyCertOptions`.

## Acceptance criteria

* Build is green.
* With default config, Pyloros still starts as HTTP on `server.port`.
* Existing Apache setup works with `http://...:8081` backend targets.
* With `server.ssl.enabled=true` and valid cert directory, Pyloros starts as HTTPS.
* With `server.ssl.enabled=true` and missing cert files, startup fails with a clear message.
* Existing OAuth, SSE and tool forwarding behavior remain unchanged.

## Not in scope

* Automatic certificate generation.
* Let's Encrypt integration.
* Certificate hot reload.
* Mutual TLS.
* Multiple listeners at the same time.
* Replacing Apache in the current deployment.

Sobald der Connector die File-Tools wieder korrekt bereitstellt, schreibe ich sie direkt ins Repo.
