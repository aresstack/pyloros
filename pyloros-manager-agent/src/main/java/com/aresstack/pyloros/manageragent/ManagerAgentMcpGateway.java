package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

interface ManagerAgentMcpGateway {

    JsonNode toolsList(McpServer server) throws IOException;

    JsonNode toolsCall(McpServer server, String toolName, JsonNode arguments) throws IOException;

    record McpServer(String url, Map<String, String> headers) {
        public McpServer {
            Objects.requireNonNull(url, "url must not be null");
            url = url.trim();
            if (url.isEmpty()) {
                throw new IllegalArgumentException("url must not be blank");
            }
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }

    final class HttpManagerAgentMcpGateway implements ManagerAgentMcpGateway {

        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        HttpManagerAgentMcpGateway(ObjectMapper objectMapper) {
            this(objectMapper, HttpClient.newHttpClient());
        }

        HttpManagerAgentMcpGateway(ObjectMapper objectMapper, HttpClient httpClient) {
            this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        }

        @Override
        public JsonNode toolsList(McpServer server) throws IOException {
            return rpc(server, "tools/list", objectMapper.createObjectNode(), "mgr-list");
        }

        @Override
        public JsonNode toolsCall(McpServer server, String toolName, JsonNode arguments) throws IOException {
            if (toolName == null || toolName.isBlank()) {
                throw new IllegalArgumentException("toolName must not be blank");
            }
            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", toolName);
            params.set("arguments", arguments == null || arguments.isNull() ? objectMapper.createObjectNode() : arguments);
            return rpc(server, "tools/call", params, "mgr-call");
        }

        private JsonNode rpc(McpServer server, String method, JsonNode params, String id) throws IOException {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            payload.put("id", id);
            payload.put("method", method);
            payload.set("params", params);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(server.url()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));

            for (Map.Entry<String, String> entry : server.headers().entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            HttpResponse<String> response;
            try {
                response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while calling injected MCP endpoint", exception);
            }

            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() != 200) {
                throw new IOException("Injected MCP endpoint returned HTTP " + response.statusCode());
            }
            if (body.has("error")) {
                throw new IOException("Injected MCP endpoint returned error: " + body.get("error"));
            }
            return body;
        }
    }

    static Map<String, String> readHeaders(JsonNode headersNode) {
        if (headersNode == null || headersNode.isNull() || !headersNode.isObject()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headersNode.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value != null && value.isTextual()) {
                headers.put(entry.getKey(), value.asText());
            }
        });
        return Map.copyOf(headers);
    }
}
