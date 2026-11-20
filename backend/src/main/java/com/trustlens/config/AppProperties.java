package com.trustlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "trustlens")
public record AppProperties(
        String anthropicApiKey,
        String anthropicModel,
        String anthropicBaseUrl,
        String workerUrl,
        String screenshotDir,
        int maxActionsPerJourney,
        int journeyTimeoutSeconds,
        int parallelJourneys,
        String allowedOrigins,
        boolean allowPrivateTargets) {

    public boolean claudeConfigured() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }
}
