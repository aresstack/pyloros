package com.aresstack.pyloros.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PylorosConfigTest {

    @Test
    void localAgentMcpUrlUsesLoopbackInsteadOfPublicOrigin() {
        PylorosConfig config = new PylorosConfig(
                18081,
                "127.0.0.1",
                "https://pyloros.example.org",
                "2025-03-26",
                "central-oauth",
                "intellij",
                "https://auth.example.org",
                "pyloros",
                false,
                "test-view-token"
        );

        assertEquals("http://127.0.0.1:18081/", config.localAgentMcpUrl());
        assertEquals("https://pyloros.example.org", config.publicOrigin());
    }
}
