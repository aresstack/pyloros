package com.aresstack.pyloros.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record ToolCatalogEntry(
        String externalName,
        ToolAddress address,
        Map<String, Object> descriptor
) {

    public ToolCatalogEntry {
        Objects.requireNonNull(externalName, "externalName must not be null");
        if (externalName.isBlank()) {
            throw new IllegalArgumentException("externalName must not be blank");
        }
        Objects.requireNonNull(address, "address must not be null");
        descriptor = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(descriptor, "descriptor must not be null")));
    }
}
