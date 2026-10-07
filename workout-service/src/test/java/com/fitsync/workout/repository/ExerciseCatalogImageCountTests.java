package com.fitsync.workout.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseImageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExerciseCatalogImageCountTests {

    private static final String SOURCE = "free-exercise-db";

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @BeforeEach
    void clearExercises() {
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void countsOnlySourceExercisesThatHaveAtLeastOneImage() {
        catalogExercise("bench", SOURCE, true);
        catalogExercise("squat", SOURCE, false);
        catalogExercise("row", "another-source", true);

        assertThat(exerciseRepository.countByExternalSource(SOURCE)).isEqualTo(2);
        assertThat(exerciseRepository.countByExternalSourceWithImages(SOURCE)).isEqualTo(1);
    }

    @Test
    void isZeroWhenTheCatalogWasImportedWithoutAnyImage() {
        catalogExercise("bench", SOURCE, false);
        catalogExercise("squat", SOURCE, false);

        assertThat(exerciseRepository.countByExternalSource(SOURCE)).isEqualTo(2);
        assertThat(exerciseRepository.countByExternalSourceWithImages(SOURCE)).isZero();
    }

    private void catalogExercise(String code, String source, boolean withImage) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(code);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.BACK);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        entity.setExternalSource(source);
        entity.setExternalRef(code);
        if (withImage) {
            ExerciseImageEntity image = new ExerciseImageEntity();
            image.setImageId(UUID.randomUUID());
            image.setExercise(entity);
            image.setPosition(0);
            image.setUrl("https://media.test/exercises/" + code + "/0.jpg");
            entity.getImages().add(image);
        }
        exerciseRepository.save(entity);
    }
}
