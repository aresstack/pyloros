package com.aresstack.pyloros.langchain;

/**
 * Reason for which a LangChain run was stopped.
 *
 * <p>Mirrors the {@code stoppedReason} field documented for {@code pyloros-ai/ask}
 * (see {@code docs/requirements/pyloros-langchain-extension.md} section 23.4.1).
 */
public enum LangChainStopReason {
    COMPLETED,
    MAX_TOOL_CALLS,
    POLICY_DENIED,
    TIMEOUT,
    MODEL_ERROR
}
