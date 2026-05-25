package com.aresstack.pyloros.langchain;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for LangChain sessions/tasks.
 *
 * <p>For Release 5 only an in-memory implementation is required;
 * data loss on restart is acceptable (see issue R5-09).
 */
public interface LangChainSessionRepository {

    void save(LangChainTask task);

    Optional<LangChainTask> findById(LangChainTaskId taskId);

    List<LangChainTask> findByProviderId(String providerId);
}
