package com.aresstack.pyloros.manageragent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManagerAgentSessionState {

    private final Set<String> sessionIds = ConcurrentHashMap.newKeySet();

    String createSessionId() {
        String sessionId = UUID.randomUUID().toString();
        sessionIds.add(sessionId);
        return sessionId;
    }

    boolean containsSessionId(String sessionId) {
        return sessionIds.contains(sessionId);
    }
}
