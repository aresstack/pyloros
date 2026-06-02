package com.aresstack.pyloros.acp.registry;

import java.util.Objects;

/**
 * Structured result of an agent lifecycle operation (install, update, uninstall).
 * Uses a sealed interface so callers must handle all outcome types.
 */
public sealed interface AgentLifecycleResult {

    /**
     * The operation completed successfully.
     *
     * @param agent   the installed/updated/removed agent state
     * @param action  the lifecycle action that was performed
     * @param message human-readable summary of the outcome
     */
    record Success(InstalledAgent agent, Action action, String message) implements AgentLifecycleResult {
        public Success {
            Objects.requireNonNull(agent, "agent must not be null");
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }
    }

    /**
     * The operation was rejected without performing any mutation.
     *
     * @param agentId the agent ID that was targeted
     * @param reason  human-readable reason for rejection
     * @param kind    the rejection kind for programmatic handling
     */
    record Rejected(String agentId, String reason, RejectionKind kind) implements AgentLifecycleResult {
        public Rejected {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    /**
     * The operation failed after partial execution; rollback was attempted.
     *
     * @param agentId        the agent ID that was targeted
     * @param reason         human-readable failure reason
     * @param rolledBack     true if rollback was successful (previous state restored)
     * @param rollbackDetail optional detail about the rollback outcome
     */
    record Failed(String agentId, String reason, boolean rolledBack, String rollbackDetail)
            implements AgentLifecycleResult {
        public Failed {
            Objects.requireNonNull(agentId, "agentId must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            rollbackDetail = rollbackDetail == null ? "" : rollbackDetail;
        }
    }

    enum Action {
        INSTALLED,
        UPDATED,
        UNINSTALLED
    }

    enum RejectionKind {
        ALREADY_INSTALLED,
        NOT_INSTALLED,
        NOT_FOUND_IN_REGISTRY,
        ALREADY_UP_TO_DATE,
        UNSUPPORTED_PLATFORM,
        UNSUPPORTED_DISTRIBUTION
    }
}
