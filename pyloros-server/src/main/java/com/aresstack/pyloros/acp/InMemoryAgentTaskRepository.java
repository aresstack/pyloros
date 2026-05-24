package com.aresstack.pyloros.acp;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAgentTaskRepository implements AgentTaskRepository {

    private final ConcurrentHashMap<AgentTaskId, AgentTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(AgentTask agentTask) {
        AgentTask task = Objects.requireNonNull(agentTask, "agentTask must not be null");
        tasks.put(task.taskId(), task);
    }

    @Override
    public Optional<AgentTask> findById(AgentTaskId taskId) {
        return Optional.ofNullable(tasks.get(Objects.requireNonNull(taskId, "taskId must not be null")));
    }

    @Override
    public List<AgentTask> findByProviderId(String providerId) {
        String normalizedProviderId = requireProviderId(providerId);
        return tasks.values().stream()
                .filter(task -> task.providerId().equals(normalizedProviderId))
                .toList();
    }

    private static String requireProviderId(String providerId) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        String normalizedProviderId = providerId.trim();
        if (normalizedProviderId.isEmpty()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        return normalizedProviderId;
    }
}
