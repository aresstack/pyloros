package com.aresstack.pyloros.plugin;

import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of registering a {@link PylorosPlugin}'s {@link PluginContribution}
 * with a host.
 *
 * <p>This type is part of the stable plugin API so that plugin hosts can
 * communicate per-plugin outcomes (e.g. duplicate plugin id, validation
 * failure, success) without exposing infrastructure or transport types.
 *
 * <p>{@link #accepted()} indicates whether the contribution was applied.
 * If {@code accepted == false}, {@link #rejectionReason()} contains a short,
 * human readable explanation (for example
 * {@code "duplicate plugin id 'com.example.foo'"}).
 *
 * @param descriptor      the plugin descriptor that was processed; never
 *                        {@code null}
 * @param contribution    the contribution that was offered; never
 *                        {@code null}
 * @param accepted        whether the contribution was accepted by the host
 * @param rejectionReason short, human readable reason when {@code accepted ==
 *                        false}; otherwise an empty string. Never
 *                        {@code null}.
 */
public record PluginContributionResult(
        PluginDescriptor descriptor,
        PluginContribution contribution,
        boolean accepted,
        String rejectionReason) {

    /**
     * Compact constructor that validates the result and enforces consistency
     * between {@link #accepted()} and {@link #rejectionReason()}.
     *
     * @throws NullPointerException     if {@code descriptor},
     *                                  {@code contribution} or
     *                                  {@code rejectionReason} is {@code null}
     * @throws IllegalArgumentException if {@code accepted} is {@code true} but
     *                                  {@code rejectionReason} is not blank,
     *                                  or if {@code accepted} is {@code false}
     *                                  but {@code rejectionReason} is blank
     */
    public PluginContributionResult {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(contribution, "contribution must not be null");
        Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
        if (accepted && !rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must be blank when accepted is true");
        }
        if (!accepted && rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must be supplied when accepted is false");
        }
    }

    /**
     * Build a successful result.
     *
     * @param descriptor   plugin descriptor that was accepted
     * @param contribution contribution that was accepted
     * @return an {@code accepted} result with an empty rejection reason
     */
    public static PluginContributionResult accepted(PluginDescriptor descriptor, PluginContribution contribution) {
        return new PluginContributionResult(descriptor, contribution, true, "");
    }

    /**
     * Build a rejected result.
     *
     * @param descriptor      plugin descriptor that was rejected
     * @param contribution    contribution that was rejected
     * @param rejectionReason short, non-blank rejection reason
     * @return a rejected result
     */
    public static PluginContributionResult rejected(
            PluginDescriptor descriptor,
            PluginContribution contribution,
            String rejectionReason) {
        Objects.requireNonNull(rejectionReason, "rejectionReason must not be null");
        if (rejectionReason.isBlank()) {
            throw new IllegalArgumentException("rejectionReason must not be blank");
        }
        return new PluginContributionResult(descriptor, contribution, false, rejectionReason);
    }

    /**
     * @return the {@link #rejectionReason()} wrapped in an {@link Optional},
     *         empty if the contribution was accepted
     */
    public Optional<String> optionalRejectionReason() {
        return accepted ? Optional.empty() : Optional.of(rejectionReason);
    }
}
