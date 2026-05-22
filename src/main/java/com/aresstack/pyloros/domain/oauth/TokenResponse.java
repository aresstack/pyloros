package com.aresstack.pyloros.domain.oauth;

public record TokenResponse(
        String accessToken,
        String tokenType,
        int expiresIn,
        String scope
) {
}
