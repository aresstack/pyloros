package com.aresstack.pyloros.acp.registry;

import java.util.List;
import java.util.Objects;

public final class UnsupportedPlatformException extends RuntimeException {

    private final TargetPlatform requestedPlatform;
    private final List<String> availablePlatforms;

    public UnsupportedPlatformException(TargetPlatform requestedPlatform, List<String> availablePlatforms) {
        super("Unsupported platform: " + Objects.requireNonNull(requestedPlatform).platformKey()
                + ". Available: " + Objects.requireNonNull(availablePlatforms));
        this.requestedPlatform = requestedPlatform;
        this.availablePlatforms = List.copyOf(availablePlatforms);
    }

    public TargetPlatform requestedPlatform() {
        return requestedPlatform;
    }

    public List<String> availablePlatforms() {
        return availablePlatforms;
    }
}
