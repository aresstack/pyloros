package com.aresstack.pyloros.acp.registry;

/**
 * Indicates how an ACP agent expects to be authenticated.
 * <p>
 * These values can appear in registry metadata or be inferred from agent behavior.
 */
public enum AgentAuthMethod {

    /**
     * No authentication is needed.
     */
    NONE,

    /**
     * The agent manages its own authentication flow entirely — typically
     * a browser-based OAuth flow that Pyloros does not intercept.
     * Pyloros starts the agent normally; the agent handles the rest.
     */
    AGENT_AUTH,

    /**
     * The agent requires a one-time setup / login command to be executed
     * in a terminal before it can operate. Pyloros offers the command as
     * a plan but does not execute it automatically.
     */
    TERMINAL_AUTH
}