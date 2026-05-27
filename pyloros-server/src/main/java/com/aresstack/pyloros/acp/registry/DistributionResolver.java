package com.aresstack.pyloros.acp.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves a {@link DistributionSpec} into a deterministic {@link InstallPlan}
 * without downloading or executing anything.
 *
 * <p>For binary distributions the resolver selects the matching platform target
 * using the upstream ACP Registry platform key vocabulary
 * (e.g. {@code linux-x86_64}, {@code darwin-aarch64}).
 */
public final class DistributionResolver {

    private final TargetPlatform platform;

    public DistributionResolver(TargetPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform must not be null");
    }

    public DistributionResolver() {
        this(TargetPlatform.current());
    }

    public InstallPlan resolve(DistributionSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return switch (spec.type()) {
            case NPX -> resolveNpx(spec);
            case UVX -> resolveUvx(spec);
            case BINARY -> resolveBinary(spec);
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

    private InstallPlan resolveNpx(DistributionSpec spec) {
        List<String> args = new ArrayList<>();
        args.add("-y");
        args.add(spec.packageRef());
        args.addAll(spec.args());
        return new InstallPlan(
                "npx",
                List.copyOf(args),
                npxInstallPath(spec),
                sourceMetadata(spec)
        );
    }

    private InstallPlan resolveUvx(DistributionSpec spec) {
        List<String> args = new ArrayList<>();
        args.add(spec.packageRef());
        args.addAll(spec.args());
        return new InstallPlan(
                "uvx",
                List.copyOf(args),
                uvxInstallPath(spec),
                sourceMetadata(spec)
        );
    }

    private InstallPlan resolveBinary(DistributionSpec spec) {
        Map<String, DistributionSpec.BinaryTarget> targets = spec.binaryTargets();
        String key = platform.platformKey();
        DistributionSpec.BinaryTarget match = targets.get(key);
        if (match == null) {
            throw new UnsupportedPlatformException(platform, List.copyOf(targets.keySet()));
        }

        List<String> args = new ArrayList<>(match.args());
        return new InstallPlan(
                match.cmd(),
                List.copyOf(args),
                binaryInstallPath(spec),
                binarySourceMetadata(spec, key, match)
        );
    }

    private static String npxInstallPath(DistributionSpec spec) {
        return "npx-agents/" + spec.packageRef();
    }

    private static String uvxInstallPath(DistributionSpec spec) {
        return "uvx-agents/" + spec.packageRef();
    }

    private String binaryInstallPath(DistributionSpec spec) {
        return "bin-agents/" + spec.packageRef() + "/" + platform.platformKey();
    }

    private static Map<String, String> sourceMetadata(DistributionSpec spec) {
        return Map.of(
                "distributionType", spec.type().name().toLowerCase(java.util.Locale.ROOT),
                "packageRef", spec.packageRef()
        );
    }

    private static Map<String, String> binarySourceMetadata(DistributionSpec spec, String platformKey,
                                                             DistributionSpec.BinaryTarget target) {
        return Map.of(
                "distributionType", spec.type().name().toLowerCase(java.util.Locale.ROOT),
                "packageRef", spec.packageRef(),
                "archive", target.archive(),
                "platform", platformKey
        );
    }
}
