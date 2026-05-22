package com.aresstack.pyloros.domain.oauth;

import java.time.Instant;

public record AuthorizationCode(
        String clientId,
        String redirectUri,
        String scope,
        String codeChallenge,
        String codeChallengeMethod,
        Instant expiresAt
) {
}
