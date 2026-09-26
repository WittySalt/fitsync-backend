package com.fitsync.recommendation.coach.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration the coach layer owns.
 *
 * <p>Separate from {@code RecommendationProperties} on purpose: the package boundary that the
 * dependency test enforces should be visible in configuration too, so that what belongs to the
 * coach is obvious from the property names alone.
 *
 * @param maxToolCalls    how many tool calls one turn may make before the orchestrator stops and
 *                        answers with what it has. Four is enough for the intents that exist and
 *                        low enough that a confused model cannot spend a minute of the user's time.
 * @param catalogueTimeout per-request timeout on a catalogue read. Short, because a slow catalogue
 *                        should degrade into an observation the model works around rather than
 *                        hold the turn open.
 * @param workoutBaseUrl  root of the service that owns the exercise catalogue
 * @param nutritionBaseUrl root of the service that owns the food catalogue
 */
@ConfigurationProperties(prefix = "fitsync.coach")
public record CoachProperties(
        Integer maxToolCalls,
        Duration catalogueTimeout,
        String workoutBaseUrl,
        String nutritionBaseUrl) {

    private static final int DEFAULT_MAX_TOOL_CALLS = 4;
    private static final Duration DEFAULT_CATALOGUE_TIMEOUT = Duration.ofSeconds(5);

    public CoachProperties {
        maxToolCalls = maxToolCalls == null || maxToolCalls.intValue() < 1
                ? Integer.valueOf(DEFAULT_MAX_TOOL_CALLS)
                : maxToolCalls;
        catalogueTimeout = catalogueTimeout == null ? DEFAULT_CATALOGUE_TIMEOUT : catalogueTimeout;
        workoutBaseUrl = workoutBaseUrl == null ? "http://localhost:8082" : stripTrailingSlash(workoutBaseUrl);
        nutritionBaseUrl = nutritionBaseUrl == null ? "http://localhost:8083" : stripTrailingSlash(nutritionBaseUrl);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
