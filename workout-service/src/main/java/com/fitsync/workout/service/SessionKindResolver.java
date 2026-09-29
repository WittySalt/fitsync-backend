package com.fitsync.workout.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutType;
import org.springframework.stereotype.Component;

/**
 * Decides the {@link SessionKind} of a workout session. The workout type decides it, and a custom
 * session takes the kind of most of its exercises. In the exercise catalogue the type SPORT means
 * jumping and throwing drills, which are strength work, and has nothing to do with a sport session.
 */
@Component
public class SessionKindResolver {

    private static final Map<ExerciseType, SessionKind> EXERCISE_TYPE_TO_KIND = Map.of(
            ExerciseType.STRENGTH, SessionKind.STRENGTH,
            ExerciseType.SPORT, SessionKind.STRENGTH,
            ExerciseType.CARDIO, SessionKind.CARDIO,
            ExerciseType.MOBILITY, SessionKind.MOBILITY,
            ExerciseType.BALANCE, SessionKind.MOBILITY);

    /**
     * @param workoutType           the session's workout type; a null value is treated as CUSTOM
     * @param exerciseTypesInOrder  the exercise types of the session's exercise logs, in sequence
     *                              order; null entries are ignored
     */
    public SessionKind resolve(WorkoutType workoutType, List<ExerciseType> exerciseTypesInOrder) {
        WorkoutType effectiveType = workoutType == null ? WorkoutType.CUSTOM : workoutType;
        return switch (effectiveType) {
            case STRENGTH -> SessionKind.STRENGTH;
            case CARDIO, HIIT -> SessionKind.CARDIO;
            case YOGA, PILATES, MOBILITY -> SessionKind.MOBILITY;
            case SPORTS -> SessionKind.SPORT;
            case CUSTOM -> resolveFromExercises(exerciseTypesInOrder);
        };
    }

    /**
     * The kind with the most mapped exercises wins. On a tie, the winner is whichever tied
     * kind belongs to the exercise that appears earliest in the list — found by scanning the
     * ordered, null-filtered kinds and returning the first one whose overall count matches the
     * maximum.
     */
    private SessionKind resolveFromExercises(List<ExerciseType> exerciseTypesInOrder) {
        if (exerciseTypesInOrder == null) {
            return SessionKind.SPORT;
        }
        List<SessionKind> kindsInOrder = exerciseTypesInOrder.stream()
                .filter(Objects::nonNull)
                .map(EXERCISE_TYPE_TO_KIND::get)
                .filter(Objects::nonNull)
                .toList();
        if (kindsInOrder.isEmpty()) {
            return SessionKind.SPORT;
        }

        Map<SessionKind, Long> counts = kindsInOrder.stream()
                .collect(Collectors.groupingBy(kind -> kind, Collectors.counting()));
        long maxCount = counts.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        return kindsInOrder.stream()
                .filter(kind -> counts.get(kind) == maxCount)
                .findFirst()
                .orElseThrow();
    }
}
