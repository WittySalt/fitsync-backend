package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkoutRequestValidationTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectBlankPlanName() {
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of());

        Set<ConstraintViolation<CreateWorkoutPlanRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shouldAcceptATempoBetweenOneAndTenSecondsInTenths() {
        assertTrue(validator.validate(planExerciseWithTempo("1.0")).isEmpty());
        assertTrue(validator.validate(planExerciseWithTempo("2.5")).isEmpty());
        assertTrue(validator.validate(planExerciseWithTempo("10.0")).isEmpty());
        assertTrue(validator.validate(planExerciseWithTempo(null)).isEmpty());
    }

    @Test
    void shouldRejectATempoOutsideOneToTenSecondsOrFinerThanATenth() {
        for (String tempo : List.of("0.5", "10.5", "2.55")) {
            assertTrue(validator.validate(planExerciseWithTempo(tempo)).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString().equals("tempoSeconds")), tempo);
        }
        WorkoutExerciseLogInput log = new WorkoutExerciseLogInput(UUID.randomUUID(), 1, null, null, null, null, null,
                null, null, null, null, new BigDecimal("12"));
        assertFalse(validator.validate(log).isEmpty());
    }

    private WorkoutPlanExerciseInput planExerciseWithTempo(String tempo) {
        return new WorkoutPlanExerciseInput(UUID.randomUUID(), 1, 3, 10, null, null, null, 60, null, List.of(),
                tempo == null ? null : new BigDecimal(tempo));
    }

    @Test
    void shouldRejectNegativeExerciseDuration() {
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Run",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T08:00:00Z"),
                Instant.parse("2026-06-11T08:30:00Z"),
                30,
                250,
                6,
                null,
                List.of(new WorkoutExerciseLogInput(
                        UUID.randomUUID(),
                        1,
                        null,
                        null,
                        null,
                        null,
                        -5,
                        null,
                        null,
                        null,
                        null)));

        Set<ConstraintViolation<UpsertWorkoutSessionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().contains("durationSeconds")));
    }

    @Test
    void shouldRejectSessionDistanceAboveOneThousandKm() {
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Ultra Swim",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T08:00:00Z"),
                Instant.parse("2026-06-11T08:30:00Z"),
                30,
                250,
                6,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("1000.01"));

        Set<ConstraintViolation<UpsertWorkoutSessionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().contains("distanceKm")));
    }

    @Test
    void shouldRejectNegativeSessionDistance() {
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Negative Distance Swim",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T08:00:00Z"),
                Instant.parse("2026-06-11T08:30:00Z"),
                30,
                250,
                6,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("-1.00"));

        Set<ConstraintViolation<UpsertWorkoutSessionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation -> violation.getPropertyPath().toString().contains("distanceKm")));
    }
}
