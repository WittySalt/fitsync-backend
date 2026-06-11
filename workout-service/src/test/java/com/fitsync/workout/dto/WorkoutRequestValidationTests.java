package com.fitsync.workout.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.GoalFocus;
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
}
