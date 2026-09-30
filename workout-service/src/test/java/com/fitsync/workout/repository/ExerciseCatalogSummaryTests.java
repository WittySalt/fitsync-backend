package com.fitsync.workout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseCatalogSummaryResponse;
import com.fitsync.workout.entity.ExerciseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExerciseCatalogSummaryTests {

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @BeforeEach
    void clearExercises() {
        // Other test classes in this shared context leave sessions/plans referencing exercises;
        // those have to go first or the exercise delete trips their foreign key.
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void summarizesCatalogExercisesByTypeDescendingThenByTypeAscending() {
        catalogExercise("squat", ExerciseType.STRENGTH);
        catalogExercise("bench", ExerciseType.STRENGTH);
        catalogExercise("row", ExerciseType.STRENGTH);
        catalogExercise("sprint", ExerciseType.CARDIO);
        catalogExercise("jog", ExerciseType.CARDIO);
        catalogExercise("stretch", ExerciseType.MOBILITY);
        customExercise("my-own-move", ExerciseType.CARDIO, UUID.randomUUID());

        ExerciseCatalogSummaryResponse summary = exerciseRepository.summarizeCatalogByType();

        // The custom exercise above is never counted: total and the CARDIO bucket both reflect
        // only the two catalogue cardio rows.
        assertThat(summary.total()).isEqualTo(6);
        assertThat(summary.byType()).extracting(
                        ExerciseCatalogSummaryResponse.ExerciseTypeCount::type,
                        ExerciseCatalogSummaryResponse.ExerciseTypeCount::count)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("STRENGTH", 3L),
                        org.assertj.core.groups.Tuple.tuple("CARDIO", 2L),
                        org.assertj.core.groups.Tuple.tuple("MOBILITY", 1L));
    }

    @Test
    void summaryIsEmptyWhenNoCatalogExercisesExist() {
        customExercise("my-own-move", ExerciseType.STRENGTH, UUID.randomUUID());

        ExerciseCatalogSummaryResponse summary = exerciseRepository.summarizeCatalogByType();

        assertThat(summary.total()).isZero();
        assertThat(summary.byType()).isEmpty();
    }

    private void catalogExercise(String code, ExerciseType type) {
        ExerciseEntity entity = baseExercise(code, type);
        entity.setCustom(false);
        entity.setOwnerUserId(null);
        exerciseRepository.save(entity);
    }

    private void customExercise(String code, ExerciseType type, UUID ownerUserId) {
        ExerciseEntity entity = baseExercise(code, type);
        entity.setCustom(true);
        entity.setOwnerUserId(ownerUserId);
        exerciseRepository.save(entity);
    }

    private ExerciseEntity baseExercise(String code, ExerciseType type) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(code);
        entity.setExerciseType(type);
        entity.setPrimaryMuscleGroup(MuscleGroup.BACK);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        return entity;
    }
}
