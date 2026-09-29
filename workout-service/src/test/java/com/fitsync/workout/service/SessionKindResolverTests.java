package com.fitsync.workout.service;

import java.util.Arrays;
import java.util.List;

import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionKindResolverTests {

    private final SessionKindResolver resolver = new SessionKindResolver();

    @Test
    void strengthWorkoutTypeIsStrengthKind() {
        assertThat(resolver.resolve(WorkoutType.STRENGTH, List.of())).isEqualTo(SessionKind.STRENGTH);
    }

    @Test
    void cardioWorkoutTypeIsCardioKind() {
        assertThat(resolver.resolve(WorkoutType.CARDIO, List.of())).isEqualTo(SessionKind.CARDIO);
    }

    @Test
    void hiitWorkoutTypeIsCardioKind() {
        assertThat(resolver.resolve(WorkoutType.HIIT, List.of())).isEqualTo(SessionKind.CARDIO);
    }

    @Test
    void yogaWorkoutTypeIsMobilityKind() {
        assertThat(resolver.resolve(WorkoutType.YOGA, List.of())).isEqualTo(SessionKind.MOBILITY);
    }

    @Test
    void pilatesWorkoutTypeIsMobilityKind() {
        assertThat(resolver.resolve(WorkoutType.PILATES, List.of())).isEqualTo(SessionKind.MOBILITY);
    }

    @Test
    void mobilityWorkoutTypeIsMobilityKind() {
        assertThat(resolver.resolve(WorkoutType.MOBILITY, List.of())).isEqualTo(SessionKind.MOBILITY);
    }

    @Test
    void sportsWorkoutTypeIsSportKind() {
        assertThat(resolver.resolve(WorkoutType.SPORTS, List.of())).isEqualTo(SessionKind.SPORT);
    }

    @Test
    void customWorkoutTypeWithNoExercisesIsSportKind() {
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of())).isEqualTo(SessionKind.SPORT);
    }

    @Test
    void customWorkoutTypeWithOnlyNullExerciseTypesIsSportKind() {
        assertThat(resolver.resolve(WorkoutType.CUSTOM, Arrays.asList(null, null))).isEqualTo(SessionKind.SPORT);
    }

    @Test
    void nullWorkoutTypeIsTreatedAsCustom() {
        assertThat(resolver.resolve(null, List.of())).isEqualTo(SessionKind.SPORT);
        assertThat(resolver.resolve(null, List.of(ExerciseType.STRENGTH))).isEqualTo(SessionKind.STRENGTH);
    }

    @Test
    void customWorkoutTypeMapsStrengthAndSportExerciseTypesToStrengthKind() {
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of(ExerciseType.STRENGTH))).isEqualTo(SessionKind.STRENGTH);
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of(ExerciseType.SPORT))).isEqualTo(SessionKind.STRENGTH);
    }

    @Test
    void customWorkoutTypeMapsCardioExerciseTypeToCardioKind() {
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of(ExerciseType.CARDIO))).isEqualTo(SessionKind.CARDIO);
    }

    @Test
    void customWorkoutTypeMapsMobilityAndBalanceExerciseTypesToMobilityKind() {
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of(ExerciseType.MOBILITY))).isEqualTo(SessionKind.MOBILITY);
        assertThat(resolver.resolve(WorkoutType.CUSTOM, List.of(ExerciseType.BALANCE))).isEqualTo(SessionKind.MOBILITY);
    }

    @Test
    void customWorkoutTypeUsesTheMajorityExerciseTypeKind() {
        List<ExerciseType> exercises = List.of(
                ExerciseType.CARDIO,
                ExerciseType.STRENGTH,
                ExerciseType.CARDIO);

        assertThat(resolver.resolve(WorkoutType.CUSTOM, exercises)).isEqualTo(SessionKind.CARDIO);
    }

    @Test
    void customWorkoutTypeIgnoresNullExerciseTypesWhenCounting() {
        List<ExerciseType> exercises = Arrays.asList(null, ExerciseType.CARDIO, null, ExerciseType.CARDIO, ExerciseType.STRENGTH);

        assertThat(resolver.resolve(WorkoutType.CUSTOM, exercises)).isEqualTo(SessionKind.CARDIO);
    }

    @Test
    void customWorkoutTypeTieBreaksToTheEarliestExerciseAmongTiedKinds() {
        // Two mobility-mapped exercises and two strength-mapped exercises: a 2-2 tie where the
        // first exercise in the list (MOBILITY) must win, not whichever kind reaches 2 first.
        List<ExerciseType> exercises = List.of(
                ExerciseType.MOBILITY,
                ExerciseType.STRENGTH,
                ExerciseType.STRENGTH,
                ExerciseType.BALANCE);

        assertThat(resolver.resolve(WorkoutType.CUSTOM, exercises)).isEqualTo(SessionKind.MOBILITY);
    }

    @Test
    void customWorkoutTypeTieBreaksToTheEarliestExerciseWhenTheEarlierKindIsNotTheFirstToReachTheMax() {
        // STRENGTH reaches count 2 before CARDIO does, but CARDIO's first occurrence (index 0)
        // is earlier in the list, so CARDIO must win the 2-2 tie, not STRENGTH.
        List<ExerciseType> exercises = List.of(
                ExerciseType.CARDIO,
                ExerciseType.STRENGTH,
                ExerciseType.STRENGTH,
                ExerciseType.CARDIO);

        assertThat(resolver.resolve(WorkoutType.CUSTOM, exercises)).isEqualTo(SessionKind.CARDIO);
    }
}
