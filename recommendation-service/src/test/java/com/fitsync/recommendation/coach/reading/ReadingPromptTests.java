package com.fitsync.recommendation.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The system prompts of the three pages. The workout prompt is pinned to the exact text it had
 * before the prompt was assembled from parts, because a changed word in it changes what the model
 * writes for every workout reading.
 */
class ReadingPromptTests {

    private static final String WORKOUT_PROMPT_AS_BEFORE = """
            You are FitSync's coach, noticing patterns in one page of pre-computed facts. You never \
            give advice, an instruction, a suggestion or a question - you only observe what the facts \
            show together, which the numbers alone do not.

            Answer with exactly one JSON object and nothing else: {"headline": "...", "body": "..."}

            Rules for the headline:
            - One sentence, at most 80 characters.
            - It does not need to contain a number.

            Rules for the body:
            - One or two sentences, at most 320 characters, on a single line.
            - It connects at least two of the facts and says what they show together.
            - Every number you write must be copied exactly from the facts you were given - do not \
            calculate a percentage, ratio or difference yourself.
            - Immediately after each number or claim taken from a fact, write that fact's number in \
            square brackets, like [2].

            Style:
            - Second person, plain English.
            - No advice, no instruction, no suggestion, no question, no exclamation mark, no emoji, \
            no medical statement.
            - No dash used as punctuation.
            - Say only what happened. Never write that something did not happen, and do not use \
            the words no, not, never or without.
            - A fact whose label ends in "record" means that a record was set.
            - Say that something went up, went down or stayed the same only when the note of a \
            fact you cite in that sentence says Up, Down or Unchanged, or that fact is a record.
            - Every sentence of the body cites at least one fact.

            Facts are given as a JSON array, each with a number, a label, a value, a unit, a short \
            note and a longer detail. The period label tells you whether this covers the current or \
            the previous week. A fact labelled Pace gives minutes and seconds for one kilometre, \
            so a lower pace is a faster one; its note says how many seconds faster or slower it was.

            The example below shows the form only. Its numbers belong to someone else, so none \
            of them may appear in your answer.

            Example input:
            period: THIS_WEEK
            facts: [
              {"number":1,"label":"Weight moved per session","value":"2,480","unit":"kg","note":"Down 6%","detail":"2,640 kg the week before"},
              {"number":2,"label":"Session length","value":"45","unit":"min","note":"Down 10 min","detail":"55 min the week before"},
              {"number":3,"label":"Barbell Deadlift record","value":"80","unit":"kg","note":"Thursday","detail":"75 kg before"},
              {"number":4,"label":"Sessions with leg work","value":"3","unit":"of 5","note":"","detail":"Back Squat"}
            ]

            Example output:
            {"headline": "Shorter sessions moved less weight and still set a record.", "body": "Each session moved 2,480 kg [1] in 45 min [2], both lower than the week before. Thursday still brought a deadlift of 80 kg [3], up from 75 kg [3]."}""";

    @Test
    @DisplayName("the workout prompt is the text it always was")
    void workoutPromptIsUnchanged() {
        assertThat(WorkoutReadingPage.SYSTEM_PROMPT).isEqualTo(WORKOUT_PROMPT_AS_BEFORE);
    }

    @Test
    @DisplayName("the nutrition and progress prompts share the voice rules and have no record or pace wording")
    void otherPromptsShareTheRulesAndDropTheWorkoutParts() {
        String sharedOpening = WORKOUT_PROMPT_AS_BEFORE.substring(0, WORKOUT_PROMPT_AS_BEFORE.indexOf("- A fact whose label"));
        for (String prompt : new String[] {NutritionReadingPage.SYSTEM_PROMPT, ProgressReadingPage.SYSTEM_PROMPT}) {
            assertThat(prompt).startsWith(sharedOpening);
            assertThat(prompt).contains("Every sentence of the body cites at least one fact.");
            assertThat(prompt).doesNotContain("record").doesNotContain("Pace").doesNotContain("Deadlift");
            assertThat(prompt).contains("says Up, Down or Unchanged.\n");
        }
    }

    @Test
    @DisplayName("each new prompt ends with one example in the same form, for its own page")
    void eachNewPromptHasItsOwnExample() {
        assertThat(NutritionReadingPage.SYSTEM_PROMPT)
                .contains("period: PREVIOUS_WEEK")
                .contains("Example output:")
                .contains("\"label\":\"Protein a day\"");
        assertThat(ProgressReadingPage.SYSTEM_PROMPT)
                .contains("period: LAST_28_DAYS")
                .contains("Example output:")
                .contains("\"label\":\"Change a week\"");
    }

    @Test
    @DisplayName("the examples' own answers pass the validator against their own facts")
    void examplesAreValidAnswers() {
        ReadingValidator validator = new ReadingValidator(new com.fitsync.recommendation.coach.guardrail.CoachGuardrail());
        java.util.List<ReadingFact> nutrition = java.util.List.of(
                fact(1, "PROTEIN_PER_DAY", "Protein a day", "118.4", "g", "Down 5%", "124.6 g the week before; target 130 g"),
                fact(2, "PROTEIN_BREAKFAST", "Protein at breakfast", "14.2", "g", "12% of the day", ""),
                fact(3, "PROTEIN_LUNCH_DINNER", "Protein at lunch and dinner", "81.7", "g", "69% of the day", ""),
                fact(4, "CALORIES_PER_DAY", "Calories a day", "1,940", "kcal", "Unchanged",
                        "1,940 kcal the week before; target 2,050 kcal"));
        assertThat(validator.validate("Protein slipped and most of it came late in the day.",
                "You ate 118.4 g of protein a day [1], down from 124.6 g, and 81.7 g [3] of it came at lunch and dinner. "
                        + "Breakfast gave 14.2 g [2], while calories stayed the same at 1,940 kcal [4].",
                nutrition).violations()).isEmpty();

        java.util.List<ReadingFact> progress = java.util.List.of(
                fact(1, "WEIGHT", "Weight", "83.6", "kg", "3 Mar", "9 weigh-ins in eight weeks"),
                fact(2, "WEEKLY_RATE", "Change a week", "0.35", "kg", "Down", "over the last 28 days"),
                fact(3, "GOAL", "Goal", "80", "kg", "by 30 Apr", "12 Apr at this rate"));
        assertThat(validator.validate("At this rate you reach your goal a little early.",
                "You weighed 83.6 kg on 3 Mar [1], which is down 0.35 kg a week [2]. "
                        + "Your goal is 80 kg by 30 Apr [3], and this rate gets there on 12 Apr [3].",
                progress).violations()).isEmpty();
    }

    private static ReadingFact fact(int number, String key, String label, String value, String unit, String note,
                                    String detail) {
        return new ReadingFact(number, key, label, value, unit, new java.math.BigDecimal(value.replace(",", "")),
                note, detail);
    }
}
