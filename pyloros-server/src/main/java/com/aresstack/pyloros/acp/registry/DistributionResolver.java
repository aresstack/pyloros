package com.aresstack.pyloros.acp.registry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        if (spec.extraPackages().isEmpty()) {
            args.add(spec.packageRef());
        } else {
            args.add("-p");
            args.add(spec.packageRef());
            for (String extraPackage : spec.extraPackages()) {
                args.add("-p");
                args.add(extraPackage);
            }
            args.add(resolveExecutable(spec));
        }
        args.addAll(spec.args());
        return new InstallPlan(
                launchCommand("npx"),
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
                launchCommand("uvx"),
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

    private String launchCommand(String command) {
        if (!"windows".equals(platform.os())) {
            return command;
        }

        String commandScript = command + ".cmd";
        return resolveFromPath(commandScript).orElse(commandScript);
    }

    private static java.util.Optional<String> resolveFromPath(String executableName) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            return java.util.Optional.empty();
        }
        for (String entry : pathValue.split(java.io.File.pathSeparator)) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(entry).resolve(executableName);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return java.util.Optional.of(candidate.toAbsolutePath().toString());
            }
        }
        return java.util.Optional.empty();
    }

    private static String resolveExecutable(DistributionSpec spec) {
        if (spec.executable() != null) {
            return spec.executable();
        }
        String packageRef = spec.packageRef();
        if (packageRef.startsWith("@")) {
            int versionSeparator = packageRef.indexOf('@', 1);
            return versionSeparator > 0 ? packageRef.substring(0, versionSeparator) : packageRef;
        }
        int versionSeparator = packageRef.indexOf('@');
        return versionSeparator > 0 ? packageRef.substring(0, versionSeparator) : packageRef;
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
