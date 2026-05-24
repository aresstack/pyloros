package com.aresstack.pyloros.acp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class AcpAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AcpAuditLogger.class);

    public void logTaskStarted(AgentTask task) {
        AgentTask agentTask = Objects.requireNonNull(task, "task must not be null");
        log.info("event=acp_task_started taskId={} providerId={} state={} promptHash={} promptPreview={} cwd={}",
                agentTask.taskId().value(),
                quote(agentTask.providerId()),
                agentTask.state(),
                quote(agentTask.promptHash()),
                quote(agentTask.promptPreview()),
                quote(agentTask.cwd()));
    }

    public void logTaskCompleted(AgentTask task, long durationMs) {
        AgentTask agentTask = Objects.requireNonNull(task, "task must not be null");
        log.info("event=acp_task_completed taskId={} providerId={} state={} durationMs={} promptHash={} promptPreview={}",
                agentTask.taskId().value(),
                quote(agentTask.providerId()),
                agentTask.state(),
                durationMs,
                quote(agentTask.promptHash()),
                quote(agentTask.promptPreview()));
    }

    public void logTaskFailed(AgentTask task, long durationMs, String errorType) {
        AgentTask agentTask = Objects.requireNonNull(task, "task must not be null");
        String normalizedErrorType = requireText(errorType, "errorType");
        log.warn("event=acp_task_failed taskId={} providerId={} state={} durationMs={} errorType={} promptHash={} promptPreview={}",
                agentTask.taskId().value(),
                quote(agentTask.providerId()),
                agentTask.state(),
                durationMs,
                quote(normalizedErrorType),
                quote(agentTask.promptHash()),
                quote(agentTask.promptPreview()));
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + '"';
    }
}
