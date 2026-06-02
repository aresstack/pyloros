package com.aresstack.pyloros.acp.registry;

/**
 * Abstraction for materializing and removing agent installations.
 *
 * <p>For binary distributions, the implementation downloads and extracts the
 * archive to a controlled install directory. For npx/uvx distributions, the
 * install is a no-op since those are resolved command plans.
 */
public interface AgentInstaller {

    /**
     * Materializes an agent installation for the given plan.
     * For binary distributions, this downloads and extracts the archive.
     * For npx/uvx, this is a no-op.
     *
     * @param plan the resolved install plan
     * @throws AgentInstallException if materialization fails
     */
    void materialize(InstallPlan plan);

    /**
     * Removes materialized agent files from the install directory.
     * For npx/uvx, this is a no-op.
     *
     * @param plan the install plan describing what was installed
     * @throws AgentInstallException if removal fails
     */
    void remove(InstallPlan plan);
}
