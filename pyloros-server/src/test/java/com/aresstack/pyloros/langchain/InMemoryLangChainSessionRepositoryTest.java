package com.aresstack.pyloros.langchain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryLangChainSessionRepositoryTest {

    @Test
    void testSaveAndFindById() {
        InMemoryLangChainSessionRepository repository = new InMemoryLangChainSessionRepository();
        LangChainTask task = newTask("pyloros-ai", "Prompt");

        repository.save(task);

        assertTrue(repository.findById(task.taskId()).isPresent());
        assertEquals(task, repository.findById(task.taskId()).orElseThrow());
    }

    @Test
    void testFindByIdNotFound() {
        InMemoryLangChainSessionRepository repository = new InMemoryLangChainSessionRepository();

        assertTrue(repository.findById(LangChainTaskId.generate()).isEmpty());
    }

    @Test
    void testFindByProviderId() {
        InMemoryLangChainSessionRepository repository = new InMemoryLangChainSessionRepository();
        LangChainTask first = newTask("pyloros-ai", "First");
        LangChainTask second = newTask("pyloros-ai", "Second");
        LangChainTask other = newTask("other", "Third");
        repository.save(first);
        repository.save(second);
        repository.save(other);

        List<LangChainTask> tasks = repository.findByProviderId("pyloros-ai");

        assertEquals(2, tasks.size());
        assertTrue(tasks.contains(first));
        assertTrue(tasks.contains(second));
    }

    @Test
    void testSaveOverwritesByTaskId() {
        InMemoryLangChainSessionRepository repository = new InMemoryLangChainSessionRepository();
        LangChainTask task = newTask("pyloros-ai", "Prompt");
        repository.save(task);

        task.markRunning();
        task.complete("Done", LangChainStopReason.COMPLETED);
        repository.save(task);

        LangChainTask stored = repository.findById(task.taskId()).orElseThrow();
        assertEquals(LangChainTaskState.COMPLETED, stored.state());
        assertEquals("Done", stored.result());
    }

    private static LangChainTask newTask(String providerId, String prompt) {
        return new LangChainTask(LangChainTaskId.generate(), providerId, prompt);
    }
}
