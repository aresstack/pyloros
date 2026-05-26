package com.aresstack.pyloros.manageragent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class LineDelimitedJsonRpcTransport implements ManagerAgentResponseEmitter {

    private final ObjectMapper objectMapper;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();

    LineDelimitedJsonRpcTransport(ObjectMapper objectMapper, InputStream inputStream, OutputStream outputStream) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
    }

    String readLine() throws IOException {
        return reader.readLine();
    }

    @Override
    public void sendResult(JsonNode id, JsonNode result) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        write(response);
    }

    @Override
    public void sendError(JsonNode id, int code, String message) throws IOException {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("code", code);
        error.put("message", message);

        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        if (id == null || id.isNull()) {
            response.putNull("id");
        } else {
            response.set("id", id);
        }
        response.set("error", error);
        write(response);
    }

    @Override
    public void sendSessionUpdate(ObjectNode params) throws IOException {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("method", "session/update");
        response.set("params", params);
        write(response);
    }

    private void write(JsonNode payload) throws IOException {
        synchronized (writeLock) {
            writer.write(objectMapper.writeValueAsString(payload));
            writer.newLine();
            writer.flush();
        }
    }
}
