package com.fitsync.workout.controller;

import java.math.BigDecimal;
import java.time.Duration;
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
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseCatalogUnmappedTermView;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseMetadataView;
import com.fitsync.workout.dto.ExerciseRecordValueView;
import com.fitsync.workout.dto.ExerciseRecordsView;
import com.fitsync.workout.dto.ImportWorkoutSessionRowRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionRowResult;
import com.fitsync.workout.dto.ImportWorkoutSessionRowStatus;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutExportView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.security.JwtService;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.service.ExerciseCatalogImportService;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.WorkoutService;
import com.fitsync.web.error.ForbiddenException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    private ExerciseCatalogImportService exerciseCatalogImportService;

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
    void shouldExportWorkoutDataForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        Instant exportedAt = Instant.parse("2026-06-19T12:00:00Z");
        ExerciseView exercise = new ExerciseView(
                UUID.randomUUID(),
                principal.userId(),
                "custom-squat",
                "Custom Squat",
                "Custom movement",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.INTERMEDIATE,
                ExerciseStatus.ACTIVE,
                true,
                exportedAt,
                exportedAt);
        WorkoutExportView response = new WorkoutExportView(
                1,
                exportedAt,
                List.of(exercise),
                List.of(),
                List.of(sessionView(UUID.randomUUID(), WorkoutSessionStatus.COMPLETED)));

        when(workoutService.exportWorkoutData(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/export")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value(1))
                .andExpect(jsonPath("$.exportedAt").value("2026-06-19T12:00:00Z"))
                .andExpect(jsonPath("$.exercises[0].code").value("custom-squat"))
                .andExpect(jsonPath("$.plans").isArray())
                .andExpect(jsonPath("$.sessions[0].status").value("COMPLETED"));

        verify(workoutService).exportWorkoutData(eq(principal));
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
    void shouldReturnExerciseMetadataForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        ExerciseMetadataView response = new ExerciseMetadataView(
                List.of(ExerciseType.values()),
                List.of(MuscleGroup.values()),
                List.of(ExerciseEquipment.values()),
                List.of(WorkoutDifficulty.values()),
                List.of(WorkoutType.values()),
                List.of(GoalFocus.values()));

        when(exerciseService.getExerciseMetadata()).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/exercises/metadata")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseTypes.length()").value(ExerciseType.values().length))
                .andExpect(jsonPath("$.exerciseTypes[0]").value(ExerciseType.STRENGTH.name()))
                .andExpect(jsonPath("$.muscleGroups.length()").value(MuscleGroup.values().length))
                .andExpect(jsonPath("$.equipment.length()").value(ExerciseEquipment.values().length))
                .andExpect(jsonPath("$.difficulties.length()").value(WorkoutDifficulty.values().length))
                .andExpect(jsonPath("$.workoutTypes.length()").value(WorkoutType.values().length))
                .andExpect(jsonPath("$.goalFocuses.length()").value(GoalFocus.values().length));

        verify(exerciseService).getExerciseMetadata();
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
    void shouldDuplicatePlanForAuthenticatedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID duplicatePlanId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutPlanView response = new WorkoutPlanView(
                duplicatePlanId,
                "Push Day (Copy)",
                "Upper body strength",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                WorkoutPlanStatus.DRAFT,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(workoutService.duplicatePlan(any(), eq(planId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/duplicate", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.planId").value(duplicatePlanId.toString()))
                .andExpect(jsonPath("$.name").value("Push Day (Copy)"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(workoutService).duplicatePlan(eq(principal), eq(planId));
    }

    @Test
    void shouldReturnNotFoundWhenDuplicatePlanIsNotAccessible() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        when(workoutService.duplicatePlan(any(), eq(planId)))
                .thenThrow(new ResourceNotFoundException("Workout plan not found"));

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/duplicate", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Workout plan not found"));
    }

    @Test
    void shouldActivatePlanForAuthenticatedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutPlanView response = new WorkoutPlanView(
                planId,
                "Push Day",
                "Upper body strength",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                WorkoutPlanStatus.ACTIVE,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(workoutService.activatePlan(any(), eq(planId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/activate", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(workoutService).activatePlan(eq(principal), eq(planId));
    }

    @Test
    void shouldGetActivePlanForAuthenticatedUser() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutPlanView response = new WorkoutPlanView(
                planId,
                "Active Push Day",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                WorkoutPlanStatus.ACTIVE,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));

        when(workoutService.getActivePlan(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/plans/active")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(workoutService).getActivePlan(eq(principal));
    }

    @Test
    void shouldReturnNotFoundWhenNoActivePlanExists() throws Exception {
        CurrentUserPrincipal principal = principal();

        when(workoutService.getActivePlan(any()))
                .thenThrow(new ResourceNotFoundException("Active workout plan not found"));

        mockMvc.perform(get("/api/v1/workouts/plans/active")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Active workout plan not found"));
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
    void shouldGetExerciseForAuthenticatedUser() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
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

        when(exerciseService.getExercise(any(), eq(exerciseId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}", exerciseId)
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.name").value("Trap Bar Deadlift"));

        verify(exerciseService).getExercise(eq(principal), eq(exerciseId));
    }

    @Test
    void shouldGetExerciseHistoryForAuthenticatedUser() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        ExerciseHistoryEntryView entry = new ExerciseHistoryEntryView(
                sessionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                exerciseId,
                "Barbell Bench Press",
                "Push Day",
                Instant.parse("2026-06-12T10:00:00Z"),
                Instant.parse("2026-06-12T11:00:00Z"),
                1,
                1,
                5,
                new BigDecimal("100.00"),
                null,
                null,
                180,
                8,
                null);
        PageResponse<ExerciseHistoryEntryView> response = PageResponse.of(List.of(entry), 0, 20, 1, 1, true, true);

        when(workoutService.getExerciseHistory(
                any(),
                eq(exerciseId),
                eq(Instant.parse("2026-06-01T00:00:00Z")),
                eq(Instant.parse("2026-06-30T00:00:00Z")),
                eq(0),
                eq(20))).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}/history", exerciseId)
                        .with(authentication(authToken(principal)))
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-30T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.content[0].exerciseNameSnapshot").value("Barbell Bench Press"))
                .andExpect(jsonPath("$.content[0].setNumber").value(1));

        verify(workoutService).getExerciseHistory(
                eq(principal),
                eq(exerciseId),
                eq(Instant.parse("2026-06-01T00:00:00Z")),
                eq(Instant.parse("2026-06-30T00:00:00Z")),
                eq(0),
                eq(20));
    }

    @Test
    void shouldReturnBadRequestWhenExerciseHistoryRangeExceedsCap() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        when(workoutService.getExerciseHistory(any(), eq(exerciseId), any(), any(), eq(0), eq(20)))
                .thenThrow(new IllegalArgumentException("Exercise history range must not exceed 370 days"));

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}/history", exerciseId)
                        .with(authentication(authToken(principal)))
                        .param("from", "2025-01-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Exercise history range must not exceed 370 days"));
    }

    @Test
    void shouldGetExerciseRecordsForAuthenticatedUser() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        ExerciseRecordsView response = new ExerciseRecordsView(
                exerciseId,
                "Barbell Bench Press",
                new ExerciseRecordValueView(new BigDecimal("125.00"), sessionId, Instant.parse("2026-06-12T11:00:00Z")),
                new ExerciseRecordValueView(new BigDecimal("8.00"), sessionId, Instant.parse("2026-06-12T11:00:00Z")),
                new ExerciseRecordValueView(new BigDecimal("1000.00"), sessionId, Instant.parse("2026-06-12T11:00:00Z")),
                new ExerciseRecordValueView(new BigDecimal("158.33"), sessionId, Instant.parse("2026-06-12T11:00:00Z")),
                new ExerciseRecordValueView(new BigDecimal("3000.00"), sessionId, Instant.parse("2026-06-12T11:00:00Z")));

        when(workoutService.getExerciseRecords(any(), eq(exerciseId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}/records", exerciseId)
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.maxWeightKg.value").value(125.00))
                .andExpect(jsonPath("$.estimatedOneRepMaxKg.value").value(158.33));

        verify(workoutService).getExerciseRecords(eq(principal), eq(exerciseId));
    }

    @Test
    void shouldUpdateCustomExerciseForAuthenticatedOwner() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        UpdateExerciseRequest request = new UpdateExerciseRequest(
                "Trap Bar Deadlift Updated",
                "Updated lower body hinge",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.ADVANCED);
        ExerciseView response = new ExerciseView(
                exerciseId,
                principal.userId(),
                "trap-bar-deadlift",
                "Trap Bar Deadlift Updated",
                "Updated lower body hinge",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.ADVANCED,
                ExerciseStatus.ACTIVE,
                true,
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:05:00Z"));

        when(exerciseService.updateCustomExercise(any(), eq(exerciseId), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/workouts/exercises/{exerciseId}", exerciseId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.code").value("trap-bar-deadlift"))
                .andExpect(jsonPath("$.name").value("Trap Bar Deadlift Updated"));

        verify(exerciseService).updateCustomExercise(eq(principal), eq(exerciseId), any());
    }

    @Test
    void shouldRejectGlobalExerciseUpdateWithForbidden() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        UpdateExerciseRequest request = new UpdateExerciseRequest(
                "Push-Up Updated",
                null,
                ExerciseType.STRENGTH,
                MuscleGroup.CHEST,
                ExerciseEquipment.BODYWEIGHT,
                WorkoutDifficulty.BEGINNER);

        when(exerciseService.updateCustomExercise(any(), eq(exerciseId), any()))
                .thenThrow(new ForbiddenException("Global exercises cannot be modified"));

        mockMvc.perform(put("/api/v1/workouts/exercises/{exerciseId}", exerciseId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Global exercises cannot be modified"));
    }

    @Test
    void shouldArchiveCustomExerciseForAuthenticatedOwner() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        mockMvc.perform(delete("/api/v1/workouts/exercises/{exerciseId}", exerciseId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(exerciseService).archiveCustomExercise(eq(principal), eq(exerciseId));
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
    void shouldReturnBadRequestWhenSessionStartsInFuture() throws Exception {
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Future Session",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.now().plus(Duration.ofDays(1)),
                null,
                10,
                100,
                5,
                null,
                List.of());

        when(workoutService.createSession(any(), any()))
                .thenThrow(new IllegalArgumentException("startedAt must not be in the future"));

        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("startedAt must not be in the future"));
    }

    @Test
    void shouldImportWorkoutSessionsForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        ImportWorkoutSessionsRequest request = new ImportWorkoutSessionsRequest(List.of(new ImportWorkoutSessionRowRequest(
                "device-session-1",
                "DEVICE",
                null,
                "Imported Strength",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                Instant.parse("2026-06-11T10:00:00Z"),
                Instant.parse("2026-06-11T11:00:00Z"),
                60,
                400,
                8,
                null,
                List.of())));
        ImportWorkoutSessionsResponse response = new ImportWorkoutSessionsResponse(
                1,
                0,
                0,
                List.of(new ImportWorkoutSessionRowResult(
                        0,
                        "device-session-1",
                        WorkoutSource.DEVICE,
                        ImportWorkoutSessionRowStatus.IMPORTED,
                        sessionId,
                        null)));

        when(workoutService.importSessions(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/import")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skippedDuplicate").value(0))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.results[0].status").value("IMPORTED"))
                .andExpect(jsonPath("$.results[0].sessionId").value(sessionId.toString()));

        verify(workoutService).importSessions(eq(principal), any());
    }

    @Test
    void shouldCompleteSessionForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        CompleteWorkoutSessionRequest request = new CompleteWorkoutSessionRequest(Instant.parse("2026-06-11T12:45:00Z"));
        WorkoutSessionView response = sessionView(sessionId, WorkoutSessionStatus.COMPLETED);

        when(workoutService.completeSession(any(), eq(sessionId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/complete", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        verify(workoutService).completeSession(eq(principal), eq(sessionId), any());
    }

    @Test
    void shouldCancelSessionForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionView response = sessionView(sessionId, WorkoutSessionStatus.CANCELLED);

        when(workoutService.cancelSession(any(), eq(sessionId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/cancel", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(workoutService).cancelSession(eq(principal), eq(sessionId));
    }

    @Test
    void shouldRepeatSessionForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID repeatedSessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionView response = sessionView(repeatedSessionId, WorkoutSessionStatus.IN_PROGRESS);

        when(workoutService.repeatSession(any(), eq(sessionId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/repeat", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(repeatedSessionId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(workoutService).repeatSession(eq(principal), eq(sessionId));
    }

    @Test
    void shouldRepeatLatestCompletedSessionForAuthenticatedUser() throws Exception {
        UUID repeatedSessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionView response = sessionView(repeatedSessionId, WorkoutSessionStatus.IN_PROGRESS);

        when(workoutService.repeatLatestSession(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/repeat-latest")
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(repeatedSessionId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        verify(workoutService).repeatLatestSession(eq(principal));
    }

    @Test
    void shouldAppendExerciseForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutExerciseLogInput request = new WorkoutExerciseLogInput(
                exerciseId,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseSetLogInput(1, 8, new BigDecimal("55.00"), null, null, 120, 7, null)));
        WorkoutSessionView response = sessionView(sessionId, WorkoutSessionStatus.IN_PROGRESS);

        when(workoutService.appendOrReplaceExercise(any(), eq(sessionId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/exercises", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        verify(workoutService).appendOrReplaceExercise(eq(principal), eq(sessionId), any());
    }

    @Test
    void shouldUpsertSetForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID exerciseLogId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutExerciseSetRequest request = new UpsertWorkoutExerciseSetRequest(
                8,
                new BigDecimal("55.00"),
                null,
                null,
                120,
                7,
                null);
        WorkoutSessionView response = sessionView(sessionId, WorkoutSessionStatus.IN_PROGRESS);

        when(workoutService.upsertSet(any(), eq(sessionId), eq(exerciseLogId), eq(1), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/exercises/{exerciseLogId}/sets/{setNumber}",
                        sessionId,
                        exerciseLogId,
                        1)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        verify(workoutService).upsertSet(eq(principal), eq(sessionId), eq(exerciseLogId), eq(1), any());
    }

    @Test
    void shouldReturnBadRequestWhenLiveEditingTerminalSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutExerciseLogInput request = new WorkoutExerciseLogInput(
                exerciseId,
                1,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of());

        when(workoutService.appendOrReplaceExercise(any(), eq(sessionId), any()))
                .thenThrow(new IllegalArgumentException("Only planned or in-progress workout sessions can be edited live"));

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/exercises", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only planned or in-progress workout sessions can be edited live"));
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

    @Test
    void shouldAllowAdminToImportExerciseCatalog() throws Exception {
        ExerciseCatalogImportResponse response = new ExerciseCatalogImportResponse(
                2,
                1,
                1,
                0,
                List.of(new ExerciseCatalogUnmappedTermView("equipment", "mystery sled", "OTHER", 1)));
        when(exerciseCatalogImportService.importFreeExerciseDbCatalog()).thenReturn(response);

        mockMvc.perform(post("/api/v1/internal/workouts/exercises/import-catalog")
                        .with(authentication(authToken(adminPrincipal())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(2))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.rejected").value(0))
                .andExpect(jsonPath("$.unmappedTerms[0].field").value("equipment"))
                .andExpect(jsonPath("$.unmappedTerms[0].term").value("mystery sled"))
                .andExpect(jsonPath("$.unmappedTerms[0].mappedTo").value("OTHER"))
                .andExpect(jsonPath("$.unmappedTerms[0].count").value(1));

        verify(exerciseCatalogImportService).importFreeExerciseDbCatalog();
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

    private WorkoutSessionView sessionView(UUID sessionId, WorkoutSessionStatus status) {
        return new WorkoutSessionView(
                sessionId,
                null,
                "Live Session",
                WorkoutType.STRENGTH,
                status,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T12:00:00Z"),
                status == WorkoutSessionStatus.COMPLETED ? Instant.parse("2026-06-11T12:45:00Z") : null,
                status == WorkoutSessionStatus.COMPLETED ? 45 : null,
                null,
                null,
                null,
                0,
                List.of(),
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:00:00Z"));
    }
}
