package com.fitsync.recommendation.coach.draft;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shape of the request the coach sends to workout-service's plan endpoint.
 *
 * <p>These field names are another bounded context's public contract, pinned here because the
 * mapper builds a map rather than sharing that context's request type. If workout-service renames
 * a field, this test is where the coach finds out, rather than a user finding out on accept.
 */
class WorkoutPlanMapperTests {

    private static final UUID BENCH = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID PLANK = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final WorkoutPlanMapper mapper = new WorkoutPlanMapper();

    @Test
    @DisplayName("the draft's own facts pass through, and the three it lacks get editable defaults")
    void mapsADraftToThePlanContract() {
        Map<String, Object> request = mapper.toCreatePlanRequest(draft());

        assertThat(request)
                .containsEntry("name", "Upper body push")
                .containsEntry("description", "Because you have not pressed this week.")
                .containsEntry("estimatedDurationMinutes", 42)
                .containsEntry("goalFocus", "GENERAL_FITNESS")
                .containsEntry("workoutType", "STRENGTH")
                .containsEntry("difficulty", "INTERMEDIATE");
    }

    @Test
    @DisplayName("exercises keep their order, targets and rationale, in the field names workout-service reads")
    @SuppressWarnings("unchecked")
    void mapsEachExercise() {
        List<Map<String, Object>> exercises =
                (List<Map<String, Object>>) mapper.toCreatePlanRequest(draft()).get("exercises");

        assertThat(exercises).hasSize(2);
        assertThat(exercises.get(0))
                .containsEntry("exerciseId", BENCH)
                .containsEntry("sequence", 1)
                .containsEntry("targetSets", 4)
                .containsEntry("targetReps", 8)
                .containsEntry("targetDurationSeconds", null)
                .containsEntry("restSeconds", 90)
                .containsEntry("notes", "Main pressing movement.");
        assertThat(exercises.get(1))
                .containsEntry("exerciseId", PLANK)
                .containsEntry("sequence", 2)
                .containsEntry("targetReps", null)
                .containsEntry("targetDurationSeconds", 45);
    }

    @Test
    @DisplayName("text longer than workout-service allows is clipped rather than rejected there")
    @SuppressWarnings("unchecked")
    void clipsToTheDownstreamLimits() {
        String longTitle = "t".repeat(200);
        String longRationale = "r".repeat(400);
        SessionDraft draft = new SessionDraft(longTitle, List.of("CHEST"), 42,
                List.of(new DraftExercise(BENCH, "Bench", 3, 10, null, 60, longRationale)), "s");

        Map<String, Object> request = mapper.toCreatePlanRequest(draft);
        Map<String, Object> exercise = ((List<Map<String, Object>>) request.get("exercises")).get(0);

        assertThat((String) request.get("name")).hasSize(120);
        assertThat((String) exercise.get("notes")).hasSize(300);
    }

    @Test
    @DisplayName("a draft with no title still gets a plan name, because the field is required downstream")
    void suppliesANameWhenTheDraftHasNone() {
        SessionDraft draft = new SessionDraft("  ", List.of(), 30,
                List.of(new DraftExercise(BENCH, "Bench", 3, 10, null, 60, "r")), "s");

        assertThat(mapper.toCreatePlanRequest(draft)).containsEntry("name", "Coach session");
    }

    private static SessionDraft draft() {
        return new SessionDraft(
                "Upper body push",
                List.of("CHEST", "CORE"),
                42,
                List.of(
                        new DraftExercise(BENCH, "Barbell Bench Press", 4, 8, null, 90, "Main pressing movement."),
                        new DraftExercise(PLANK, "Plank Hold", 3, null, 45, 45, "Bracing to finish.")),
                "Because you have not pressed this week.");
    }
}
