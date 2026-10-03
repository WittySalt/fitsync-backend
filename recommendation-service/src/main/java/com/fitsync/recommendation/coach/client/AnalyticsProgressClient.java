package com.fitsync.recommendation.coach.client;

import java.time.LocalDate;
import java.util.Map;

import com.fitsync.recommendation.coach.config.CoachProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the Goal and Food pages' figures from analytics-service, which owns the weight and food read
 * model those pages are built from.
 *
 * <p>Over HTTP with the caller's own token and zone, like every other read the coach makes, so the
 * coach sees exactly the numbers the page shows and nothing of anyone else's.
 */
@Component
public class AnalyticsProgressClient extends CatalogueClient {

    private static final String GOAL_PROGRESS = "/api/v1/analytics/progress/goal";
    private static final String FOOD_PROGRESS = "/api/v1/analytics/progress/food";

    public AnalyticsProgressClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                                   CoachProperties properties) {
        super(builder, properties.analyticsBaseUrl(), "analytics");
    }

    /** The Goal page's figures for one period, as JSON, or null when they could not be read. */
    public String goalProgress(String bearerToken, String timezone, LocalDate from, LocalDate to) {
        return get(GOAL_PROGRESS, period(from, to), bearerToken, timezone);
    }

    /** The Food page's figures for one period, as JSON, or null when they could not be read. */
    public String foodProgress(String bearerToken, String timezone, LocalDate from, LocalDate to) {
        return get(FOOD_PROGRESS, period(from, to), bearerToken, timezone);
    }

    private static Map<String, Object> period(LocalDate from, LocalDate to) {
        Map<String, Object> params = params();
        params.put("from", from);
        params.put("to", to);
        return params;
    }
}
