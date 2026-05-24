package com.aresstack.pyloros.acp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public final class AgentTask {

    private static final int PROMPT_PREVIEW_LENGTH = 200;

    private final AgentTaskId taskId;
    private final String providerId;
    private String acpSessionId;
    private AgentTaskState state;
    private final String cwd;
    private final String promptPreview;
    private final String promptHash;
    private final Instant startedAt;
    private volatile Instant updatedAt;
    private Instant completedAt;
    private final List<String> events;
    private final List<PendingPermission> pendingPermissions;
    private String result;
    private String error;
    private volatile boolean cancellationRequested;
    private final int maxEvents;

    public AgentTask(
            AgentTaskId taskId,
            String providerId,
            String cwd,
            String prompt,
            int maxEvents) {
        this.taskId = Objects.requireNonNull(taskId, "taskId must not be null");
        this.providerId = requireProviderId(providerId);
        this.cwd = normalizeNullableText(cwd);
        this.promptPreview = promptPreview(prompt);
        this.promptHash = promptHash(prompt);
        this.maxEvents = requirePositive(maxEvents, "maxEvents");
        this.state = AgentTaskState.CREATED;
        this.startedAt = Instant.now();
        this.updatedAt = startedAt;
        this.events = Collections.synchronizedList(new ArrayList<>());
        this.pendingPermissions = Collections.synchronizedList(new ArrayList<>());
    }

    public AgentTaskId taskId() {
        return taskId;
    }

    public String providerId() {
        return providerId;
    }

    public synchronized String acpSessionId() {
        return acpSessionId;
    }

    public synchronized void setAcpSessionId(String acpSessionId) {
        String normalizedSessionId = requireText(acpSessionId, "acpSessionId");
        if (this.acpSessionId != null && !this.acpSessionId.equals(normalizedSessionId)) {
            throw new IllegalStateException("acpSessionId is already set");
        }
        this.acpSessionId = normalizedSessionId;
        touch();
    }

    public synchronized AgentTaskState state() {
        return state;
    }

    public String cwd() {
        return cwd;
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

    public List<String> events() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    public List<PendingPermission> pendingPermissions() {
        synchronized (pendingPermissions) {
            return List.copyOf(pendingPermissions);
        }
    }

    public synchronized String result() {
        return result;
    }

    public synchronized String error() {
        return error;
    }

    public boolean cancellationRequested() {
        return cancellationRequested;
    }

    public int maxEvents() {
        return maxEvents;
    }

    public synchronized void markRunning() {
        transitionTo(AgentTaskState.RUNNING, AgentTaskState.CREATED, AgentTaskState.WAITING_FOR_PERMISSION);
        synchronized (pendingPermissions) {
            pendingPermissions.clear();
        }
    }

    public synchronized void addEvent(String event) {
        Objects.requireNonNull(event, "event must not be null");
        synchronized (events) {
            if (events.size() == maxEvents) {
                events.remove(0);
            }
            events.add(event);
        }
        touch();
    }

    public synchronized void complete(String result) {
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.error = null;
        completeTransition(AgentTaskState.COMPLETED, AgentTaskState.RUNNING);
    }

    public synchronized void fail(String error) {
        this.error = requireText(error, "error");
        this.result = null;
        completeTransition(AgentTaskState.FAILED, AgentTaskState.RUNNING, AgentTaskState.WAITING_FOR_PERMISSION);
    }

    public synchronized void timeout() {
        this.error = "Task timed out";
        this.result = null;
        completeTransition(AgentTaskState.TIMEOUT, AgentTaskState.RUNNING, AgentTaskState.WAITING_FOR_PERMISSION);
    }

    public synchronized void cancel() {
        this.cancellationRequested = true;
        this.result = null;
        this.error = null;
        completeTransition(AgentTaskState.CANCELLED, AgentTaskState.RUNNING, AgentTaskState.WAITING_FOR_PERMISSION);
    }

    public synchronized void requestPermission(PendingPermission pendingPermission) {
        PendingPermission permission = Objects.requireNonNull(pendingPermission, "pendingPermission must not be null");
        ensureState(AgentTaskState.WAITING_FOR_PERMISSION, AgentTaskState.RUNNING);
        synchronized (pendingPermissions) {
            pendingPermissions.add(permission);
        }
        this.state = AgentTaskState.WAITING_FOR_PERMISSION;
        touch();
    }

    private void completeTransition(AgentTaskState targetState, AgentTaskState... allowedStates) {
        ensureState(targetState, allowedStates);
        this.state = targetState;
        this.completedAt = Instant.now();
        synchronized (pendingPermissions) {
            pendingPermissions.clear();
        }
        touch(this.completedAt);
    }

    private void transitionTo(AgentTaskState targetState, AgentTaskState... allowedStates) {
        ensureState(targetState, allowedStates);
        this.state = targetState;
        touch();
    }

    private void ensureState(AgentTaskState targetState, AgentTaskState... allowedStates) {
        for (AgentTaskState allowedState : allowedStates) {
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

    private static String requireProviderId(String providerId) {
        return requireText(providerId, "providerId");
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be greater than 0");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalizedValue = value.trim();
        if (normalizedValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalizedValue;
    }

    private static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
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
