package com.aresstack.pyloros.acp;

import java.time.Instant;
import java.util.Objects;

public record PendingPermission(
        String id,
        String description,
        Instant requestedAt
) {

    public PendingPermission {
        Objects.requireNonNull(id, "id must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(description, "description must not be null");
        if (description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }
}
