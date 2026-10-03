package com.fitsync.recommendation.coach.guardrail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoachVoiceTests {

    @ParameterizedTest
    @DisplayName("the coach may say what it cannot see")
    @ValueSource(strings = {
            "I do not have your meals from yesterday.",
            "Your records do not show a session on Sunday.",
            "I don't have anything logged for that day.",
            "The facts I was given do not include sleep."})
    void acceptsDoNotAsAStatement(String text) {
        assertThat(CoachVoice.toneViolations(text)).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("do not as an instruction is advice")
    @ValueSource(strings = {
            "Do not skip the warm up.",
            "You trained 4 days. Don't add a fifth.",
            "Rest tomorrow, and do not lift.",
            "Please do not train today."})
    void rejectsDoNotAsAnInstruction(String text) {
        assertThat(CoachVoice.toneViolations(text)).containsExactly("BANNED_PHRASE:do not");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Your goal pace status is AHEAD.", "The status is ON_TRACK for the week.",
            "Today reads NOT_LOGGED so far."})
    @DisplayName("a status code copied from a tool result is a tone violation")
    void rejectsRawStatusWords(String text) {
        assertThat(CoachVoice.toneViolations(text)).contains("RAW_STATUS_WORD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"You are ahead of your date.", "Your BMI is 22.5 and your HR zone is steady."})
    @DisplayName("plain words and short capitals such as BMI are fine")
    void acceptsPlainStatusWords(String text) {
        assertThat(CoachVoice.toneViolations(text)).doesNotContain("RAW_STATUS_WORD");
    }
}
