package com.aresstack.pyloros.provider;

import com.aresstack.pyloros.tool.ToolProvider;
import com.aresstack.pyloros.tool.ToolView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ProviderDescriptor {

    private final String providerId;
    private final ProviderType providerType;
    private final List<ToolView> exposedViews;
    private final boolean preservesUpstreamToolName;

    public ProviderDescriptor(
            String providerId,
            ProviderType providerType,
            List<ToolView> exposedViews,
            boolean preservesUpstreamToolName) {
        this.providerId = validateProviderId(providerId);
        this.providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        this.exposedViews = normalizeViews(exposedViews);
        this.preservesUpstreamToolName = preservesUpstreamToolName;
    }

    public static ProviderDescriptor from(ToolProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        return new ProviderDescriptor(
                provider.providerId(),
                provider.providerType(),
                provider.exposedViews(),
                provider.preservesUpstreamToolName()
        );
    }

    public String providerId() {
        return providerId;
    }

    public ProviderType providerType() {
        return providerType;
    }

    public List<ToolView> exposedViews() {
        return exposedViews;
    }

    public boolean preservesUpstreamToolName() {
        return preservesUpstreamToolName;
    }

    public boolean isExposedIn(ToolView toolView) {
        return exposedViews.contains(Objects.requireNonNull(toolView, "toolView must not be null"));
    }

    private static String validateProviderId(String providerId) {
        Objects.requireNonNull(providerId, "providerId must not be null");
        String trimmedProviderId = providerId.trim();
        if (trimmedProviderId.isEmpty()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        return trimmedProviderId;
    }

    private static List<ToolView> normalizeViews(List<ToolView> exposedViews) {
        if (exposedViews == null || exposedViews.isEmpty()) {
            return List.of(ToolView.PUBLIC);
        }

        List<ToolView> normalizedViews = new ArrayList<>();
        for (ToolView exposedView : exposedViews) {
            ToolView normalizedView = Objects.requireNonNull(exposedView, "exposedView must not be null");
            if (!normalizedViews.contains(normalizedView)) {
                normalizedViews.add(normalizedView);
            }
        }
        return Collections.unmodifiableList(normalizedViews);
    }
}
    