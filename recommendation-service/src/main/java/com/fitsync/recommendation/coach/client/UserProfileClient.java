package com.fitsync.recommendation.coach.client;

import com.fitsync.recommendation.coach.config.CoachProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the readiness questionnaire from user-service.
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

    private static final String HEALTH_READINESS = "/api/v1/users/me/health-readiness";

    public UserProfileClient(@Qualifier("coachRestClientBuilder") RestClient.Builder builder,
                             CoachProperties properties) {
        super(builder, properties.userBaseUrl(), "user");
    }

    /** @return the questionnaire as JSON, or null when it could not be read */
    public String healthReadiness(String bearerToken) {
        return get(HEALTH_READINESS, params(), bearerToken);
    }
}
