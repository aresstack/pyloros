package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AcpJsonRpcConnection implements Closeable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final ExecutorService readExecutor;
    private final Object writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public AcpJsonRpcConnection(InputStream inputStream, OutputStream outputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        Objects.requireNonNull(outputStream, "outputStream must not be null");
        this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        this.readExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "acp-json-rpc-reader");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void sendRequest(String method, Object params, String id) {
        writeMessage(request(method, params, requireText(id, "id")));
    }

    public void sendNotification(String method, Object params) {
        writeMessage(notification(method, params));
    }

    public CompletableFuture<JsonNode> readNext() {
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("ACP JSON-RPC connection is closed"));
        }
        return CompletableFuture.supplyAsync(this::readNextNode, readExecutor);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        readExecutor.shutdownNow();

        UncheckedIOException failure = null;
        failure = close(writer::close, failure);
        failure = close(reader::close, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private JsonNode readNextNode() {
        try {
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalStateException("ACP JSON-RPC stream closed");
            }
            return OBJECT_MAPPER.readTree(line);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeMessage(ObjectNode message) {
        Objects.requireNonNull(message, "message must not be null");
        if (closed.get()) {
            throw new IllegalStateException("ACP JSON-RPC connection is closed");
        }

        synchronized (writeLock) {
            try {
                writer.write(OBJECT_MAPPER.writeValueAsString(message));
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private static ObjectNode request(String method, Object params, String id) {
        ObjectNode payload = baseMessage(method, params);
        payload.put("id", id);
        return payload;
    }

    private static ObjectNode notification(String method, Object params) {
        return baseMessage(method, params);
    }

    private static ObjectNode baseMessage(String method, Object params) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("jsonrpc", "2.0");
        payload.put("method", requireText(method, "method"));
        payload.set("params", OBJECT_MAPPER.valueToTree(params));
        return payload;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static UncheckedIOException close(IoCloseable ioCloseable, UncheckedIOException failure) {
        try {
            ioCloseable.close();
            return failure;
        } catch (IOException exception) {
            return failure == null ? new UncheckedIOException(exception) : failure;
        }
    }

    @FunctionalInterface
    private interface IoCloseable {
        void close() throws IOException;
    }
}
