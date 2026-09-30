package com.fitsync.workout;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseCatalogSummaryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private com.fitsync.workout.repository.WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private com.fitsync.workout.repository.WorkoutPlanRepository workoutPlanRepository;

    @BeforeEach
    void clearExercises() {
        // Other test classes in this shared context leave sessions/plans referencing exercises;
        // those have to go first or the exercise delete trips their foreign key.
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void adminSeesCatalogSummaryGroupedByType() throws Exception {
        catalogExercise("summary-squat", ExerciseType.STRENGTH);
        catalogExercise("summary-bench", ExerciseType.STRENGTH);
        catalogExercise("summary-jog", ExerciseType.CARDIO);
        customExercise("summary-custom", ExerciseType.CARDIO);

        mockMvc.perform(get("/api/v1/internal/workouts/exercises/summary")
                        .with(authentication(TestJwt.authentication("ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.byType[0].type").value("STRENGTH"))
                .andExpect(jsonPath("$.byType[0].count").value(2))
                .andExpect(jsonPath("$.byType[1].type").value("CARDIO"))
                .andExpect(jsonPath("$.byType[1].count").value(1));
    }

    @Test
    void userCannotSeeCatalogSummary() throws Exception {
        mockMvc.perform(get("/api/v1/internal/workouts/exercises/summary")
                        .with(authentication(TestJwt.authentication("USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void catalogSummaryRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/internal/workouts/exercises/summary"))
                .andExpect(status().isUnauthorized());
    }

    private void catalogExercise(String code, ExerciseType type) {
        ExerciseEntity entity = baseExercise(code, type);
        entity.setCustom(false);
        exerciseRepository.save(entity);
    }

    private void customExercise(String code, ExerciseType type) {
        ExerciseEntity entity = baseExercise(code, type);
        entity.setCustom(true);
        entity.setOwnerUserId(UUID.randomUUID());
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
