package com.aresstack.pyloros.langchain;

import com.aresstack.pyloros.provider.ProviderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Health check for Ollama model availability.
 * Probes the Ollama API endpoint to determine if the service is reachable.
 */
public final class OllamaModelHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(OllamaModelHealthCheck.class);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final HttpClient httpClient;

    public OllamaModelHealthCheck(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder()
                .connectTimeout(HEALTH_CHECK_TIMEOUT)
                .build());
    }

    OllamaModelHealthCheck(String baseUrl, HttpClient httpClient) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    }

    /**
     * Checks if the Ollama service is reachable.
     *
     * @return {@link ProviderStatus#AVAILABLE} if Ollama responds successfully,
     *         {@link ProviderStatus#UNAVAILABLE} otherwise
     */
    public ProviderStatus check() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .GET()
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() >= 200 && response.statusCode() < 400) {
                log.debug("Ollama health check passed: baseUrl={}", baseUrl);
                return ProviderStatus.AVAILABLE;
            } else {
                log.warn("Ollama health check failed: baseUrl={}, status={}", baseUrl, response.statusCode());
                return ProviderStatus.UNAVAILABLE;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Ollama health check interrupted: baseUrl={}", baseUrl);
            return ProviderStatus.UNAVAILABLE;
        } catch (Exception e) {
            log.warn("Ollama health check failed: baseUrl={}, error={}", baseUrl, e.getMessage());
            return ProviderStatus.UNAVAILABLE;
        }
    }
}
