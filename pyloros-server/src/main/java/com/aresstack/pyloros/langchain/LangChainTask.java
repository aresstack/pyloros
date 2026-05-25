package com.aresstack.pyloros.langchain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate representing a single {@code pyloros-ai/ask} invocation (or longer
 * agent task) as a traceable LangChain session.
 *
 * <p>The full prompt is intentionally never persisted; only a truncated preview
 * and a SHA-256 hash are kept. Used tools, the stop reason and either a result
 * or an error are recorded so that the run can be audited later.
 */
public final class LangChainTask {

    private static final int PROMPT_PREVIEW_LENGTH = 200;

    private final LangChainTaskId taskId;
    private final String providerId;
    private LangChainTaskState state;
    private final String promptPreview;
    private final String promptHash;
    private final Instant startedAt;
    private volatile Instant updatedAt;
    private Instant completedAt;
    private final List<LangChainToolCall> usedTools;
    private LangChainStopReason stopReason;
    private String result;
    private String error;

    public LangChainTask(
            LangChainTaskId taskId,
            String providerId,
            String prompt) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.providerId = requireText(providerId, "providerId");
        this.promptPreview = promptPreview(prompt);
        this.promptHash = promptHash(prompt);
        this.state = LangChainTaskState.CREATED;
        this.startedAt = Instant.now();
        this.updatedAt = startedAt;
        this.usedTools = Collections.synchronizedList(new ArrayList<>());
    }

    public LangChainTaskId taskId() {
        return taskId;
    }

    public String providerId() {
        return providerId;
    }

    public synchronized LangChainTaskState state() {
        return state;
    }

    public String promptPreview() {
        return promptPreview;
    }

    public String promptHash() {
        return promptHash;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public synchronized Instant completedAt() {
        return completedAt;
    }

    public List<LangChainToolCall> usedTools() {
        synchronized (usedTools) {
            return List.copyOf(usedTools);
        }
    }

    public synchronized LangChainStopReason stopReason() {
        return stopReason;
    }

    public synchronized String result() {
        return result;
    }

    public synchronized String error() {
        return error;
    }

    public synchronized void markRunning() {
        ensureState(LangChainTaskState.RUNNING, LangChainTaskState.CREATED);
        this.state = LangChainTaskState.RUNNING;
        touch();
    }

    public synchronized void addToolCall(LangChainToolCall toolCall) {
        Objects.requireNonNull(toolCall, "toolCall must not be null");
        synchronized (usedTools) {
            usedTools.add(toolCall);
        }
        touch();
    }

    public synchronized void complete(String result, LangChainStopReason stopReason) {
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.error = null;
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        completeTransition(LangChainTaskState.COMPLETED, LangChainTaskState.RUNNING, LangChainTaskState.CREATED);
    }

    public synchronized void fail(String error, LangChainStopReason stopReason) {
        this.error = requireText(error, "error");
        this.result = null;
        this.stopReason = Objects.requireNonNull(stopReason, "stopReason must not be null");
        completeTransition(LangChainTaskState.FAILED, LangChainTaskState.RUNNING, LangChainTaskState.CREATED);
    }

    public synchronized void timeout() {
        this.error = "Task timed out";
        this.result = null;
        this.stopReason = LangChainStopReason.TIMEOUT;
        completeTransition(LangChainTaskState.TIMEOUT, LangChainTaskState.RUNNING, LangChainTaskState.CREATED);
    }

    public synchronized void cancel() {
        this.result = null;
        this.error = null;
        completeTransition(LangChainTaskState.CANCELLED, LangChainTaskState.RUNNING, LangChainTaskState.CREATED);
    }

    private void completeTransition(LangChainTaskState targetState, LangChainTaskState... allowedStates) {
        ensureState(targetState, allowedStates);
        this.state = targetState;
        this.completedAt = Instant.now();
        touch(this.completedAt);
    }

    private void ensureState(LangChainTaskState targetState, LangChainTaskState... allowedStates) {
        for (LangChainTaskState allowedState : allowedStates) {
            if (state == allowedState) {
                return;
            }
        }
        throw new IllegalStateException("Cannot transition task from " + state + " to " + targetState);
    }

    private void touch() {
        touch(Instant.now());
    }

    private void touch(Instant timestamp) {
        this.updatedAt = timestamp;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String promptPreview(String prompt) {
        String promptText = Objects.requireNonNull(prompt, "prompt must not be null");
        return promptText.length() <= PROMPT_PREVIEW_LENGTH
                ? promptText
                : promptText.substring(0, PROMPT_PREVIEW_LENGTH);
    }

    private static String promptHash(String prompt) {
        Objects.requireNonNull(prompt, "prompt must not be null");
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(prompt.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
