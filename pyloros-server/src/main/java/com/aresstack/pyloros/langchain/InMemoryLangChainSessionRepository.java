package com.aresstack.pyloros.langchain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryLangChainSessionRepository implements LangChainSessionRepository {

    private final ConcurrentHashMap<LangChainTaskId, LangChainTask> tasks = new ConcurrentHashMap<>();

    @Override
    public void save(LangChainTask task) {
        LangChainTask langChainTask = Objects.requireNonNull(task, "task must not be null");
        tasks.put(langChainTask.taskId(), langChainTask);
    }

    @Override
    public Optional<LangChainTask> findById(LangChainTaskId taskId) {
        return Optional.ofNullable(tasks.get(Objects.requireNonNull(taskId, "taskId must not be null")));
    }

    @Override
    public List<LangChainTask> findByProviderId(String providerId) {
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
