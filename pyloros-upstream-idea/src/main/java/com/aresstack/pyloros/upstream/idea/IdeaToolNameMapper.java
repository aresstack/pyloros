package com.aresstack.pyloros.upstream.idea;

import java.util.Set;

/**
 * Maps IDEA tool names between public names used by Pyloros and original upstream names.
 */
public final class IdeaToolNameMapper {

    public static final String PUBLIC_PREFIX = "intellij/";

    private final String legacyPrefix;

    public IdeaToolNameMapper(String legacyPrefix) {
        this.legacyPrefix = legacyPrefix == null ? "idea__" : legacyPrefix;
    }

    public String publicName(String originalName) {
        return PUBLIC_PREFIX + originalName;
    }

    public boolean isPublicName(String name) {
        return name != null && name.startsWith(PUBLIC_PREFIX);
    }

    public boolean isLegacyName(String name) {
        return name != null && name.startsWith(legacyPrefix);
    }

    public boolean isNamespacedAlias(String name) {
        return isPublicName(name) || isLegacyName(name);
    }

    public String toOriginalName(String publicOrLegacyOrOriginal) {
        if (publicOrLegacyOrOriginal == null) {
            return "";
        }
        if (isPublicName(publicOrLegacyOrOriginal)) {
            return publicOrLegacyOrOriginal.substring(PUBLIC_PREFIX.length());
        }
        if (isLegacyName(publicOrLegacyOrOriginal)) {
            return publicOrLegacyOrOriginal.substring(legacyPrefix.length());
        }
        return publicOrLegacyOrOriginal;
    }

    /**
     * Unprefixed names are accepted only when they are known upstream IDEA tool names.
     */
    public boolean supports(String toolName, Set<String> knownOriginalTools, boolean allowOptimisticUnprefixed) {
        if (toolName == null || toolName.isBlank()) {
            return false;
        }

        if (isNamespacedAlias(toolName)) {
            return true;
        }

        if (knownOriginalTools != null && knownOriginalTools.contains(toolName)) {
            return true;
        }

        return allowOptimisticUnprefixed;
    }
}
