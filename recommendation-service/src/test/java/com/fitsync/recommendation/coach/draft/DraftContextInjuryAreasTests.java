package com.fitsync.recommendation.coach.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** The plan draft uses injured areas only while the user consents to their health answers being used. */
class DraftContextInjuryAreasTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void usesTheAreasWhenConsentIsGiven() {
        String body = "{\"healthDataConsent\":true,\"injuryAreas\":[\"left knee\",\"LOWER_BACK\",\" \"]}";

        assertThat(DraftContextAssembler.injuryAreas(body, objectMapper))
                .containsExactly("LEFT_KNEE", "LOWER_BACK");
    }

    @Test
    void usesNothingWithoutConsentEvenIfAreasWereSent() {
        String body = "{\"healthDataConsent\":false,\"injuryAreas\":[\"LEFT_KNEE\"]}";

        assertThat(DraftContextAssembler.injuryAreas(body, objectMapper)).isEmpty();
    }

    @Test
    void treatsAMissingConsentFieldAsNoConsent() {
        assertThat(DraftContextAssembler.injuryAreas("{\"injuryAreas\":[\"LEFT_KNEE\"]}", objectMapper)).isEmpty();
    }

    @Test
    void treatsAnUnreachableOrUnreadableAnswerAsNoInjuryInformation() {
        assertThat(DraftContextAssembler.injuryAreas(null, objectMapper)).isEmpty();
        assertThat(DraftContextAssembler.injuryAreas("not json", objectMapper)).isEmpty();
    }
}
