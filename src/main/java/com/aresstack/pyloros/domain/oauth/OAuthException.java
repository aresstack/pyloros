package com.aresstack.pyloros.domain.oauth;

public final class OAuthException extends RuntimeException {

    private final int statusCode;
    private final String error;
    private final String errorDescription;

    public OAuthException(int statusCode, String error) {
        this(statusCode, error, null);
    }

    public OAuthException(int statusCode, String error, String errorDescription) {
        super(errorDescription == null ? error : error + ": " + errorDescription);
        this.statusCode = statusCode;
        this.error = error;
        this.errorDescription = errorDescription;
    }

    public int statusCode() {
        return statusCode;
    }

    public String error() {
        return error;
    }

    public String errorDescription() {
        return errorDescription;
    }
}
