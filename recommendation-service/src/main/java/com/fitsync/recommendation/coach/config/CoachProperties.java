package com.fitsync.recommendation.coach.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
 * @param userBaseUrl     root of the service that owns the readiness questionnaire
 * @param streamTimeout   how long a streamed turn may stay open. Generous next to a turn measured
 *                        in seconds, because the cost of cutting a slow answer off is a user who
 *                        sees nothing, while the cost of holding a dead connection is one idle
 *                        socket that the container reaps anyway.
 * @param answerValidationEnabled whether every chat answer is checked by {@code AnswerValidator}
 *                        before it is returned. Defaults to on; a separate switch from the feature
 *                        itself exists so an environment can turn the check off without a code
 *                        change, the same reason every other coach behaviour here is a property
 *                        rather than a constant.
 * @param turnsPerHour    how many orchestrator turns and session drafts one user may start in an hour
 * @param turnsPerDay     how many one user may start in a day
 * @param analyticsBaseUrl root of the service that owns the goal and food progress read model
 */
@ConfigurationProperties(prefix = "fitsync.coach")
public record CoachProperties(
        Integer maxToolCalls,
        Duration catalogueTimeout,
        String workoutBaseUrl,
        String nutritionBaseUrl,
        String userBaseUrl,
        Duration streamTimeout,
        Boolean answerValidationEnabled,
        Integer turnsPerHour,
        Integer turnsPerDay,
        String analyticsBaseUrl) {

    private static final int DEFAULT_MAX_TOOL_CALLS = 4;
    private static final Duration DEFAULT_CATALOGUE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(2);
    private static final int DEFAULT_TURNS_PER_HOUR = 30;
    private static final int DEFAULT_TURNS_PER_DAY = 150;

    /** The properties as they were before the limits existed, for callers that never set them. */
    public CoachProperties(Integer maxToolCalls, Duration catalogueTimeout, String workoutBaseUrl,
                           String nutritionBaseUrl, String userBaseUrl, Duration streamTimeout,
                           Boolean answerValidationEnabled) {
        this(maxToolCalls, catalogueTimeout, workoutBaseUrl, nutritionBaseUrl, userBaseUrl, streamTimeout,
                answerValidationEnabled, null, null, null);
    }

    /** The properties as they were before the analytics root existed. */
    public CoachProperties(Integer maxToolCalls, Duration catalogueTimeout, String workoutBaseUrl,
                           String nutritionBaseUrl, String userBaseUrl, Duration streamTimeout,
                           Boolean answerValidationEnabled, Integer turnsPerHour, Integer turnsPerDay) {
        this(maxToolCalls, catalogueTimeout, workoutBaseUrl, nutritionBaseUrl, userBaseUrl, streamTimeout,
                answerValidationEnabled, turnsPerHour, turnsPerDay, null);
    }

    @ConstructorBinding
    public CoachProperties {
        turnsPerHour = turnsPerHour == null || turnsPerHour.intValue() < 1
                ? Integer.valueOf(DEFAULT_TURNS_PER_HOUR)
                : turnsPerHour;
        turnsPerDay = turnsPerDay == null || turnsPerDay.intValue() < 1
                ? Integer.valueOf(DEFAULT_TURNS_PER_DAY)
                : turnsPerDay;
        maxToolCalls = maxToolCalls == null || maxToolCalls.intValue() < 1
                ? Integer.valueOf(DEFAULT_MAX_TOOL_CALLS)
                : maxToolCalls;
        catalogueTimeout = catalogueTimeout == null ? DEFAULT_CATALOGUE_TIMEOUT : catalogueTimeout;
        workoutBaseUrl = workoutBaseUrl == null ? "http://localhost:8082" : stripTrailingSlash(workoutBaseUrl);
        nutritionBaseUrl = nutritionBaseUrl == null ? "http://localhost:8083" : stripTrailingSlash(nutritionBaseUrl);
        userBaseUrl = userBaseUrl == null ? "http://localhost:8081" : stripTrailingSlash(userBaseUrl);
        analyticsBaseUrl = analyticsBaseUrl == null ? "http://localhost:8085" : stripTrailingSlash(analyticsBaseUrl);
        streamTimeout = streamTimeout == null ? DEFAULT_STREAM_TIMEOUT : streamTimeout;
        answerValidationEnabled = answerValidationEnabled == null ? Boolean.TRUE : answerValidationEnabled;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
