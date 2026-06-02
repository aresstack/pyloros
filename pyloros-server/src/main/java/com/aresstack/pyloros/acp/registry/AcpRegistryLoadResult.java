package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Objects;

/**
 * Result of loading the ACP registry. Either success with a registry or failure with error details.
 */
public sealed interface AcpRegistryLoadResult {

    record Success(AcpRegistry registry, Source source) implements AcpRegistryLoadResult {
        public Success {
            Objects.requireNonNull(registry, "registry must not be null");
            Objects.requireNonNull(source, "source must not be null");
        }
    }

    record Failure(List<String> errors) implements AcpRegistryLoadResult {
        public Failure {
            Objects.requireNonNull(errors, "errors must not be null");
            if (errors.isEmpty()) {
                throw new IllegalArgumentException("errors must not be empty");
            }
        }

        public Failure(String error) {
            this(List.of(error));
        }
    }

    enum Source {
        REMOTE,
        CACHE
    }
}
