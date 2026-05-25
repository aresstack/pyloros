package com.aresstack.pyloros.plugin;

import java.util.Objects;

/**
 * Structured, operator-friendly description of a plugin failure.
 *
 * <p>The {@link #message()} value is always truncated to at most
 * {@link #MAX_MESSAGE_LENGTH} characters so that very long stack traces or
 * messages from misbehaving plugins do not flood diagnostics.</p>
 *
 * @param errorClass fully-qualified name of the throwable class, never {@code null}
 * @param message    truncated (and never {@code null}) failure message
 */
public record PluginErrorInfo(String errorClass, String message) {

    /** Maximum length of the diagnostic message before truncation. */
    public static final int MAX_MESSAGE_LENGTH = 500;

    private static final String TRUNCATION_SUFFIX = "...";

    public PluginErrorInfo {
        Objects.requireNonNull(errorClass, "errorClass must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "message must not exceed " + MAX_MESSAGE_LENGTH + " characters; use PluginErrorInfo.from(Throwable)"
            );
        }
    }

    /**
     * Builds a {@link PluginErrorInfo} from a throwable, truncating long messages.
     */
    public static PluginErrorInfo from(Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable must not be null");
        String errorClass = throwable.getClass().getName();
        String rawMessage = throwable.getMessage();
        if (rawMessage == null || rawMessage.isEmpty()) {
            rawMessage = throwable.getClass().getSimpleName();
        }
        return new PluginErrorInfo(errorClass, truncate(rawMessage));
    }

    private static String truncate(String message) {
        if (message.length() <= MAX_MESSAGE_LENGTH) {
            return message;
        }
        int keep = MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length();
        return message.substring(0, keep) + TRUNCATION_SUFFIX;
    }
}
