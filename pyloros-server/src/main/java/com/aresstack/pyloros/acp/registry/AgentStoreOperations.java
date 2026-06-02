package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Optional;

/**
 * Defines the operations for managing installed ACP registry agents.
 * Allows test doubles to be injected without weakening production class design.
 */
public interface AgentStoreOperations {

    /**
     * Lists all installed agents.
     */
    List<InstalledAgent> listAll();

    /**
     * Lists only enabled installed agents.
     */
    List<InstalledAgent> listEnabled();

    /**
     * Finds an installed agent by its ID.
     */
    Optional<InstalledAgent> findById(String agentId);

    /**
     * Installs or updates an agent in the store.
     */
    InstalledAgent save(InstalledAgent agent);

    /**
     * Removes an installed agent by its ID.
     * Returns the removed agent, or empty if not found.
     */
    Optional<InstalledAgent> remove(String agentId);

    /**
     * Enables or disables an agent by ID.
     * Returns the updated agent, or empty if not found.
     */
    Optional<InstalledAgent> setEnabled(String agentId, boolean enabled);
}
