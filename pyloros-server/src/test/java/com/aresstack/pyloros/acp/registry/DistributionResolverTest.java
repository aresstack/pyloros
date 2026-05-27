package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionResolverTest {

    private static final TargetPlatform LINUX_X86_64 = new TargetPlatform("linux", "x86_64");

    @Test
    void npxPlanProducesDeterministicCommandAndArgs() {
        var spec = new DistributionSpec(
                DistributionType.NPX,
                "@agentclientprotocol/claude-agent-acp@0.37.0",
                List.of(),
                Map.of());
        var resolver = new DistributionResolver(LINUX_X86_64);

        InstallPlan plan = resolver.resolve(spec);

        assertEquals("npx", plan.command());
        assertEquals(List.of("-y", "@agentclientprotocol/claude-agent-acp@0.37.0"), plan.args());
        assertEquals("npx-agents/@agentclientprotocol/claude-agent-acp@0.37.0", plan.installPath());
        assertEquals("npx", plan.sourceMetadata().get("distributionType"));
        assertEquals("@agentclientprotocol/claude-agent-acp@0.37.0", plan.sourceMetadata().get("packageRef"));
    }

    @Test
    void npxPlanIncludesExtraArgs() {
        var spec = new DistributionSpec(
                DistributionType.NPX,
                "deepagents-acp@0.1.7",
                List.of(),
                Map.of());
        var resolver = new DistributionResolver(LINUX_X86_64);

        InstallPlan plan = resolver.resolve(spec);

        assertEquals("npx", plan.command());
        assertEquals(List.of("-y", "deepagents-acp@0.1.7"), plan.args());
    }

    @Test
    void uvxPlanProducesDeterministicCommandAndArgs() {
        var spec = new DistributionSpec(
                DistributionType.UVX,
                "fast-agent-acp==0.7.12",
                List.of("-x"),
                Map.of());
        var resolver = new DistributionResolver(LINUX_X86_64);

        InstallPlan plan = resolver.resolve(spec);

        assertEquals("uvx", plan.command());
        assertEquals(List.of("fast-agent-acp==0.7.12", "-x"), plan.args());
        assertEquals("uvx-agents/fast-agent-acp==0.7.12", plan.installPath());
        assertEquals("uvx", plan.sourceMetadata().get("distributionType"));
        assertEquals("fast-agent-acp==0.7.12", plan.sourceMetadata().get("packageRef"));
    }

    @Test
    void binaryPlanSelectsMatchingPlatformByRegistryKey() {
        var targets = Map.of(
                "linux-x86_64", new DistributionSpec.BinaryTarget(
                        "https://github.com/block/goose/releases/download/v1.35.0/goose-x86_64-unknown-linux-gnu.tar.bz2",
                        "./goose", List.of("acp")),
                "darwin-aarch64", new DistributionSpec.BinaryTarget(
                        "https://github.com/block/goose/releases/download/v1.35.0/goose-aarch64-apple-darwin.tar.bz2",
                        "./goose", List.of("acp"))
        );
        var spec = new DistributionSpec(
                DistributionType.BINARY, "goose", List.of(), targets);
        var resolver = new DistributionResolver(LINUX_X86_64);

        InstallPlan plan = resolver.resolve(spec);

        assertEquals("./goose", plan.command());
        assertEquals(List.of("acp"), plan.args());
        assertEquals("bin-agents/goose/linux-x86_64", plan.installPath());
        assertEquals("binary", plan.sourceMetadata().get("distributionType"));
        assertEquals("https://github.com/block/goose/releases/download/v1.35.0/goose-x86_64-unknown-linux-gnu.tar.bz2",
                plan.sourceMetadata().get("archive"));
        assertEquals("linux-x86_64", plan.sourceMetadata().get("platform"));
    }

    @Test
    void binaryPlanThrowsForUnsupportedPlatform() {
        var targets = Map.of(
                "darwin-aarch64", new DistributionSpec.BinaryTarget(
                        "https://example.com/agent-darwin-aarch64.tar.gz",
                        "./agent", List.of())
        );
        var spec = new DistributionSpec(
                DistributionType.BINARY, "my-agent", List.of(), targets);
        var resolver = new DistributionResolver(LINUX_X86_64);

        UnsupportedPlatformException ex = assertThrows(
                UnsupportedPlatformException.class, () -> resolver.resolve(spec));

        assertEquals(LINUX_X86_64, ex.requestedPlatform());
        assertEquals(List.of("darwin-aarch64"), ex.availablePlatforms());
        assertTrue(ex.getMessage().contains("linux-x86_64"));
    }

    @Test
    void parseTypeThrowsForUnknownDistribution() {
        UnsupportedDistributionException ex = assertThrows(
                UnsupportedDistributionException.class,
                () -> DistributionResolver.parseType("docker"));

        assertEquals("docker", ex.distributionType());
        assertTrue(ex.getMessage().contains("docker"));
    }

    @Test
    void parseTypeRecognizesAllValidTypes() {
        assertEquals(DistributionType.BINARY, DistributionResolver.parseType("binary"));
        assertEquals(DistributionType.NPX, DistributionResolver.parseType("npx"));
        assertEquals(DistributionType.UVX, DistributionResolver.parseType("uvx"));
        assertEquals(DistributionType.BINARY, DistributionResolver.parseType("BINARY"));
        assertEquals(DistributionType.NPX, DistributionResolver.parseType("Npx"));
    }

    @Test
    void targetPlatformCurrentReturnsNonNull() {
        TargetPlatform current = TargetPlatform.current();
        assertNotNull(current.os());
        assertNotNull(current.arch());
    }

    @Test
    void targetPlatformNormalizesToUpstreamRegistryKeys() {
        assertEquals("linux", TargetPlatform.normalizeOs("Linux"));
        assertEquals("darwin", TargetPlatform.normalizeOs("Mac OS X"));
        assertEquals("windows", TargetPlatform.normalizeOs("Windows 10"));
        assertEquals("x86_64", TargetPlatform.normalizeArch("amd64"));
        assertEquals("x86_64", TargetPlatform.normalizeArch("x86_64"));
        assertEquals("aarch64", TargetPlatform.normalizeArch("aarch64"));
        assertEquals("aarch64", TargetPlatform.normalizeArch("arm64"));
    }

    @Test
    void platformKeyMatchesRegistryFormat() {
        assertEquals("linux-x86_64", LINUX_X86_64.platformKey());
        assertEquals("darwin-aarch64", new TargetPlatform("darwin", "aarch64").platformKey());
        assertEquals("windows-x86_64", new TargetPlatform("windows", "x86_64").platformKey());
    }
}
