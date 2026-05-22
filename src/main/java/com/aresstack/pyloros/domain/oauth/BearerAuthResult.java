package com.aresstack.pyloros.domain.oauth;

/**
 * Result of a Bearer token authorization check (RFC 6750).
 */
public enum BearerAuthResult {

    /** Token is present and valid. */
    OK,

    /** No Authorization header or not a Bearer token. */
    MISSING_TOKEN,

    /** Token is present but not known (never issued or already cleaned up). */
    INVALID_TOKEN,

    /** Token was issued but has expired. */
    EXPIRED_TOKEN
}

