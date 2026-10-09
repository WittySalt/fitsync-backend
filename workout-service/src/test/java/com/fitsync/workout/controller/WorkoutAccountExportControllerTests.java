package com.fitsync.workout.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.WorkoutService;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the personal data export at GET /api/v1/workouts/me/export end to end, from the security filter chain
 * down to the H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutAccountExportControllerTests {

    private static final String EXPORT_URL = "/api/v1/workouts/me/export";

    private final CurrentUserPrincipal userA = new CurrentUserPrincipal(
            UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a"), "export-a@example.com", "A", "USER");
    private final CurrentUserPrincipal userB = new CurrentUserPrincipal(
            UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b"), "export-b@example.com", "B", "USER");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void clearData() {
        // Deleted sessions are invisible to the repository, so they are removed with plain SQL first. Plans and
        // exercises follow, because sessions and plans reference exercises.
        var entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();
            entityManager.createNativeQuery("delete from workout_sessions").executeUpdate();
            entityManager.getTransaction().commit();
        } finally {
            entityManager.close();
        }
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void exportReturnsOnlyTheCallersSessionsPlansAndCustomExercises() throws Exception {
        UUID squatId = catalogueExercise("export-squat");
        ExerciseView customA = customExercise(userA, "export-row-a");
        ExerciseView customB = customExercise(userB, "export-row-b");
        WorkoutPlanView planA = plan(userA, "A plan", squatId);
        WorkoutPlanView planB = plan(userB, "B plan", squatId);
        WorkoutSessionView sessionA = session(userA, "A session", squatId, new BigDecimal("110.00"),
                Instant.parse("2026-06-11T10:00:00Z"));
        WorkoutSessionView deletedA = session(userA, "A deleted session", squatId, new BigDecimal("90.00"),
                Instant.parse("2026-06-09T10:00:00Z"));
        workoutService.deleteSession(userA, deletedA.sessionId());
        WorkoutSessionView sessionB = session(userB, "B session", squatId, new BigDecimal("95.00"),
                Instant.parse("2026-06-11T10:00:00Z"));

        String body = mockMvc.perform(get(EXPORT_URL).with(authentication(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("workout-service"))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(sessionA.sessionId().toString()))
                .andExpect(jsonPath("$.sessions[0].exercises[0].exerciseId").value(squatId.toString()))
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets[0].reps").value(6))
                .andExpect(jsonPath("$.sessions[0].exercises[0].sets[0].weightKg").value(110.0))
                .andExpect(jsonPath("$.plans.length()").value(1))
                .andExpect(jsonPath("$.plans[0].planId").value(planA.planId().toString()))
                .andExpect(jsonPath("$.plans[0].exercises[0].exerciseId").value(squatId.toString()))
                .andExpect(jsonPath("$.customExercises.length()").value(1))
                .andExpect(jsonPath("$.customExercises[0].exerciseId").value(customA.exerciseId().toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body)
                .doesNotContain(customB.exerciseId().toString())
                .doesNotContain(planB.planId().toString())
                .doesNotContain(sessionB.sessionId().toString())
                .doesNotContain(deletedA.sessionId().toString());
    }

    @Test
    void exportOfAnEmptyAccountHasEmptyLists() throws Exception {
        mockMvc.perform(get(EXPORT_URL).with(authentication(as(userA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("workout-service"))
                .andExpect(jsonPath("$.sessions").isEmpty())
                .andExpect(jsonPath("$.plans").isEmpty())
                .andExpect(jsonPath("$.customExercises").isEmpty());
    }

    @Test
    void exportRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get(EXPORT_URL))
                .andExpect(status().isUnauthorized());
    }

    private static UsernamePasswordAuthenticationToken as(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }

    private UUID catalogueExercise(String code) {
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
        return exerciseRepository.save(entity).getExerciseId();
    }

    private ExerciseView customExercise(CurrentUserPrincipal user, String code) {
        return exerciseService.createCustomExercise(user, new CreateExerciseRequest(
                code, code, "Custom exercise", ExerciseType.STRENGTH, MuscleGroup.BACK,
                ExerciseEquipment.BARBELL, WorkoutDifficulty.BEGINNER));
    }

    private WorkoutPlanView plan(CurrentUserPrincipal user, String name, UUID exerciseId) {
        return workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                name, null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45,
                List.of(new WorkoutPlanExerciseInput(exerciseId, 1, 3, 5, null, null, null, 120, null, null))));
    }

    private WorkoutSessionView session(CurrentUserPrincipal user,
                                       String name,
                                       UUID exerciseId,
                                       BigDecimal weightKg,
                                       Instant startedAt) {
        return workoutService.createSession(user, new UpsertWorkoutSessionRequest(
                null,
                name,
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                startedAt,
                startedAt.plus(Duration.ofHours(1)),
                60,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        exerciseId, 1, null, null, null, null, null, null, null, null,
                        List.of(new WorkoutExerciseSetLogInput(1, 6, weightKg, null, null, 120, 8, null))))));
    }
}
