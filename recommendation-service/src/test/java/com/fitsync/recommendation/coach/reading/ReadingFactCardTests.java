package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Progress reading's facts name the card they belong to; other pages' facts do not. */
class ReadingFactCardTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("weight, rate and goal belong to the Goal card and calories to the Food card")
    void progressFactsNameTheirCard() {
        List<ReadingFact> facts = new ProgressFactsBuilder().build(PageInsightFixtures.progress(),
                PageInsightFixtures.nutrition());

        assertThat(facts).extracting(ReadingFact::key, ReadingFact::card).containsExactly(
                org.assertj.core.groups.Tuple.tuple("WEIGHT", "GOAL"),
                org.assertj.core.groups.Tuple.tuple("WEEKLY_RATE", "GOAL"),
                org.assertj.core.groups.Tuple.tuple("GOAL", "GOAL"),
                org.assertj.core.groups.Tuple.tuple("CALORIES_PER_DAY", "FOOD"));
        assertThat(facts).extracting(ReadingFact::number).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("the card is in the JSON only when set, survives a round trip, and leaves the facts hash alone")
    void cardInJsonAndNotInTheHash() throws Exception {
        ReadingFact plain = new ReadingFact(1, "SESSION_LENGTH", "Session length", "50", "min",
                BigDecimal.valueOf(50), "Unchanged", "50 min the week before");
        ReadingFact onCard = plain.withCard("TRAINING");

        JsonNode plainJson = objectMapper.valueToTree(plain);
        JsonNode cardJson = objectMapper.valueToTree(onCard);
        assertThat(plainJson.has("card")).isFalse();
        assertThat(cardJson.path("card").asText()).isEqualTo("TRAINING");

        List<ReadingFact> back = objectMapper.readValue(objectMapper.writeValueAsString(List.of(onCard, plain)),
                new TypeReference<List<ReadingFact>>() { });
        assertThat(back).containsExactly(onCard, plain);

        assertThat(CoachReadingService.hash("P", null, null, List.of(plain)))
                .isEqualTo(CoachReadingService.hash("P", null, null, List.of(onCard)));
    }
}
