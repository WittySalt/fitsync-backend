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
 * Reads the coach's view of the readiness questionnaire and the progress insight from user-service.
 *
 * <p>Over HTTP with the caller's own token, like the catalogues, rather than through a projection.
 * That is the deliberate choice for this particular data: the questionnaire is health information
 * the user entered about themselves, and projecting it into this service's database would spread it
 * across another schema, another backup and another set of logs to gain a few milliseconds on a
 * call that already waits seconds for a model.
 *
 * <p>What comes back is used by the draft validator to exclude exercises. It is not added to the
 * system prompt: the coach does not need to discuss a user's injuries in order to avoid loading
 * them, and the narrower the path this data travels, the better. For the same reason the coach reads
 * the consent-gated coach context rather than the full questionnaire: user-service releases only the
 * injured areas, and none at all once the user has withdrawn consent to processing their health
 * answers.
 */
@Component
public class UserProfileClient extends CatalogueClient {

    private static final Logger LOG = LoggerFactory.getLogger(UserProfileClient.class);

    private static final String COACH_HEALTH_CONTEXT = "/api/v1/users/me/health-readiness/coach-context";
    private static final String PROGRESS_INSIGHT = "/api/v1/users/me/insights/progress";

    // A reader is immutable, which a shared ObjectMapper is not.
    private final ObjectReader insightReader;

    public UserProfileClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                             CoachProperties properties,
                             ObjectMapper objectMapper) {
        super(builder, properties.apiBaseUrl(), "user");
        this.insightReader = objectMapper.readerFor(ProgressInsight.class);
    }

    /**
     * @return the coach context as JSON ({@code healthDataConsent} and {@code injuryAreas}), or null
     *         when it could not be read
     */
    public String coachHealthContext(String bearerToken) {
        return get(COACH_HEALTH_CONTEXT, params(), bearerToken);
    }

    /**
     * The pre-computed weight facts a progress-page reading is built from. Null covers both "the
     * service could not be reached" and "the body was not valid JSON for this contract".
     */
    public ProgressInsight progressInsight(String bearerToken, String timezone) {
        Map<String, Object> params = params();
        String body = get(PROGRESS_INSIGHT, params, bearerToken, timezone);
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
