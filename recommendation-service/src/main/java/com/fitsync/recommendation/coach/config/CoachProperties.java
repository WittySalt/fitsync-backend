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
 * @param catalogueTimeout per-request timeout on a read from another service. Short, because a slow
 *                        read should degrade into an observation the model works around rather than
 *                        hold the turn open.
 * @param apiBaseUrl      root of the API gateway. The coach reads every other service through the
 *                        gateway's public paths, with the caller's own token, exactly as the app does;
 *                        it holds no address of any service behind it.
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
 */
@ConfigurationProperties(prefix = "fitsync.coach")
public record CoachProperties(
        Integer maxToolCalls,
        Duration catalogueTimeout,
        String apiBaseUrl,
        Duration streamTimeout,
        Boolean answerValidationEnabled,
        Integer turnsPerHour,
        Integer turnsPerDay) {

    private static final int DEFAULT_MAX_TOOL_CALLS = 4;
    private static final Duration DEFAULT_CATALOGUE_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofMinutes(2);
    private static final int DEFAULT_TURNS_PER_HOUR = 30;
    private static final int DEFAULT_TURNS_PER_DAY = 150;
    private static final String DEFAULT_API_BASE_URL = "http://localhost:8088";

    /** The properties without the limits, which take their defaults. */
    public CoachProperties(Integer maxToolCalls, Duration catalogueTimeout, String apiBaseUrl,
                           Duration streamTimeout, Boolean answerValidationEnabled) {
        this(maxToolCalls, catalogueTimeout, apiBaseUrl, streamTimeout, answerValidationEnabled, null, null);
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
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? DEFAULT_API_BASE_URL : stripTrailingSlash(apiBaseUrl);
        streamTimeout = streamTimeout == null ? DEFAULT_STREAM_TIMEOUT : streamTimeout;
        answerValidationEnabled = answerValidationEnabled == null ? Boolean.TRUE : answerValidationEnabled;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
