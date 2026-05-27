package com.aresstack.pyloros.manageragent;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ManagerAgentSessionState {

    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    String createSessionId(SessionContext sessionContext) {
        Objects.requireNonNull(sessionContext, "sessionContext must not be null");
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, sessionContext);
        return sessionId;
    }

    boolean containsSessionId(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    SessionContext session(String sessionId) {
        return sessions.get(sessionId);
    }

    record SessionContext(ManagerAgentMcpGateway.McpServer server) {
        SessionContext {
            Objects.requireNonNull(server, "server must not be null");
        }
    }
}
