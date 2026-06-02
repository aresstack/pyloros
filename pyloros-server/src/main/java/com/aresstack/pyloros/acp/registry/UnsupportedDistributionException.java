package com.aresstack.pyloros.acp.registry;

import java.util.Objects;

public final class UnsupportedDistributionException extends RuntimeException {

    private final String distributionType;

    public UnsupportedDistributionException(String distributionType) {
        super("Unsupported distribution type: " + Objects.requireNonNull(distributionType, "distributionType must not be null"));
        this.distributionType = distributionType;
    }

    public String distributionType() {
        return distributionType;
    }
}
