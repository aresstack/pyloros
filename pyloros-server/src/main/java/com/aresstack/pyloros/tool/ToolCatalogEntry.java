package com.aresstack.pyloros.tool;

import java.util.Map;

public record ToolCatalogEntry(
        String exposedName,
        String providerId,
        String nativeToolName,
        Map<String, Object> descriptor
) {

    public ToolAddress address() {
        return new ToolAddress(providerId, nativeToolName);
    }
}

