package com.fitsync.recommendation.coach.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CoachTurnEntityTests {

    @Test
    @DisplayName("a rejected answer's verdict fits the column with the rule and its detail")
    void keepsARejectedAnswersVerdictWhole() {
        CoachTurnEntity turn = new CoachTurnEntity();

        turn.setGuardrailVerdict("ANSWER_REJECTED:SENTENCE_WITHOUT_CITATION:12");

        assertThat(turn.getGuardrailVerdict()).isEqualTo("ANSWER_REJECTED:SENTENCE_WITHOUT_CITATION:12");
    }

    @Test
    @DisplayName("a verdict longer than the column keeps its start and never fails the turn")
    void shortensAVerdictLongerThanTheColumn() {
        CoachTurnEntity turn = new CoachTurnEntity();

        turn.setGuardrailVerdict("ANSWER_REJECTED:NUMBER_NOT_IN_RECORDS:" + "9".repeat(80));

        assertThat(turn.getGuardrailVerdict()).hasSize(CoachTurnEntity.GUARDRAIL_VERDICT_LENGTH);
        assertThat(turn.getGuardrailVerdict()).startsWith("ANSWER_REJECTED:NUMBER_NOT_IN_RECORDS:");
    }

    @Test
    @DisplayName("the column is as long as the entity says")
    void declaresTheColumnLength() throws NoSuchFieldException {
        Column column = CoachTurnEntity.class.getDeclaredField("guardrailVerdict").getAnnotation(Column.class);

        assertThat(column.length()).isEqualTo(CoachTurnEntity.GUARDRAIL_VERDICT_LENGTH).isEqualTo(64);
    }
}
