package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AcpRegistryClientTest {

    private static final String VALID_REGISTRY_JSON = """
            {
              "version": "1.0.0",
              "agents": [
                {
                  "id": "test-agent",
                  "name": "Test Agent",
                  "version": "0.1.0",
                  "description": "A test agent for unit testing",
                  "repository": "https://github.com/example/test-agent",
                  "license": "MIT",
                  "authors": ["Test Author"],
                  "distribution": {
                    "npx": {
                      "package": "test-agent-acp@0.1.0",
                      "args": []
                    }
                  }
                },
                {
                  "id": "binary-agent",
                  "name": "Binary Agent",
                  "version": "2.0.0",
                  "description": "An agent with binary distribution",
                  "license": "Apache-2.0",
                  "icon": "https://example.com/icon.svg",
                  "distribution": {
                    "binary": {
                      "linux-x86_64": {
                        "archive": "https://example.com/agent-linux.tar.gz",
                        "cmd": "./agent",
                        "args": ["--stdio"]
                      }
                    }
                  }
                }
              ]
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void loadsValidRegistryFromInputStream() {
        AcpRegistryCache cache = new AcpRegistryCache(tempDir.resolve("cache.json"));
        AcpRegistryClient client = new AcpRegistryClient(URI.create("http://localhost/registry.json"), cache);

        AcpRegistryLoadResult result = client.loadFrom(
                new ByteArrayInputStream(VALID_REGISTRY_JSON.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Success.class, result);
        var success = (AcpRegistryLoadResult.Success) result;
        assertEquals(AcpRegistryLoadResult.Source.REMOTE, success.source());
        assertEquals("1.0.0", success.registry().version());
        assertEquals(2, success.registry().agents().size());

        RegistryAgent first = success.registry().agents().get(0);
        assertEquals("test-agent", first.id());
        assertEquals("Test Agent", first.name());
        assertEquals("0.1.0", first.version());
        assertEquals("A test agent for unit testing", first.description());
        assertEquals("MIT", first.license());
        assertEquals("https://github.com/example/test-agent", first.repository());
        assertNotNull(first.distribution().npx());
        assertEquals("test-agent-acp@0.1.0", first.distribution().npx().packageName());

        RegistryAgent second = success.registry().agents().get(1);
        assertEquals("binary-agent", second.id());
        assertNotNull(second.distribution().binary());
        assertTrue(second.distribution().binary().containsKey("linux-x86_64"));
        assertEquals("./agent", second.distribution().binary().get("linux-x86_64").cmd());
    }

    @Test
    void rejectsRegistryWithMissingVersion() {
        AcpRegistryCache cache = new AcpRegistryCache(tempDir.resolve("cache.json"));
        AcpRegistryClient client = new AcpRegistryClient(URI.create("http://localhost/registry.json"), cache);

        String json = """
                {
                  "agents": [
                    {
                      "id": "ok",
                      "name": "OK",
                      "version": "1.0.0",
                      "description": "ok",
                      "distribution": { "npx": { "package": "ok@1.0.0" } }
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = client.loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Failure.class, result);
        var failure = (AcpRegistryLoadResult.Failure) result;
        assertTrue(failure.errors().stream().anyMatch(e -> e.contains("version")));
    }

    @Test
    void rejectsRegistryWithInvalidAgent() {
        AcpRegistryCache cache = new AcpRegistryCache(tempDir.resolve("cache.json"));
        AcpRegistryClient client = new AcpRegistryClient(URI.create("http://localhost/registry.json"), cache);

        String json = """
                {
                  "version": "1.0.0",
                  "agents": [
                    {
                      "id": "",
                      "name": "",
                      "version": "",
                      "description": "",
                      "distribution": null
                    }
                  ]
                }
                """;

        AcpRegistryLoadResult result = client.loadFrom(
                new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Failure.class, result);
        var failure = (AcpRegistryLoadResult.Failure) result;
        assertTrue(failure.errors().size() >= 4, "Expected multiple validation errors, got: " + failure.errors());
    }

    @Test
    void rejectsInvalidJson() {
        AcpRegistryCache cache = new AcpRegistryCache(tempDir.resolve("cache.json"));
        AcpRegistryClient client = new AcpRegistryClient(URI.create("http://localhost/registry.json"), cache);

        AcpRegistryLoadResult result = client.loadFrom(
                new ByteArrayInputStream("not json".getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(AcpRegistryLoadResult.Failure.class, result);
        var failure = (AcpRegistryLoadResult.Failure) result;
        assertTrue(failure.errors().get(0).contains("parse"));
    }

    @Test
    void cacheIsWrittenOnSuccessfulLoad() {
        Path cacheFile = tempDir.resolve("cache.json");
        AcpRegistryCache cache = new AcpRegistryCache(cacheFile);
        AcpRegistryClient client = new AcpRegistryClient(URI.create("http://localhost/registry.json"), cache);

        client.loadFrom(new ByteArrayInputStream(VALID_REGISTRY_JSON.getBytes(StandardCharsets.UTF_8)));

        assertTrue(Files.exists(cacheFile));
    }

    @Test
    void cacheFallbackOnNetworkError() throws IOException {
        Path cacheFile = tempDir.resolve("cache.json");
        AcpRegistryCache cache = new AcpRegistryCache(cacheFile);

        // Pre-populate cache
        Files.writeString(cacheFile, VALID_REGISTRY_JSON);

        // Use an unreachable URL to trigger network failure
        HttpClient failingClient = new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                    throws IOException {
                throw new IOException("Connection refused");
            }

            @Override
            public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
            @Override
            public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
            @Override
            public Redirect followRedirects() { return Redirect.NEVER; }
            @Override
            public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
            @Override
            public javax.net.ssl.SSLContext sslContext() { return null; }
            @Override
            public javax.net.ssl.SSLParameters sslParameters() { return null; }
            @Override
            public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
            @Override
            public Version version() { return Version.HTTP_1_1; }
            @Override
            public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Connection refused"));
            }
            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                    HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Connection refused"));
            }
        };

        AcpRegistryClient client = new AcpRegistryClient(
                URI.create("http://unreachable.invalid/registry.json"), cache, failingClient);

        AcpRegistryLoadResult result = client.load();

        assertInstanceOf(AcpRegistryLoadResult.Success.class, result);
        var success = (AcpRegistryLoadResult.Success) result;
        assertEquals(AcpRegistryLoadResult.Source.CACHE, success.source());
        assertEquals(2, success.registry().agents().size());
    }

    @Test
    void failureWhenNoCacheAndNetworkError() {
        Path cacheFile = tempDir.resolve("nonexistent-cache.json");
        AcpRegistryCache cache = new AcpRegistryCache(cacheFile);

        HttpClient failingClient = new HttpClient() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                    throws IOException {
                throw new IOException("Connection refused");
            }

            @Override
            public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
            @Override
            public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
            @Override
            public Redirect followRedirects() { return Redirect.NEVER; }
            @Override
            public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
            @Override
            public javax.net.ssl.SSLContext sslContext() { return null; }
            @Override
            public javax.net.ssl.SSLParameters sslParameters() { return null; }
            @Override
            public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
            @Override
            public Version version() { return Version.HTTP_1_1; }
            @Override
            public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Connection refused"));
            }
            @Override
            public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(
                    HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler,
                    HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
                return java.util.concurrent.CompletableFuture.failedFuture(new IOException("Connection refused"));
            }
        };

        AcpRegistryClient client = new AcpRegistryClient(
                URI.create("http://unreachable.invalid/registry.json"), cache, failingClient);

        AcpRegistryLoadResult result = client.load();

        assertInstanceOf(AcpRegistryLoadResult.Failure.class, result);
        var failure = (AcpRegistryLoadResult.Failure) result;
        assertTrue(failure.errors().get(0).contains("unavailable"));
        assertTrue(failure.errors().get(0).contains("no cache"));
    }

    @Test
    void registryUrlIsConfigurable() {
        URI customUrl = URI.create("https://custom.registry.example/agents.json");
        AcpRegistryCache cache = new AcpRegistryCache(tempDir.resolve("cache.json"));
        AcpRegistryClient client = new AcpRegistryClient(customUrl, cache);

        assertEquals(customUrl, client.registryUrl());
    }

    @Test
    void defaultRegistryUrlPointsToPublicRegistry() {
        assertEquals(
                URI.create("https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json"),
                AcpRegistryClient.defaultRegistryUrl());
    }

    @Test
    void cacheReadAndWrite() {
        Path cacheFile = tempDir.resolve("sub/dir/cache.json");
        AcpRegistryCache cache = new AcpRegistryCache(cacheFile);

        AcpRegistry registry = new AcpRegistry("1.0.0", List.of(
                new RegistryAgent("demo", "Demo", "1.0.0", "A demo agent", null, null, null, "MIT", null,
                        new RegistryDistribution(null,
                                new RegistryDistribution.PackageDistribution("demo@1.0.0", List.of(), null),
                                null))
        ));

        cache.write(registry);
        assertTrue(Files.exists(cacheFile));

        var loaded = cache.read();
        assertTrue(loaded.isPresent());
        assertEquals("1.0.0", loaded.get().version());
        assertEquals(1, loaded.get().agents().size());
        assertEquals("demo", loaded.get().agents().get(0).id());
    }
}
