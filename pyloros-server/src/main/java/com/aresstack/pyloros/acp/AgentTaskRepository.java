package com.aresstack.pyloros.acp;

import java.util.List;
import java.util.Optional;

public interface AgentTaskRepository {

    void save(AgentTask agentTask);

    Optional<AgentTask> findById(AgentTaskId taskId);

    List<AgentTask> findByProviderId(String providerId);
}
