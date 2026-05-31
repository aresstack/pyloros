package com.aresstack.pyloros.acp.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Declared auth requirements for an ACP registry agent, as read from
 * registry metadata.
 *
 * @param method       how the agent expects to be authenticated
 * @param setupCommand suggested terminal command for setup/login
 *                     (meaningful only for {@link AgentAuthMethod#TERMINAL_AUTH})
 * @param description  human-readable description of the required auth step
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthRequirements(
        AgentAuthMethod method,
        String setupCommand,
        String description
) {
    public AuthRequirements {
        if (method == null) {
            method = AgentAuthMethod.NONE;
        }
        if (setupCommand == null) {
            setupCommand = "";
        }
        if (description == null) {
            description = "";
        }
    }
}