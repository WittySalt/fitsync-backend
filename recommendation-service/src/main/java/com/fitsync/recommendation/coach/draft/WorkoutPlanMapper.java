package com.fitsync.recommendation.coach.draft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Turns an accepted draft into the request workout-service's plan endpoint expects.
 *
 * <p>Built as a plain map rather than by sharing workout-service's request record, because sharing
 * it would put one bounded context's DTO on another's classpath, and the whole point of accepting
 * through the API is that the coach writes exactly what a user would through the plan editor. The
 * field names here are that endpoint's public contract, and a test pins them.
 *
 * <p>Three fields the draft does not carry get fixed values. Goal focus is general fitness, workout
 * type is strength and difficulty is intermediate, because the draft has no basis for choosing any
 * of them and a guess dressed up as a choice would be worse than a default the user can edit. The
 * session's own facts, which the draft does carry, go through unchanged.
 */
@Component
public class WorkoutPlanMapper {

    /** workout-service caps a plan name at this many characters. */
    private static final int MAX_NAME = 120;
    private static final int MAX_DESCRIPTION = 1000;
    /** And an exercise note at this many, which is where the rationale goes. */
    private static final int MAX_NOTES = 300;

    public Map<String, Object> toCreatePlanRequest(SessionDraft draft) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", clip(blankTo(draft.title(), "Coach session"), MAX_NAME));
        request.put("description", clip(draft.summary(), MAX_DESCRIPTION));
        request.put("goalFocus", "GENERAL_FITNESS");
        request.put("workoutType", "STRENGTH");
        request.put("difficulty", "INTERMEDIATE");
        request.put("estimatedDurationMinutes", Integer.valueOf(Math.max(1, draft.estimatedMinutes())));

        List<Map<String, Object>> exercises = new ArrayList<>();
        int sequence = 1;
        for (DraftExercise exercise : draft.exercises()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("exerciseId", exercise.exerciseId());
            entry.put("sequence", Integer.valueOf(sequence++));
            entry.put("targetSets", Integer.valueOf(exercise.sets()));
            entry.put("targetReps", exercise.reps());
            entry.put("targetDurationSeconds", exercise.seconds());
            entry.put("restSeconds", Integer.valueOf(exercise.restSeconds()));
            entry.put("notes", clip(exercise.rationale(), MAX_NOTES));
            exercises.add(entry);
        }
        request.put("exercises", exercises);
        return request;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
