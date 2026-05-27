package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Client for loading, validating, and caching the public ACP agent registry.
 * Falls back to the local cache when the remote registry is unreachable.
 */
public final class AcpRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(AcpRegistryClient.class);
    private static final String DEFAULT_REGISTRY_URL =
            "https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);

    private final URI registryUrl;
    private final AcpRegistryCache cache;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AcpRegistryClient(URI registryUrl, AcpRegistryCache cache) {
        this(registryUrl, cache, HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build());
    }

    public AcpRegistryClient(URI registryUrl, AcpRegistryCache cache, HttpClient httpClient) {
        this.registryUrl = Objects.requireNonNull(registryUrl, "registryUrl must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public static URI defaultRegistryUrl() {
        return URI.create(DEFAULT_REGISTRY_URL);
    }

    public URI registryUrl() {
        return registryUrl;
    }

    /**
     * Loads the ACP registry from the configured URL.
     * On success, the registry is validated and cached locally.
     * On network failure, falls back to the cached version if available.
     */
    public AcpRegistryLoadResult load() {
        try {
            return loadFromRemote();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to load ACP registry from {}: {}", registryUrl, e.getMessage());
            return fallbackToCache(e.getMessage());
        }
    }

    /**
     * Loads the registry from an input stream (useful for testing with local resources).
     */
    public AcpRegistryLoadResult loadFrom(InputStream inputStream) {
        try {
            AcpRegistry registry = objectMapper.readValue(inputStream, AcpRegistry.class);
            List<String> errors = validate(registry);
            if (!errors.isEmpty()) {
                return new AcpRegistryLoadResult.Failure(errors);
            }
            cache.write(registry);
            return new AcpRegistryLoadResult.Success(registry, AcpRegistryLoadResult.Source.REMOTE);
        } catch (IOException e) {
            return new AcpRegistryLoadResult.Failure("Failed to parse registry JSON: " + e.getMessage());
        }
    }

    private AcpRegistryLoadResult loadFromRemote() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(registryUrl)
                .timeout(DEFAULT_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            String msg = "Registry returned HTTP " + response.statusCode();
            log.warn("{} from {}", msg, registryUrl);
            return fallbackToCache(msg);
        }

        try (InputStream body = response.body()) {
            AcpRegistry registry = objectMapper.readValue(body, AcpRegistry.class);
            List<String> errors = validate(registry);
            if (!errors.isEmpty()) {
                return new AcpRegistryLoadResult.Failure(errors);
            }
            cache.write(registry);
            return new AcpRegistryLoadResult.Success(registry, AcpRegistryLoadResult.Source.REMOTE);
        }
    }

    private AcpRegistryLoadResult fallbackToCache(String remoteError) {
        Optional<AcpRegistry> cached = cache.read();
        if (cached.isPresent()) {
            List<String> errors = validate(cached.get());
            if (!errors.isEmpty()) {
                log.warn("Cached ACP registry is invalid: {}", errors);
                return new AcpRegistryLoadResult.Failure(
                        "Remote registry unavailable (" + remoteError + ") and cached registry is invalid");
            }
            log.info("Using cached ACP registry as fallback");
            return new AcpRegistryLoadResult.Success(cached.get(), AcpRegistryLoadResult.Source.CACHE);
        }
        return new AcpRegistryLoadResult.Failure(
                "Remote registry unavailable (" + remoteError + ") and no cache available");
    }

    private List<String> validate(AcpRegistry registry) {
        List<String> errors = new ArrayList<>();
        if (registry == null) {
            errors.add("Registry is null");
            return errors;
        }
        if (registry.version() == null || registry.version().isBlank()) {
            errors.add("Registry version is missing");
        }
        if (registry.agents() == null) {
            errors.add("Registry agents list is missing");
            return errors;
        }
        for (int i = 0; i < registry.agents().size(); i++) {
            RegistryAgent agent = registry.agents().get(i);
            if (agent.id() == null || agent.id().isBlank()) {
                errors.add("Agent at index " + i + " has no id");
            }
            if (agent.name() == null || agent.name().isBlank()) {
                errors.add("Agent at index " + i + " has no name");
            }
            if (agent.version() == null || agent.version().isBlank()) {
                errors.add("Agent at index " + i + " has no version");
            }
            if (agent.description() == null || agent.description().isBlank()) {
                errors.add("Agent at index " + i + " has no description");
            }
            if (agent.distribution() == null) {
                errors.add("Agent at index " + i + " has no distribution");
            }
        }
        return errors;
    }
}
