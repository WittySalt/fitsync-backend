package com.fitsync.workout.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.security.JwtService;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.WorkoutService;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {WorkoutController.class, InternalMessagingController.class})
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class WorkoutControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkoutService workoutService;

    @MockBean
    private ExerciseService exerciseService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OutboxEventRelay outboxEventRelay;

    @Test
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreatePlanForAuthenticatedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "Push Day",
                "Upper body strength",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                List.of(new WorkoutPlanExerciseInput(exerciseId, 1, 4, 8, null, null, null, 120, null, null)));
        WorkoutPlanView response = new WorkoutPlanView(
                planId,
                "Push Day",
                "Upper body strength",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                WorkoutPlanStatus.DRAFT,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(workoutService.createPlan(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.name").value("Push Day"));
    }

    @Test
    void shouldStartPlanForAuthenticatedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionView response = new WorkoutSessionView(
                sessionId,
                planId,
                "Push Day",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.PLAN,
                Instant.parse("2026-06-11T12:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(workoutService.startPlan(any(), eq(planId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.source").value("PLAN"));

        verify(workoutService).startPlan(eq(principal), eq(planId));
    }

    @Test
    void shouldReturnBadRequestWhenStartingArchivedPlan() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        when(workoutService.startPlan(any(), eq(planId)))
                .thenThrow(new IllegalArgumentException("Archived workout plan cannot be used for a session"));

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Archived workout plan cannot be used for a session"));
    }

    @Test
    void shouldReturnNotFoundWhenStartingMissingPlan() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        when(workoutService.startPlan(any(), eq(planId)))
                .thenThrow(new ResourceNotFoundException("Workout plan not found"));

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workout plan not found"));
    }

    @Test
    void shouldCreateCustomExerciseForAuthenticatedUser() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        CreateExerciseRequest request = new CreateExerciseRequest(
                "trap-bar-deadlift",
                "Trap Bar Deadlift",
                "Lower body hinge",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.INTERMEDIATE);
        ExerciseView response = new ExerciseView(
                exerciseId,
                principal.userId(),
                "trap-bar-deadlift",
                "Trap Bar Deadlift",
                "Lower body hinge",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.INTERMEDIATE,
                ExerciseStatus.ACTIVE,
                true,
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(exerciseService.createCustomExercise(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/exercises")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.name").value("Trap Bar Deadlift"));
    }

    @Test
    void shouldRejectInvalidSessionPayload() throws Exception {
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                10,
                100,
                5,
                null,
                List.of());

        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldAllowAdminToPublishOutboxEvents() throws Exception {
        when(outboxEventRelay.publishPendingBatch(10)).thenReturn(1);

        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(authentication(authToken(adminPrincipal())))
                        .with(csrf())
                        .param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBatchSize").value(10))
                .andExpect(jsonPath("$.publishedCount").value(1));
    }

    private UsernamePasswordAuthenticationToken authToken(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_" + principal.role()));
    }

    private CurrentUserPrincipal principal() {
        return new CurrentUserPrincipal(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "alice@example.com",
                "Alice",
                "USER");
    }

    private CurrentUserPrincipal adminPrincipal() {
        return new CurrentUserPrincipal(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "admin@example.com",
                "Admin",
                "ADMIN");
    }
}
