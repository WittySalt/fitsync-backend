package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiaryFiguresTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String body) {
        return DiaryFigures.read(objectMapper.reader(), body);
    }

    @Test
    @DisplayName("a body with none of the new fields gives no figures at all")
    void anOldBodyGivesNothing() {
        JsonNode summary = json("{\"totalCalories\":1200,\"totalProteinGrams\":78}");

        assertThat(DiaryFigures.day(summary, null)).isEmpty();
        assertThat(DiaryFigures.day(null, null)).isEmpty();
        assertThat(DiaryFigures.micronutrients(null)).isEmpty();
        assertThat(DiaryFigures.micronutrients(json("{}"))).isEmpty();
    }

    @Test
    @DisplayName("a missing body or one that is not JSON reads as null")
    void unreadableBodiesAreNull() {
        assertThat(DiaryFigures.read(objectMapper.reader(), null)).isNull();
        assertThat(DiaryFigures.read(objectMapper.reader(), "{nope")).isNull();
    }

    @Test
    @DisplayName("a balance entry without an amount or a reference is skipped")
    void incompleteBalanceEntriesAreSkipped() {
        Map<String, Object> figures = DiaryFigures.day(json("""
                {"balance":[{"code":"FIBER","kind":"TARGET","amount":10},
                            {"code":"UNKNOWN","kind":"TARGET","amount":1,"reference":2}]}"""), null);

        assertThat(figures).isEmpty();
    }

    @Test
    @DisplayName("only three vitamins and minerals are named, lowest first, and days without a log give none")
    void namesTheLowestThree() {
        Map<String, Object> figures = DiaryFigures.micronutrients(json(DiaryFixtures.NUTRIENTS));

        assertThat(figures.get("lowest")).asList().containsExactly(
                "Vitamin D 12% (1.8 of 15 mcg)", "Vitamin B12 50% (1.2 of 2.4 mcg)", "Iron 75% (6 of 8 mg)");
        assertThat(figures.get("aboveUpperLimit")).asList()
                .containsExactly("Vitamin A 1200 mcg on average, above its upper limit of 900 mcg");

        assertThat(DiaryFigures.micronutrients(json("{\"daysLogged\":0,\"nutrients\":[]}"))).isEmpty();
    }
}
