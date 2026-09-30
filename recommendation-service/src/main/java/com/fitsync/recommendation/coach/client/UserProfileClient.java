package com.fitsync.recommendation.coach.client;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.reading.ProgressInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the readiness questionnaire and the progress insight from user-service.
 *
 * <p>Over HTTP with the caller's own token, like the catalogues, rather than through a projection.
 * That is the deliberate choice for this particular data: the questionnaire is health information
 * the user entered about themselves, and projecting it into this service's database would spread it
 * across another schema, another backup and another set of logs to gain a few milliseconds on a
 * call that already waits seconds for a model.
 *
 * <p>What comes back is used by the draft validator to exclude exercises. It is not added to the
 * system prompt: the coach does not need to discuss a user's injuries in order to avoid loading
 * them, and the narrower the path this data travels, the better.
 */
@Component
public class UserProfileClient extends CatalogueClient {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileClient.class);

    private static final String HEALTH_READINESS = "/api/v1/users/me/health-readiness";
    private static final String PROGRESS_INSIGHT = "/api/v1/users/me/insights/progress";

    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader insightReader;

    public UserProfileClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                             CoachProperties properties,
                             ObjectMapper objectMapper) {
        super(builder, properties.userBaseUrl(), "user");
        this.insightReader = objectMapper.readerFor(ProgressInsight.class);
    }

    /** @return the questionnaire as JSON, or null when it could not be read */
    public String healthReadiness(String bearerToken) {
        return get(HEALTH_READINESS, params(), bearerToken);
    }

    /**
     * The pre-computed weight facts a progress-page reading is built from. Null covers both "the
     * service could not be reached" and "the body was not valid JSON for this contract".
     */
    public ProgressInsight progressInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        params.put("timezone", timezone);
        String body = get(PROGRESS_INSIGHT, params, bearerToken);
        if (body == null) {
            return null;
        }
        try {
            return insightReader.readValue(body);
        } catch (JsonProcessingException ex) {
            LOG.warn("event=progress_insight_unreadable");
            return null;
        }
    }
}
