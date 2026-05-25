package com.aresstack.pyloros.langchain;

import com.aresstack.pyloros.provider.ProviderStatus;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OllamaModelHealthCheckTest {

    @SuppressWarnings("unchecked")
    @Test
    void returnsAvailableWhenOllamaResponds200() throws Exception {
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var healthCheck = new OllamaModelHealthCheck("http://localhost:11434", httpClient);

        assertEquals(ProviderStatus.AVAILABLE, healthCheck.check());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsUnavailableWhenOllamaReturns500() throws Exception {
        var httpClient = mock(HttpClient.class);
        var response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(500);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        var healthCheck = new OllamaModelHealthCheck("http://localhost:11434", httpClient);

        assertEquals(ProviderStatus.UNAVAILABLE, healthCheck.check());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsUnavailableWhenConnectionFails() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Connection refused"));

        var healthCheck = new OllamaModelHealthCheck("http://localhost:11434", httpClient);

        assertEquals(ProviderStatus.UNAVAILABLE, healthCheck.check());
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsUnavailableOnTimeout() throws Exception {
        var httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.net.http.HttpTimeoutException("request timed out"));

        var healthCheck = new OllamaModelHealthCheck("http://localhost:11434", httpClient);

        assertEquals(ProviderStatus.UNAVAILABLE, healthCheck.check());
    }

    @Test
    void rejectsNullBaseUrl() {
        assertThrows(NullPointerException.class, () -> new OllamaModelHealthCheck(null));
    }
}
