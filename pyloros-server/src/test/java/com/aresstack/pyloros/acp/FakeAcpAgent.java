package com.aresstack.pyloros.acp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fake ACP agent for integration tests. Runs in-process using PipedInputStream/PipedOutputStream
 * to simulate stdin/stdout communication without launching a real process.
 */
public final class FakeAcpAgent implements AutoCloseable {

    private static final String SESSION_ID = "fake-session-1";

    private final java.io.PipedInputStream clientReadsFrom;
    private final java.io.PipedOutputStream agentWritesTo;
    private final java.io.PipedInputStream agentReadsFrom;
    private final java.io.PipedOutputStream clientWritesTo;
    private final ExecutorService executor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicReference<JsonNode> lastSessionNewParams = new AtomicReference<>();
    private volatile String behavior = "success";

    public FakeAcpAgent() throws IOException {
        this.clientReadsFrom = new java.io.PipedInputStream(64 * 1024);
        this.agentWritesTo = new java.io.PipedOutputStream(clientReadsFrom);
        this.agentReadsFrom = new java.io.PipedInputStream(64 * 1024);
        this.clientWritesTo = new java.io.PipedOutputStream(agentReadsFrom);
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "fake-acp-agent");
            thread.setDaemon(true);
            return thread;
        });
    }

    public InputStream clientInputStream() {
        return clientReadsFrom;
    }

    public OutputStream clientOutputStream() {
        return clientWritesTo;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }

    public void start() {
        executor.submit(this::agentLoop);
    }

    public AcpProcessHandle processHandle() {
        return new AcpProcessHandle(new FakeProcess());
    }

    public JsonNode lastSessionNewParams() {
        return lastSessionNewParams.get();
    }

    private void agentLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(agentReadsFrom, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(agentWritesTo, StandardCharsets.UTF_8))) {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                JsonNode request = mapper.readTree(line);
                String method = text(request, "method");
                if ("unresponsive".equals(behavior)) {
                    // Intentionally do not respond to any requests
                    continue;
                }
                if ("session/new".equals(method)) {
                    lastSessionNewParams.set(request.get("params"));
                    sendResult(writer, request.get("id").asText(), mapper.createObjectNode().put("sessionId", SESSION_ID));
                } else if ("session/prompt".equals(method)) {
                    sendResult(writer, request.get("id").asText(), mapper.createObjectNode().put("accepted", true));
                    sendBehaviorEvents(writer, request.path("params").path("prompt").asText());
                }
            }
        } catch (IOException ignored) {
            if (!closed.get()) {
                throw new IllegalStateException("Fake ACP agent failed", ignored);
            }
        }
    }

    private void sendBehaviorEvents(BufferedWriter writer, String prompt) throws IOException {
        switch (behavior) {
            case "success" -> {
                sendSessionEvent(writer, mapper.createObjectNode()
                        .put("type", "text")
                        .put("text", "processing: " + prompt));
                sendSessionEvent(writer, mapper.createObjectNode()
                        .put("type", "completion")
                        .put("result", "completed: " + prompt));
            }
            case "error" -> sendSessionEvent(writer, mapper.createObjectNode()
                    .put("type", "error")
                    .put("message", "simulated failure"));
            case "timeout" -> {
                // Intentionally emit no session/update events.
            }
            case "permission" -> sendSessionEvent(writer, mapper.createObjectNode()
                    .put("type", "permission_request")
                    .put("permissionId", "perm-1")
                    .put("description", "Approve access"));
            case "large_output" -> {
                for (int index = 0; index < 50; index++) {
                    sendSessionEvent(writer, mapper.createObjectNode()
                            .put("type", "text")
                            .put("text", "chunk-" + index));
                }
                sendSessionEvent(writer, mapper.createObjectNode()
                        .put("type", "completion")
                        .put("result", "large-output-complete"));
            }
            default -> throw new IllegalStateException("Unknown fake ACP behavior: " + behavior);
        }
    }

    private void sendResult(BufferedWriter writer, String id, ObjectNode result) throws IOException {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.set("result", result);
        writer.write(mapper.writeValueAsString(response));
        writer.newLine();
        writer.flush();
    }

    private void sendSessionEvent(BufferedWriter writer, ObjectNode params) throws IOException {
        params.put("sessionId", SESSION_ID);
        ObjectNode event = mapper.createObjectNode();
        event.put("jsonrpc", "2.0");
        event.put("method", "session/update");
        event.set("params", params);
        writer.write(mapper.writeValueAsString(event));
        writer.newLine();
        writer.flush();
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        return value == null || value.isNull() ? null : value.asText();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            clientWritesTo.close();
        } catch (IOException ignored) {
        }
        try {
            agentWritesTo.close();
        } catch (IOException ignored) {
        }
        try {
            agentReadsFrom.close();
        } catch (IOException ignored) {
        }
        try {
            clientReadsFrom.close();
        } catch (IOException ignored) {
        }
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private final class FakeProcess extends Process {

        private final AtomicBoolean alive = new AtomicBoolean(true);

        @Override
        public OutputStream getOutputStream() {
            return clientWritesTo;
        }

        @Override
        public InputStream getInputStream() {
            return clientReadsFrom;
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() throws InterruptedException {
            while (alive.get()) {
                Thread.sleep(10L);
            }
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (alive.get() && System.nanoTime() < deadline) {
                Thread.sleep(10L);
            }
            return !alive.get();
        }

        @Override
        public int exitValue() {
            if (alive.get()) {
                throw new IllegalThreadStateException("Process is still running");
            }
            return 0;
        }

        @Override
        public void destroy() {
            if (alive.compareAndSet(true, false)) {
                FakeAcpAgent.this.close();
            }
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive.get() && !closed.get();
        }
    }
}
