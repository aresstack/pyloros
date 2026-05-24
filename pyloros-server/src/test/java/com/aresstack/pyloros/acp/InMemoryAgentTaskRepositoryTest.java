package com.aresstack.pyloros.acp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryAgentTaskRepositoryTest {

    @Test
    void testSaveAndFindById() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AgentTask task = newTask("copilot", "Prompt");

        repository.save(task);

        assertTrue(repository.findById(task.taskId()).isPresent());
        assertEquals(task, repository.findById(task.taskId()).orElseThrow());
    }

    @Test
    void testFindByIdNotFound() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();

        assertTrue(repository.findById(AgentTaskId.generate()).isEmpty());
    }

    @Test
    void testFindByProviderId() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        AgentTask first = newTask("copilot", "First");
        AgentTask second = newTask("copilot", "Second");
        AgentTask other = newTask("other", "Third");
        repository.save(first);
        repository.save(second);
        repository.save(other);

        List<AgentTask> tasks = repository.findByProviderId("copilot");

        assertEquals(2, tasks.size());
        assertTrue(tasks.contains(first));
        assertTrue(tasks.contains(second));
    }

    @Test
    void testFindByProviderIdEmpty() {
        InMemoryAgentTaskRepository repository = new InMemoryAgentTaskRepository();
        repository.save(newTask("copilot", "Prompt"));

        assertTrue(repository.findByProviderId("missing").isEmpty());
    }

    private static AgentTask newTask(String providerId, String prompt) {
        return new AgentTask(AgentTaskId.generate(), providerId, "/workspace", prompt, 5);
    }
}
