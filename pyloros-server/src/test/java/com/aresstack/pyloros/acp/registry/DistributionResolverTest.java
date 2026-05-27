package com.aresstack.pyloros.acp.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistributionResolverTest {

    private static final TargetPlatform LINUX_X64 = new TargetPlatform("linux", "x64");

    @Test
    void npxPlanProducesDeterministicCommandAndArgs() {
        var distribution = new RegistryDistribution(
                DistributionType.NPX, "@anthropic/claude-agent", "1.2.3", List.of());
        var resolver = new DistributionResolver(LINUX_X64);

        InstallPlan plan = resolver.resolve(distribution);

        assertEquals("npx", plan.command());
        assertEquals(List.of("-y", "@anthropic/claude-agent@1.2.3"), plan.args());
        assertEquals("npx-agents/@anthropic/claude-agent/1.2.3", plan.installPath());
        assertEquals("npx", plan.sourceMetadata().get("distributionType"));
        assertEquals("@anthropic/claude-agent", plan.sourceMetadata().get("packageName"));
        assertEquals("1.2.3", plan.sourceMetadata().get("version"));
    }

    @Test
    void uvxPlanProducesDeterministicCommandAndArgs() {
        var distribution = new RegistryDistribution(
                DistributionType.UVX, "aider-chat", "0.42.0", List.of());
        var resolver = new DistributionResolver(LINUX_X64);

        InstallPlan plan = resolver.resolve(distribution);

        assertEquals("uvx", plan.command());
        assertEquals(List.of("aider-chat==0.42.0"), plan.args());
        assertEquals("uvx-agents/aider-chat/0.42.0", plan.installPath());
        assertEquals("uvx", plan.sourceMetadata().get("distributionType"));
        assertEquals("aider-chat", plan.sourceMetadata().get("packageName"));
        assertEquals("0.42.0", plan.sourceMetadata().get("version"));
    }

    @Test
    void binaryPlanSelectsMatchingPlatform() {
        var targets = List.of(
                new RegistryDistribution.BinaryTarget("linux", "x64", "https://example.com/agent-linux-x64"),
                new RegistryDistribution.BinaryTarget("darwin", "arm64", "https://example.com/agent-darwin-arm64")
        );
        var distribution = new RegistryDistribution(
                DistributionType.BINARY, "my-agent", "2.0.0", targets);
        var resolver = new DistributionResolver(LINUX_X64);

        InstallPlan plan = resolver.resolve(distribution);

        assertEquals("https://example.com/agent-linux-x64", plan.command());
        assertEquals(List.of(), plan.args());
        assertEquals("bin-agents/my-agent/2.0.0/linux-x64", plan.installPath());
        assertEquals("binary", plan.sourceMetadata().get("distributionType"));
        assertEquals("https://example.com/agent-linux-x64", plan.sourceMetadata().get("downloadUrl"));
        assertEquals("linux-x64", plan.sourceMetadata().get("platform"));
    }

    @Test
    void binaryPlanThrowsForUnsupportedPlatform() {
        var targets = List.of(
                new RegistryDistribution.BinaryTarget("darwin", "arm64", "https://example.com/agent-darwin-arm64")
        );
        var distribution = new RegistryDistribution(
                DistributionType.BINARY, "my-agent", "2.0.0", targets);
        var resolver = new DistributionResolver(LINUX_X64);

        UnsupportedPlatformException ex = assertThrows(
                UnsupportedPlatformException.class, () -> resolver.resolve(distribution));

        assertEquals(LINUX_X64, ex.requestedPlatform());
        assertEquals(List.of("darwin-arm64"), ex.availablePlatforms());
        assertTrue(ex.getMessage().contains("linux-x64"));
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
    void targetPlatformNormalizesKnownValues() {
        assertEquals("linux", TargetPlatform.normalizeOs("Linux"));
        assertEquals("darwin", TargetPlatform.normalizeOs("Mac OS X"));
        assertEquals("windows", TargetPlatform.normalizeOs("Windows 10"));
        assertEquals("x64", TargetPlatform.normalizeArch("amd64"));
        assertEquals("x64", TargetPlatform.normalizeArch("x86_64"));
        assertEquals("arm64", TargetPlatform.normalizeArch("aarch64"));
    }
}
