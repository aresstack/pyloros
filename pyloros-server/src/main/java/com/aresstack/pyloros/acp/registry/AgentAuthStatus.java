package com.aresstack.pyloros.acp.registry;

/**
 * Current auth lifecycle state of an installed ACP agent.
 */
public enum AgentAuthStatus {

    /**
     * The agent requires no authentication.
     */
    NOT_REQUIRED,

    /**
     * Auth is required but has not been set up yet.
     * The agent MUST NOT be registered as an ACP provider in this state.
     */
    REQUIRED,

    /**
     * The required auth has been configured (e.g. setup command was
     * executed). The agent is eligible for ACP provider registration.
     */
    CONFIGURED
}