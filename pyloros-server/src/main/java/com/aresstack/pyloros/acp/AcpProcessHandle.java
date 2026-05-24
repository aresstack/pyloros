package com.aresstack.pyloros.acp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class AcpProcessHandle {

    private static final int STDERR_BUFFER_LIMIT = 4096;
    private static final long DESTROY_TIMEOUT_SECONDS = 5L;

    private final Process process;
    private final InputStream stdout;
    private final OutputStream stdin;
    private final Object stderrLock = new Object();
    private final StringBuilder stderrCollector = new StringBuilder();

    public AcpProcessHandle(Process process) {
        this.process = Objects.requireNonNull(process, "process must not be null");
        this.stdout = process.getInputStream();
        this.stdin = process.getOutputStream();
        startStderrCollector(process.getErrorStream());
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public void destroy() {
        if (!process.isAlive()) {
            return;
        }

        process.destroy();
        if (awaitExit()) {
            return;
        }

        process.destroyForcibly();
        awaitExit();
    }

    public String collectStderr(int maxChars) {
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars must not be negative");
        }
        if (maxChars == 0) {
            return "";
        }

        synchronized (stderrLock) {
            int length = stderrCollector.length();
            if (length <= maxChars) {
                return stderrCollector.toString();
            }
            return stderrCollector.substring(length - maxChars);
        }
    }

    public InputStream stdout() {
        return stdout;
    }

    public OutputStream stdin() {
        return stdin;
    }

    private void startStderrCollector(InputStream stderr) {
        Thread stderrThread = new Thread(() -> readStderr(stderr), "acp-process-stderr");
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private void readStderr(InputStream stderr) {
        try (InputStream stream = stderr;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[512];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                appendStderr(buffer, read);
            }
        } catch (IOException ignored) {
            if (process.isAlive()) {
                appendStderr(("[stderr collector failed: " + ignored.getMessage() + "]").toCharArray(),
                        ("[stderr collector failed: " + ignored.getMessage() + "]").length());
            }
        }
    }

    private void appendStderr(char[] content, int length) {
        synchronized (stderrLock) {
            stderrCollector.append(content, 0, length);
            int overflow = stderrCollector.length() - STDERR_BUFFER_LIMIT;
            if (overflow > 0) {
                stderrCollector.delete(0, overflow);
            }
        }
    }

    private boolean awaitExit() {
        try {
            return process.waitFor(DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return false;
        }
    }
}
