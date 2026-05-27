package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DistributionResolver {

    private final TargetPlatform platform;

    public DistributionResolver(TargetPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    public DistributionResolver() {
        this(TargetPlatform.current());
    }

    public InstallPlan resolve(RegistryDistribution distribution) {
        Objects.requireNonNull(distribution, "distribution must not be null");
        return switch (distribution.type()) {
            case NPX -> resolveNpx(distribution);
            case UVX -> resolveUvx(distribution);
            case BINARY -> resolveBinary(distribution);
        };
    }

    public static DistributionType parseType(String type) {
        Objects.requireNonNull(type, "type must not be null");
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "binary" -> DistributionType.BINARY;
            case "npx" -> DistributionType.NPX;
            case "uvx" -> DistributionType.UVX;
            default -> throw new UnsupportedDistributionException(type);
        };
    }

    private InstallPlan resolveNpx(RegistryDistribution distribution) {
        String packageSpec = distribution.packageName() + "@" + distribution.version();
        return new InstallPlan(
                "npx",
                List.of("-y", packageSpec),
                npxInstallPath(distribution),
                sourceMetadata(distribution)
        );
    }

    private InstallPlan resolveUvx(RegistryDistribution distribution) {
        String packageSpec = distribution.packageName() + "==" + distribution.version();
        return new InstallPlan(
                "uvx",
                List.of(packageSpec),
                uvxInstallPath(distribution),
                sourceMetadata(distribution)
        );
    }

    private InstallPlan resolveBinary(RegistryDistribution distribution) {
        List<RegistryDistribution.BinaryTarget> targets = distribution.binaryTargets();
        RegistryDistribution.BinaryTarget match = targets.stream()
                .filter(t -> t.matches(platform))
                .findFirst()
                .orElseThrow(() -> {
                    List<String> available = targets.stream()
                            .map(t -> t.os() + "-" + t.arch())
                            .toList();
                    return new UnsupportedPlatformException(platform, available);
                });

        return new InstallPlan(
                match.downloadUrl(),
                List.of(),
                binaryInstallPath(distribution),
                sourceMetadata(distribution, match)
        );
    }

    private static String npxInstallPath(RegistryDistribution distribution) {
        return "npx-agents/" + distribution.packageName() + "/" + distribution.version();
    }

    private static String uvxInstallPath(RegistryDistribution distribution) {
        return "uvx-agents/" + distribution.packageName() + "/" + distribution.version();
    }

    private String binaryInstallPath(RegistryDistribution distribution) {
        return "bin-agents/" + distribution.packageName() + "/" + distribution.version() + "/" + platform.label();
    }

    private static Map<String, String> sourceMetadata(RegistryDistribution distribution) {
        return Map.of(
                "distributionType", distribution.type().name().toLowerCase(java.util.Locale.ROOT),
                "packageName", distribution.packageName(),
                "version", distribution.version()
        );
    }

    private static Map<String, String> sourceMetadata(RegistryDistribution distribution,
                                                       RegistryDistribution.BinaryTarget target) {
        return Map.of(
                "distributionType", distribution.type().name().toLowerCase(java.util.Locale.ROOT),
                "packageName", distribution.packageName(),
                "version", distribution.version(),
                "downloadUrl", target.downloadUrl(),
                "platform", target.os() + "-" + target.arch()
        );
    }
}
