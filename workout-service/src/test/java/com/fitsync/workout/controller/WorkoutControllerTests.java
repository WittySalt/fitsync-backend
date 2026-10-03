package com.fitsync.workout.controller;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.InsightPeriod;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.ActiveWorkoutSessionView;
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseCatalogUnmappedTermView;
import com.fitsync.workout.dto.ExerciseImageView;
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
import com.fitsync.workout.dto.LiftProgressView;
import com.fitsync.workout.dto.MostTrainedView;
import com.fitsync.workout.dto.MonthlyDayView;
import com.fitsync.workout.dto.MonthlyMuscleView;
import com.fitsync.workout.dto.MonthlyTotalsView;
import com.fitsync.workout.dto.MonthlyTrainingInsightView;
import com.fitsync.workout.dto.MonthlyWeekView;
import com.fitsync.workout.dto.TrainingPageInsightView;
import com.fitsync.workout.dto.TrainingRecordView;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import com.fitsync.workout.dto.DayInsightView;
import com.fitsync.workout.dto.MuscleGroupInsightView;
import com.fitsync.workout.dto.NextTrainingView;
import com.fitsync.workout.dto.PlannedDayView;
import com.fitsync.workout.dto.SportActivityView;
import com.fitsync.workout.dto.TodayInsightView;
import com.fitsync.workout.dto.WeeklyTrainingInsightView;
import com.fitsync.workout.dto.WeeklyVolumeView;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutExportView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutResumeSetView;
import com.fitsync.workout.dto.WorkoutSessionResumeView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.security.JwtService;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.service.ExerciseCatalogImportService;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.MonthlyTrainingInsightService;
import com.fitsync.workout.service.TrainingPageInsightService;
import com.fitsync.workout.service.WeeklyTrainingInsightService;
import com.fitsync.workout.service.WorkoutService;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ConflictException;
import com.fitsync.web.error.ForbiddenException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
    private WeeklyTrainingInsightService weeklyTrainingInsightService;

    @MockBean
    private TrainingPageInsightService trainingPageInsightService;

    @MockBean
    private MonthlyTrainingInsightService monthlyTrainingInsightService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OutboxEventRelay outboxEventRelay;

    @MockBean
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private ExerciseRepository exerciseRepository;

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
    void shouldRequireAuthenticationForWeeklyTrainingInsight() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/insights/weekly"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnWeeklyTrainingInsightForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        WeeklyTrainingInsightView response = new WeeklyTrainingInsightView(
                "Asia/Singapore",
                LocalDate.parse("2026-09-27"),
                LocalDate.parse("2026-09-21"),
                LocalDate.parse("2026-09-27"),
                4,
                170,
                3,
                120,
                List.of(new MuscleGroupInsightView(
                        MuscleGroup.CHEST,
                        2,
                        true,
                        false,
                        LocalDate.parse("2026-09-25"),
                        0,
                        List.of("Dumbbell Bench Press"))),
                new WeeklyVolumeView(
                        new BigDecimal("3846.00"),
                        new BigDecimal("1716.00"),
                        new BigDecimal("1716.00"),
                        2,
                        0));

        when(weeklyTrainingInsightService.getWeeklyTrainingInsight(
                        eq(principal), eq("Asia/Singapore"), eq(LocalDate.parse("2026-09-27"))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/insights/weekly")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Asia/Singapore")
                        .param("date", "2026-09-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.referenceDate").value("2026-09-27"))
                .andExpect(jsonPath("$.weekStart").value("2026-09-21"))
                .andExpect(jsonPath("$.weekEnd").value("2026-09-27"))
                .andExpect(jsonPath("$.completedSessions").value(4))
                .andExpect(jsonPath("$.totalMinutes").value(170))
                .andExpect(jsonPath("$.previousWeekCompletedSessions").value(3))
                .andExpect(jsonPath("$.previousWeekTotalMinutes").value(120))
                .andExpect(jsonPath("$.muscleGroups[0].muscleGroup").value("CHEST"))
                .andExpect(jsonPath("$.muscleGroups[0].lastTrainedOn").value("2026-09-25"))
                .andExpect(jsonPath("$.volume.thisWeekKg").value(3846.00))
                .andExpect(jsonPath("$.volume.thisWeekSessionsWithVolume").value(2))
                .andExpect(jsonPath("$.volume.previousWeekSameDaysKg").value(1716.00))
                .andExpect(jsonPath("$.previousWeekSameDaysCompletedSessions").value(3))
                .andExpect(jsonPath("$.previousWeekSameDaysTotalMinutes").value(120));

        verify(weeklyTrainingInsightService).getWeeklyTrainingInsight(
                eq(principal), eq("Asia/Singapore"), eq(LocalDate.parse("2026-09-27")));
    }

    @Test
    void shouldReturnDayByDayTodayAndNextTrainingOnWeeklyTrainingInsight() throws Exception {
        CurrentUserPrincipal principal = principal();
        UUID planDayId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        DayInsightView monday = new DayInsightView(
                LocalDate.parse("2026-09-28"), 1, DayKind.STRENGTH, "Strength", DaySource.DONE, 50,
                List.of(), new PlannedDayView(planDayId, SessionKind.STRENGTH, "Full body", null, 50, 4, true));
        DayInsightView tuesday = new DayInsightView(
                LocalDate.parse("2026-09-29"), 2, DayKind.REST, "Rest", DaySource.NONE, null, List.of(), null);
        TodayInsightView today = new TodayInsightView(
                LocalDate.parse("2026-09-29"), DayKind.REST, "Rest", DaySource.NONE, null, null, null, null, null,
                List.of());
        NextTrainingView nextTraining = new NextTrainingView(
                LocalDate.parse("2026-10-01"), DayKind.CARDIO, "Cardio", "Treadmill run", 35);

        WeeklyTrainingInsightView response = new WeeklyTrainingInsightView(
                "Asia/Singapore",
                LocalDate.parse("2026-09-29"),
                LocalDate.parse("2026-09-28"),
                LocalDate.parse("2026-10-04"),
                1,
                50,
                0,
                0,
                List.of(),
                new WeeklyVolumeView(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0),
                List.of(monday, tuesday),
                today,
                nextTraining,
                "18:30");

        when(weeklyTrainingInsightService.getWeeklyTrainingInsight(
                        eq(principal), eq("Asia/Singapore"), eq(LocalDate.parse("2026-09-29"))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/insights/weekly")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Asia/Singapore")
                        .param("date", "2026-09-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].kind").value("STRENGTH"))
                .andExpect(jsonPath("$.days[0].source").value("DONE"))
                .andExpect(jsonPath("$.days[0].planned.fulfilled").value(true))
                .andExpect(jsonPath("$.days[1].kind").value("REST"))
                .andExpect(jsonPath("$.today.kind").value("REST"))
                .andExpect(jsonPath("$.today.source").value("NONE"))
                .andExpect(jsonPath("$.nextTraining.date").value("2026-10-01"))
                .andExpect(jsonPath("$.nextTraining.kind").value("CARDIO"))
                .andExpect(jsonPath("$.nextTraining.title").value("Treadmill run"))
                .andExpect(jsonPath("$.usualStartTime").value("18:30"));
    }

    @Test
    void shouldReturnBadRequestForInvalidTimezone() throws Exception {
        CurrentUserPrincipal principal = principal();

        when(weeklyTrainingInsightService.getWeeklyTrainingInsight(any(), eq("Not/AZone"), any()))
                .thenThrow(new IllegalArgumentException("The timezone is not a recognised zone id."));

        mockMvc.perform(get("/api/v1/workouts/insights/weekly")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Not/AZone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The timezone is not a recognised zone id."));
    }

    @Test
    void shouldReturnBadRequestForUnparseableDate() throws Exception {
        CurrentUserPrincipal principal = principal();

        mockMvc.perform(get("/api/v1/workouts/insights/weekly")
                        .with(authentication(authToken(principal)))
                        .param("date", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationForMonthlyTrainingInsight() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/insights/monthly"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnMonthlyTrainingInsightForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        MonthlyTrainingInsightView response = new MonthlyTrainingInsightView(
                "Asia/Singapore",
                "2026-09",
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-09-30"),
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-08-03"),
                List.of(new MonthlyDayView(LocalDate.parse("2026-09-03"), SessionKind.CARDIO, "Cardio", 1, 35,
                        List.of(MuscleGroup.CARDIO), false)),
                List.of(new MonthlyWeekView(LocalDate.parse("2026-08-31"), LocalDate.parse("2026-09-06"),
                        4, 195, 2, 1, 0, 1, true)),
                new MonthlyTotalsView(16, 780, 3, 3),
                new MonthlyTotalsView(15, 720, 2, 2),
                List.of(new MonthlyMuscleView(MuscleGroup.CHEST, "Chest", 8)));

        when(monthlyTrainingInsightService.getMonthlyTrainingInsight(
                        eq(principal), eq("Asia/Singapore"), eq("2026-09")))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/insights/monthly")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Asia/Singapore")
                        .param("month", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.monthStart").value("2026-09-01"))
                .andExpect(jsonPath("$.monthEnd").value("2026-09-30"))
                .andExpect(jsonPath("$.referenceDate").value("2026-10-01"))
                .andExpect(jsonPath("$.firstSessionDate").value("2026-08-03"))
                .andExpect(jsonPath("$.days[0].date").value("2026-09-03"))
                .andExpect(jsonPath("$.days[0].kind").value("CARDIO"))
                .andExpect(jsonPath("$.days[0].label").value("Cardio"))
                .andExpect(jsonPath("$.days[0].sessions").value(1))
                .andExpect(jsonPath("$.days[0].minutes").value(35))
                .andExpect(jsonPath("$.days[0].muscles[0]").value("CARDIO"))
                .andExpect(jsonPath("$.days[0].record").value(false))
                .andExpect(jsonPath("$.weeks[0].weekStart").value("2026-08-31"))
                .andExpect(jsonPath("$.weeks[0].weekEnd").value("2026-09-06"))
                .andExpect(jsonPath("$.weeks[0].strengthSessions").value(2))
                .andExpect(jsonPath("$.weeks[0].cardioSessions").value(1))
                .andExpect(jsonPath("$.weeks[0].mobilitySessions").value(0))
                .andExpect(jsonPath("$.weeks[0].sportSessions").value(1))
                .andExpect(jsonPath("$.weeks[0].complete").value(true))
                .andExpect(jsonPath("$.totals.sessions").value(16))
                .andExpect(jsonPath("$.totals.minutes").value(780))
                .andExpect(jsonPath("$.totals.records").value(3))
                .andExpect(jsonPath("$.totals.longestStreakDays").value(3))
                .andExpect(jsonPath("$.previousTotals.sessions").value(15))
                .andExpect(jsonPath("$.muscles[0].group").value("CHEST"))
                .andExpect(jsonPath("$.muscles[0].label").value("Chest"))
                .andExpect(jsonPath("$.muscles[0].sessions").value(8));
    }

    @Test
    void shouldReturnBadRequestForFutureOrMalformedMonth() throws Exception {
        CurrentUserPrincipal principal = principal();

        when(monthlyTrainingInsightService.getMonthlyTrainingInsight(any(), any(), eq("2099-01")))
                .thenThrow(new IllegalArgumentException("The month cannot be after the current month."));
        when(monthlyTrainingInsightService.getMonthlyTrainingInsight(any(), any(), eq("2026-9")))
                .thenThrow(new IllegalArgumentException("The month must be given as YYYY-MM."));

        mockMvc.perform(get("/api/v1/workouts/insights/monthly")
                        .with(authentication(authToken(principal)))
                        .param("month", "2099-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The month cannot be after the current month."));
        mockMvc.perform(get("/api/v1/workouts/insights/monthly")
                        .with(authentication(authToken(principal)))
                        .param("month", "2026-9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The month must be given as YYYY-MM."));
        mockMvc.perform(get("/api/v1/workouts/insights/monthly")
                        .with(authentication(authToken(principal)))
                        .param("month", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestForInvalidTimezoneOnMonthlyInsight() throws Exception {
        CurrentUserPrincipal principal = principal();

        when(monthlyTrainingInsightService.getMonthlyTrainingInsight(any(), eq("Not/AZone"), any()))
                .thenThrow(new IllegalArgumentException("The timezone is not a recognised zone id."));

        mockMvc.perform(get("/api/v1/workouts/insights/monthly")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Not/AZone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The timezone is not a recognised zone id."));
    }

    @Test
    void shouldRequireAuthenticationForTrainingPageInsight() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/insights/training"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnTrainingPageInsightForAuthenticatedUser() throws Exception {
        CurrentUserPrincipal principal = principal();
        TrainingPageInsightView response = new TrainingPageInsightView(
                "Asia/Singapore",
                LocalDate.parse("2026-09-28"),
                InsightPeriod.PREVIOUS_WEEK,
                LocalDate.parse("2026-09-21"),
                LocalDate.parse("2026-09-27"),
                4,
                2,
                new BigDecimal("1923.00"),
                new BigDecimal("1716.00"),
                50,
                50,
                new TrainingRecordView("Dumbbell Bench Press", new BigDecimal("22.50"), LocalDate.parse("2026-09-21"), new BigDecimal("20.00")),
                new MostTrainedView(MuscleGroup.CORE, 4, List.of("Plank Hold")),
                List.of(new LiftProgressView("Dumbbell Bench Press", new BigDecimal("20.00"), LocalDate.parse("2026-09-16"),
                        new BigDecimal("22.50"), LocalDate.parse("2026-09-25"))),
                null, null, null);

        when(trainingPageInsightService.getTrainingPageInsight(
                        eq(principal), eq("Asia/Singapore"), eq(LocalDate.parse("2026-09-28"))))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/insights/training")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Asia/Singapore")
                        .param("date", "2026-09-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Asia/Singapore"))
                .andExpect(jsonPath("$.referenceDate").value("2026-09-28"))
                .andExpect(jsonPath("$.period").value("PREVIOUS_WEEK"))
                .andExpect(jsonPath("$.periodStart").value("2026-09-21"))
                .andExpect(jsonPath("$.periodEnd").value("2026-09-27"))
                .andExpect(jsonPath("$.sessions").value(4))
                .andExpect(jsonPath("$.liftingSessions").value(2))
                .andExpect(jsonPath("$.volumePerSessionKg").value(1923.00))
                .andExpect(jsonPath("$.earlierVolumePerSessionKg").value(1716.00))
                .andExpect(jsonPath("$.minutesPerLiftingSession").value(50))
                .andExpect(jsonPath("$.earlierMinutesPerLiftingSession").value(50))
                .andExpect(jsonPath("$.record.exerciseName").value("Dumbbell Bench Press"))
                .andExpect(jsonPath("$.record.weightKg").value(22.50))
                .andExpect(jsonPath("$.record.achievedOn").value("2026-09-21"))
                .andExpect(jsonPath("$.record.previousBestKg").value(20.00))
                .andExpect(jsonPath("$.mostTrained.muscleGroup").value("CORE"))
                .andExpect(jsonPath("$.mostTrained.sessions").value(4))
                .andExpect(jsonPath("$.mostTrained.exerciseNames[0]").value("Plank Hold"))
                .andExpect(jsonPath("$.lifts[0].exerciseName").value("Dumbbell Bench Press"))
                .andExpect(jsonPath("$.lifts[0].fromKg").value(20.00))
                .andExpect(jsonPath("$.lifts[0].toKg").value(22.50));

        verify(trainingPageInsightService).getTrainingPageInsight(
                eq(principal), eq("Asia/Singapore"), eq(LocalDate.parse("2026-09-28")));
    }

    @Test
    void shouldReturnBadRequestForInvalidTimezoneOnTrainingPageInsight() throws Exception {
        CurrentUserPrincipal principal = principal();

        when(trainingPageInsightService.getTrainingPageInsight(any(), eq("Not/AZone"), any()))
                .thenThrow(new IllegalArgumentException("The timezone is not a recognised zone id."));

        mockMvc.perform(get("/api/v1/workouts/insights/training")
                        .with(authentication(authToken(principal)))
                        .param("timezone", "Not/AZone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The timezone is not a recognised zone id."));
    }

    @Test
    void shouldReturnBadRequestForUnparseableDateOnTrainingPageInsight() throws Exception {
        CurrentUserPrincipal principal = principal();

        mockMvc.perform(get("/api/v1/workouts/insights/training")
                        .with(authentication(authToken(principal)))
                        .param("date", "yesterday"))
                .andExpect(status().isBadRequest());
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
    void shouldReturnBadRequestWhenAPlanHasEightDays() throws Exception {
        CurrentUserPrincipal principal = principal();
        List<com.fitsync.workout.dto.WorkoutPlanDayInput> eightDays = new java.util.ArrayList<>();
        for (int dayOfWeek = 1; dayOfWeek <= 7; dayOfWeek++) {
            eightDays.add(new com.fitsync.workout.dto.WorkoutPlanDayInput(
                    null, dayOfWeek, SessionKind.MOBILITY, "Day " + dayOfWeek, null, 30, List.of()));
        }
        eightDays.add(new com.fitsync.workout.dto.WorkoutPlanDayInput(
                null, 1, SessionKind.MOBILITY, "Extra Day", null, 30, List.of()));
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "Eight Day Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.CUSTOM,
                WorkoutDifficulty.BEGINNER,
                null,
                List.of(),
                eightDays);

        mockMvc.perform(post("/api/v1/workouts/plans")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnBadRequestWhenADayOfWeekIsZero() throws Exception {
        CurrentUserPrincipal principal = principal();
        CreateWorkoutPlanRequest request = new CreateWorkoutPlanRequest(
                "Zero Day Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.CUSTOM,
                WorkoutDifficulty.BEGINNER,
                null,
                List.of(),
                List.of(new com.fitsync.workout.dto.WorkoutPlanDayInput(
                        null, 0, SessionKind.MOBILITY, "Invalid Day", null, 30, List.of())));

        mockMvc.perform(post("/api/v1/workouts/plans")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
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
                List.of(GoalFocus.values()),
                Arrays.stream(SportActivity.values())
                        .map(activity -> new SportActivityView(activity, activity.label(), activity.effort(), activity.measure()))
                        .toList());

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
                .andExpect(jsonPath("$.goalFocuses.length()").value(GoalFocus.values().length))
                .andExpect(jsonPath("$.sportActivities.length()").value(15))
                .andExpect(jsonPath("$.sportActivities[0].code").value(SportActivity.BASKETBALL.name()))
                .andExpect(jsonPath("$.sportActivities[0].label").value("Basketball"));

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

        when(workoutService.startPlan(any(), eq(planId), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.planId").value(planId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.source").value("PLAN"));

        verify(workoutService).startPlan(eq(principal), eq(planId), eq(null), eq("UTC"));
    }

    @Test
    void shouldPassPlanDayIdAndTimezoneThroughToStartPlan() throws Exception {
        UUID planId = UUID.randomUUID();
        UUID planDayId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionView response = sessionView(sessionId, WorkoutSessionStatus.IN_PROGRESS);

        when(workoutService.startPlan(any(), eq(planId), eq(planDayId), eq("Asia/Singapore"))).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .param("planDayId", planDayId.toString())
                        .param("timezone", "Asia/Singapore"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        verify(workoutService).startPlan(eq(principal), eq(planId), eq(planDayId), eq("Asia/Singapore"));
    }

    @Test
    void shouldReturnBadRequestWhenStartPlanTimezoneIsInvalid() throws Exception {
        UUID planId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();

        when(workoutService.startPlan(any(), eq(planId), any(), eq("Not/AZone")))
                .thenThrow(new IllegalArgumentException("The timezone is not a recognised zone id."));

        mockMvc.perform(post("/api/v1/workouts/plans/{planId}/start", planId)
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .param("timezone", "Not/AZone"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The timezone is not a recognised zone id."));
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

        when(workoutService.startPlan(any(), eq(planId), any(), any()))
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

        when(workoutService.startPlan(any(), eq(planId), any(), any()))
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
    void shouldUploadExerciseVideoForAuthenticatedOwner() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.mp4",
                "video/mp4",
                "video-content".getBytes());
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
                Instant.parse("2026-06-11T12:05:00Z"),
                "https://media.test/exercises/" + exerciseId + "/videos/demo.mp4",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of());

        when(exerciseService.uploadExerciseVideo(any(), eq(exerciseId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/workouts/exercises/{exerciseId}/video", exerciseId)
                        .file(file)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.videoUrl").value(response.videoUrl()));

        verify(exerciseService).uploadExerciseVideo(eq(principal), eq(exerciseId), any());
    }

    @Test
    void shouldUploadExerciseImageForAuthenticatedOwner() throws Exception {
        UUID exerciseId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.webp",
                MediaType.valueOf("image/webp").toString(),
                "image-content".getBytes());
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
                Instant.parse("2026-06-11T12:05:00Z"),
                null,
                null,
                null,
                null,
                List.of(),
                List.of(new ExerciseImageView(0, "https://media.test/exercises/" + exerciseId + "/images/demo.webp")),
                List.of());

        when(exerciseService.uploadExerciseImage(any(), eq(exerciseId), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/workouts/exercises/{exerciseId}/image", exerciseId)
                        .file(file)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseId").value(exerciseId.toString()))
                .andExpect(jsonPath("$.images[0].position").value(0))
                .andExpect(jsonPath("$.images[0].url").value(response.images().get(0).url()));

        verify(exerciseService).uploadExerciseImage(eq(principal), eq(exerciseId), any());
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
    void shouldRejectSportsSessionWithoutSportActivity() throws Exception {
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Pickup Game",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
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
    void shouldCreateSportsSessionWithSportActivity() throws Exception {
        CurrentUserPrincipal principal = principal();
        UUID sessionId = UUID.randomUUID();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Pickup Game",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                SportActivity.BASKETBALL);
        WorkoutSessionView response = new WorkoutSessionView(
                sessionId,
                null,
                "Pickup Game",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                List.of(),
                Instant.parse("2026-06-11T10:00:00Z"),
                Instant.parse("2026-06-11T10:00:00Z"),
                SessionKind.SPORT,
                SportActivity.BASKETBALL,
                "Basketball",
                null);

        when(workoutService.createSession(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("SPORT"))
                .andExpect(jsonPath("$.sportActivity").value("BASKETBALL"))
                .andExpect(jsonPath("$.sportLabel").value("Basketball"));
    }

    @Test
    void shouldRejectSessionDistanceAboveOneThousandKm() throws Exception {
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Ultra Swim",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("1000.01"));

        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(authToken(principal)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void shouldRejectNegativeSessionDistance() throws Exception {
        CurrentUserPrincipal principal = principal();
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "Negative Distance Swim",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("-1.00"));

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
    void shouldAnswerBadRequestWithTheNothingLoggedCodeWhenNoSetWasLogged() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(workoutService.completeSession(any(), eq(sessionId), any())).thenThrow(
                new BadRequestException("NOTHING_LOGGED", "No set was logged in this workout."));

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/complete", sessionId)
                        .with(authentication(authToken(principal())))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTHING_LOGGED"))
                .andExpect(jsonPath("$.message").value("No set was logged in this workout."));
    }

    @Test
    void shouldCountSetTheEffortAndRecordTheRestOnlyForASignedInPerson() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/count", sessionId).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/effort", sessionId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"perceivedExertion\":5}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/exercises/{logId}/sets/1/rest", sessionId, logId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"restTakenSeconds\":40}"))
                .andExpect(status().isUnauthorized());
        verify(workoutService, never()).countSession(any(), any());
        verify(workoutService, never()).updateEffort(any(), any(), any());
        verify(workoutService, never()).recordRestTaken(any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldRefuseAnEffortOutsideOneToTenAndARestOutsideZeroToSixHours() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        for (String body : new String[] {"{\"perceivedExertion\":0}", "{\"perceivedExertion\":11}", "{}"}) {
            mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/effort", sessionId)
                            .with(authentication(authToken(principal())))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : new String[] {"{\"restTakenSeconds\":-1}", "{\"restTakenSeconds\":21601}"}) {
            mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/exercises/{logId}/sets/1/rest", sessionId, logId)
                            .with(authentication(authToken(principal())))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(workoutService, never()).updateEffort(any(), any(), any());
        verify(workoutService, never()).recordRestTaken(any(), any(), any(), anyInt(), any());
    }

    @Test
    void shouldCountACancelledSessionForTheSignedInPerson() throws Exception {
        CurrentUserPrincipal principal = principal();
        UUID sessionId = UUID.randomUUID();
        when(workoutService.countSession(eq(principal), eq(sessionId))).thenReturn(sessionView(sessionId, WorkoutSessionStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/workouts/sessions/{sessionId}/count", sessionId)
                        .with(authentication(authToken(principal)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.newRecords").isArray());
    }

    @Test
    void shouldPassTheSessionNameSearchToTheService() throws Exception {
        CurrentUserPrincipal principal = principal();
        when(workoutService.listSessions(any(), any(), any(), any(), any(), any(), any(), any(), eq("run"),
                eq(0), eq(20))).thenReturn(PageResponse.of(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .param("q", "run")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk());

        verify(workoutService).listSessions(eq(principal), any(), any(), any(), any(), any(), any(), any(),
                eq("run"), eq(0), eq(20));
    }

    @Test
    void shouldRejectASessionNameSearchLongerThanEightyCharacters() throws Exception {
        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .param("q", "x".repeat(81))
                        .with(authentication(authToken(principal()))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .param("q", "x".repeat(80))
                        .with(authentication(authToken(principal()))))
                .andExpect(status().isOk());
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
                WorkoutSetStatus.SKIPPED,
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
    void shouldResumeSessionForAuthenticatedUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID exerciseLogId = UUID.randomUUID();
        UUID exerciseId = UUID.randomUUID();
        UUID setLogId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionResumeView response = new WorkoutSessionResumeView(
                sessionId,
                WorkoutSessionStatus.IN_PROGRESS,
                1,
                1,
                2,
                4,
                new WorkoutResumeSetView(
                        exerciseLogId,
                        exerciseId,
                        "Back Squat",
                        2,
                        setLogId,
                        1,
                        WorkoutSetStatus.PENDING,
                        5,
                        new BigDecimal("100.00"),
                        null,
                        null,
                        180,
                        null,
                        null));

        when(workoutService.resumeSession(any(), eq(sessionId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/sessions/{sessionId}/resume", sessionId)
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.doneSets").value(1))
                .andExpect(jsonPath("$.skippedSets").value(1))
                .andExpect(jsonPath("$.pendingSets").value(2))
                .andExpect(jsonPath("$.totalSets").value(4))
                .andExpect(jsonPath("$.nextSet.exerciseLogId").value(exerciseLogId.toString()))
                .andExpect(jsonPath("$.nextSet.exerciseSequence").value(2))
                .andExpect(jsonPath("$.nextSet.setNumber").value(1))
                .andExpect(jsonPath("$.nextSet.status").value("PENDING"));

        verify(workoutService).resumeSession(eq(principal), eq(sessionId));
    }

    @Test
    void shouldReturnTerminalResumeWithoutNextSet() throws Exception {
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        WorkoutSessionResumeView response = new WorkoutSessionResumeView(
                sessionId,
                WorkoutSessionStatus.COMPLETED,
                3,
                1,
                0,
                4,
                null);

        when(workoutService.resumeSession(any(), eq(sessionId))).thenReturn(response);

        mockMvc.perform(get("/api/v1/workouts/sessions/{sessionId}/resume", sessionId)
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.nextSet").doesNotExist());
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

    @Test
    void shouldReachTheActiveSessionHandlerAndNotTheSessionByIdHandler() throws Exception {
        UUID sessionId = UUID.randomUUID();
        CurrentUserPrincipal principal = principal();
        when(workoutService.getActiveSession(any())).thenReturn(java.util.Optional.of(new ActiveWorkoutSessionView(
                sessionView(sessionId, WorkoutSessionStatus.IN_PROGRESS),
                new WorkoutSessionResumeView(sessionId, WorkoutSessionStatus.IN_PROGRESS, 1, 0, 2, 3, null))));

        mockMvc.perform(get("/api/v1/workouts/sessions/active")
                        .with(authentication(authToken(principal))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.session.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.session.planDayId").doesNotExist())
                .andExpect(jsonPath("$.session.autoClosedAt").doesNotExist())
                .andExpect(jsonPath("$.resume.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.resume.doneSets").value(1))
                .andExpect(jsonPath("$.resume.totalSets").value(3));

        verify(workoutService).getActiveSession(eq(principal));
        verify(workoutService, never()).getSession(any(), any());
    }

    @Test
    void shouldAnswerNoContentWhenThereIsNoActiveSession() throws Exception {
        when(workoutService.getActiveSession(any())).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/workouts/sessions/active")
                        .with(authentication(authToken(principal()))))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void shouldStillReachTheSessionByIdHandlerForAnId() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(workoutService.getSession(any(), eq(sessionId)))
                .thenReturn(sessionView(sessionId, WorkoutSessionStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/v1/workouts/sessions/" + sessionId)
                        .with(authentication(authToken(principal()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId.toString()));

        verify(workoutService, never()).getActiveSession(any());
    }

    @Test
    void shouldAnswerConflictWithTheActiveSessionNamedWhenOneIsInProgress() throws Exception {
        UUID activeId = UUID.randomUUID();
        when(workoutService.startPlan(any(), any(), any(), any())).thenThrow(new ConflictException(
                "ACTIVE_SESSION_EXISTS",
                "You have a workout in progress.",
                java.util.Map.of("sessionId", activeId.toString(), "name", "Leg day", "startedAt", "2026-06-11T12:00:00Z")));

        mockMvc.perform(post("/api/v1/workouts/plans/" + UUID.randomUUID() + "/start")
                        .with(authentication(authToken(principal())))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_EXISTS"))
                .andExpect(jsonPath("$.message").value("You have a workout in progress."))
                .andExpect(jsonPath("$.data.sessionId").value(activeId.toString()))
                .andExpect(jsonPath("$.data.name").value("Leg day"))
                .andExpect(jsonPath("$.data.startedAt").value("2026-06-11T12:00:00Z"));
    }

    @Test
    void shouldLeaveTheDataOutOfErrorsThatCarryNone() throws Exception {
        when(workoutService.getSession(any(), any())).thenThrow(new ResourceNotFoundException("Workout session not found"));

        mockMvc.perform(get("/api/v1/workouts/sessions/" + UUID.randomUUID())
                        .with(authentication(authToken(principal()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
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
