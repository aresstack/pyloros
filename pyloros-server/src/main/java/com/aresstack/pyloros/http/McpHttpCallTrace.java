package com.aresstack.pyloros.http;

import io.vertx.core.AsyncResult;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Observes the downstream HTTP lifecycle for one MCP tool call.
 *
 * <p>Only metadata is logged: no request arguments, tool output, authorization headers or
 * JSON-RPC bodies. A successful Vert.x end future means the local write completed; it does not
 * prove that Caddy or ChatGPT received or processed the response.
 */
final class McpHttpCallTrace {

    static final String RESPONSE_HEADER = "X-Pyloros-Trace-Id";
    private static final String CONTEXT_KEY = McpHttpCallTrace.class.getName();
    private static final Logger log = LoggerFactory.getLogger(McpHttpCallTrace.class);

    private final String traceId = UUID.randomUUID().toString();
    private final String toolName;
    private final long startedAtNanos = System.nanoTime();
    private final AtomicBoolean endSubmitted = new AtomicBoolean();
    private final AtomicBoolean endCompleted = new AtomicBoolean();
    private final AtomicBoolean closeObserved = new AtomicBoolean();

    private McpHttpCallTrace(String toolName) {
        this.toolName = toolName;
    }

    static McpHttpCallTrace begin(RoutingContext context, String toolName) {
        McpHttpCallTrace trace = new McpHttpCallTrace(toolName);
        context.put(CONTEXT_KEY, trace);
        context.response().putHeader(RESPONSE_HEADER, trace.traceId);
        context.response().closeHandler(ignored -> trace.connectionClosed());
        context.response().exceptionHandler(trace::responseException);
        log.info("[MCP-HTTP] trace={} event=received tool={}", trace.traceId, trace.toolName);
        return trace;
    }

    static McpHttpCallTrace from(RoutingContext context) {
        return context.get(CONTEXT_KEY);
    }

    void upstreamCompleted(boolean success) {
        log.info("[MCP-HTTP] trace={} event=upstream_completed tool={} outcome={} elapsedMs={}",
                traceId, toolName, success ? "success" : "failure", elapsedMillis());
    }

    void responseEndSubmitted(int status, String payload) {
        endSubmitted.set(true);
        int payloadBytes = payload == null ? 0 : payload.getBytes(StandardCharsets.UTF_8).length;
        log.info("[MCP-HTTP] trace={} event=response_end_submitted tool={} status={} bytes={} elapsedMs={}",
                traceId, toolName, status, payloadBytes, elapsedMillis());
    }

    void responseEndResult(int status, AsyncResult<Void> result) {
        if (result.succeeded()) {
            endCompleted.set(true);
            log.info("[MCP-HTTP] trace={} event=response_end_succeeded tool={} status={} elapsedMs={}",
                    traceId, toolName, status, elapsedMillis());
        } else {
            log.warn("[MCP-HTTP] trace={} event=response_end_failed tool={} status={} cause={} elapsedMs={}",
                    traceId, toolName, status, result.cause().getClass().getSimpleName(), elapsedMillis());
        }
    }

    void writeException(Throwable error) {
        log.warn("[MCP-HTTP] trace={} event=response_write_exception tool={} cause={} elapsedMs={}",
                traceId, toolName, error.getClass().getSimpleName(), elapsedMillis());
    }

    private void responseException(Throwable error) {
        log.warn("[MCP-HTTP] trace={} event=response_exception tool={} cause={} elapsedMs={}",
                traceId, toolName, error.getClass().getSimpleName(), elapsedMillis());
    }

    private void connectionClosed() {
        if (closeObserved.compareAndSet(false, true) && !endCompleted.get()) {
            log.warn("[MCP-HTTP] trace={} event=connection_closed_before_end tool={} endSubmitted={} elapsedMs={}",
                    traceId, toolName, endSubmitted.get(), elapsedMillis());
        }
    }

    private long elapsedMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
