package com.aresstack.pyloros.domain.oauth;

import java.time.Instant;

public record AccessToken(String scope, Instant expiresAt) {
}
