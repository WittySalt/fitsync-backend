package com.fitsync.recommendation.coach;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.orchestrator.CoachAction;
import com.fitsync.recommendation.coach.orchestrator.CoachActionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The parser between the model and the loop.
 *
 * <p>The day-zero spike measured 100% well-formed JSON from {@code llama3.2:3b} over ninety trials,
 * so the strict path is the expected one and the first test covers it. The rest cover the two ways
 * small models actually fail — a markdown fence, and prose wrapped around the object — because a
 * model that is right ninety-nine times in a hundred still fails once an hour in front of an
 * assessor.
 *
 * <p>Anything beyond those is reported unparseable rather than guessed at, which lets the loop ask
 * again instead of acting on a misreading.
 */
class CoachActionParserTests {

    private final CoachActionParser parser = new CoachActionParser(new ObjectMapper());

    @Test
    @DisplayName("a bare tool action parses")
    void toolAction() {
        CoachAction action = parser.parse("{\"tool\":\"searchExercises\",\"args\":{\"muscleGroup\":\"CHEST\"}}");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.TOOL);
        assertThat(action.tool()).isEqualTo("searchExercises");
        assertThat(action.args().path("muscleGroup").asText()).isEqualTo("CHEST");
    }

    @Test
    @DisplayName("a bare answer parses")
    void answerAction() {
        CoachAction action = parser.parse("{\"answer\":\"You trained 4 of 5 planned days.\"}");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.ANSWER);
        assertThat(action.answer()).isEqualTo("You trained 4 of 5 planned days.");
    }

    @Test
    @DisplayName("a markdown fence is stripped rather than failing the turn")
    void fencedJson() {
        CoachAction action = parser.parse("""
                ```json
                {"tool":"readFacts","args":{}}
                ```""");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.TOOL);
        assertThat(action.tool()).isEqualTo("readFacts");
    }

    @Test
    @DisplayName("an object wrapped in prose is recovered")
    void objectInsideProse() {
        CoachAction action = parser.parse(
                "Sure, let me check that for you. {\"tool\":\"readRecentTraining\",\"args\":{\"days\":7}} Hope that helps.");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.TOOL);
        assertThat(action.tool()).isEqualTo("readRecentTraining");
        assertThat(action.args().path("days").asInt()).isEqualTo(7);
    }

    @Test
    @DisplayName("a tool call with no args still parses, with an empty object")
    void toolWithoutArgs() {
        CoachAction action = parser.parse("{\"tool\":\"readFacts\"}");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.TOOL);
        assertThat(action.args()).isNotNull();
        assertThat(action.args().isObject()).isTrue();
    }

    @Test
    @DisplayName("an answer wins when the model emits both, since it is the terminal step")
    void answerWinsOverTool() {
        CoachAction action = parser.parse("{\"answer\":\"Here you go.\",\"tool\":\"readFacts\"}");

        assertThat(action.kind()).isEqualTo(CoachAction.Kind.ANSWER);
    }

    @Test
    @DisplayName("prose alone is unparseable, so the loop asks again instead of guessing")
    void proseIsUnparseable() {
        assertThat(parser.parse("I think you should train chest today.").kind())
                .isEqualTo(CoachAction.Kind.UNPARSEABLE);
    }

    @Test
    @DisplayName("an object with neither field is unparseable")
    void emptyObjectIsUnparseable() {
        assertThat(parser.parse("{}").kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
        assertThat(parser.parse("{\"thinking\":\"hmm\"}").kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
    }

    @Test
    @DisplayName("nothing at all is unparseable rather than a crash")
    void emptyInput() {
        assertThat(parser.parse(null).kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
        assertThat(parser.parse("").kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
        assertThat(parser.parse("   ").kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
    }

    @Test
    @DisplayName("a blank answer is not an answer")
    void blankAnswerIsUnparseable() {
        assertThat(parser.parse("{\"answer\":\"  \"}").kind()).isEqualTo(CoachAction.Kind.UNPARSEABLE);
    }
}
