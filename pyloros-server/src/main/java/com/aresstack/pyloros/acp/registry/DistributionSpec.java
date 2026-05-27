package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Internal representation of a single resolved distribution specification,
 * extracted from one of the upstream registry distribution entries (npx, uvx, binary).
 *
 * <p>This type is separate from the R7-01 {@code RegistryDistribution} which
 * mirrors the full upstream JSON shape. This record captures only what the
 * resolver needs to produce an {@link InstallPlan}.
 */
public record DistributionSpec(
        DistributionType type,
        String packageRef,
        List<String> args,
        Map<String, BinaryTarget> binaryTargets
) {

    public DistributionSpec {
        Objects.requireNonNull(type, "type must not be null");
        packageRef = requireText(packageRef, "packageRef");
        args = args == null ? List.of() : List.copyOf(args);
        binaryTargets = binaryTargets == null ? Map.of() : Map.copyOf(binaryTargets);
    }

    /**
     * A single binary target keyed by platform (e.g. "linux-x86_64", "darwin-aarch64").
     */
    public record BinaryTarget(String archive, String cmd, List<String> args) {

        public BinaryTarget {
            archive = requireText(archive, "archive");
            cmd = requireText(cmd, "cmd");
            args = args == null ? List.of() : List.copyOf(args);
        }

        private static String requireText(String value, String fieldName) {
            Objects.requireNonNull(value, fieldName + " must not be null");
            if (value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
