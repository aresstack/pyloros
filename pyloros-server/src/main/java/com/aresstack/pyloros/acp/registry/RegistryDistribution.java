package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Objects;

public record RegistryDistribution(
        DistributionType type,
        String packageName,
        String version,
        List<BinaryTarget> binaryTargets
) {

    public RegistryDistribution {
        Objects.requireNonNull(type, "type must not be null");
        packageName = requireText(packageName, "packageName");
        version = requireText(version, "version");
        binaryTargets = binaryTargets == null ? List.of() : List.copyOf(binaryTargets);
    }

    public record BinaryTarget(String os, String arch, String downloadUrl) {

        public BinaryTarget {
            os = requireText(os, "os");
            arch = requireText(arch, "arch");
            downloadUrl = requireText(downloadUrl, "downloadUrl");
        }

        public boolean matches(TargetPlatform platform) {
            return os.equalsIgnoreCase(platform.os()) && arch.equalsIgnoreCase(platform.arch());
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
