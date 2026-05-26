package com.aresstack.pyloros.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the trust gate of {@link McpRoutes#isTrustedInjectedViewRequest}.
 *
 * <p>The R6 MCP view boundary requires that non-public tool views can only be selected
 * by the Pyloros-injected manager-agent path. Earlier revisions relied solely on a
 * client-controlled {@code X-Pyloros-Injected-View} header plus loopback, which is
 * trivially spoofable by any locally authenticated MCP caller. This test pins the
 * stricter contract: an unguessable shared-secret token must also match, in constant
 * time, before the request is considered trusted.
 */
final class McpRoutesTrustTest {

    private static final String TOKEN = "secret-token-value";

    @Test
    void rejectsWhenConfiguredTokenIsBlank() {
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "127.0.0.1", ""));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "127.0.0.1", null));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "127.0.0.1", "   "));
    }

    @Test
    void rejectsWhenInjectedViewHeaderMissingOrWrong() {
        assertFalse(McpRoutes.isTrustedInjectedViewRequest(null, TOKEN, "127.0.0.1", TOKEN));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("", TOKEN, "127.0.0.1", TOKEN));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("native", TOKEN, "127.0.0.1", TOKEN));
    }

    @Test
    void rejectsWhenTokenHeaderMissing() {
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", null, "127.0.0.1", TOKEN));
    }

    @Test
    void rejectsWhenTokenMismatch() {
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", "wrong-token", "127.0.0.1", TOKEN));
    }

    @Test
    void rejectsWhenRemoteHostNotLoopback() {
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "10.0.0.1", TOKEN));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "192.168.1.5", TOKEN));
        assertFalse(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, null, TOKEN));
    }

    @Test
    void acceptsWhenAllChecksPass() {
        assertTrue(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "127.0.0.1", TOKEN));
        assertTrue(McpRoutes.isTrustedInjectedViewRequest("ACP", TOKEN, "127.0.0.1", TOKEN));
        assertTrue(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "::1", TOKEN));
        assertTrue(McpRoutes.isTrustedInjectedViewRequest("acp", TOKEN, "0:0:0:0:0:0:0:1", TOKEN));
    }
}
