package com.fitsync.workout.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanEntryView;
import com.fitsync.workout.dto.SessionEntryView;
import com.fitsync.workout.dto.WorkoutPlanDayView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.messaging.OutboxEventRelay;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.security.JwtService;
import com.fitsync.workout.service.ExerciseCatalogImportService;
import com.fitsync.workout.service.ExerciseService;
import com.fitsync.workout.service.MonthlyTrainingInsightService;
import com.fitsync.workout.service.TrainingPageInsightService;
import com.fitsync.workout.service.WeeklyTrainingInsightService;
import com.fitsync.workout.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The plan entries API: coverage, proposal, the entry update route and the entries shapes. */
@WebMvcTest(controllers = {WorkoutController.class, InternalMessagingController.class})
@Import(com.fitsync.web.config.CommonWebErrorConfiguration.class)
@ActiveProfiles("test")
class PlanEntriesControllerTests {

    private static final CurrentUserPrincipal USER = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com", "Alice", "USER");

    @Autowired
    private MockMvc mockMvc;

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
    void coverageAnswersGroupsAndNotes() throws Exception {
        when(workoutService.previewCoverage(any(), any())).thenReturn(new PlanCoverageView(
                List.of(new PlanCoverageView.GroupSets(MuscleGroup.CHEST, new BigDecimal("6.0"))),
                List.of(new PlanCoverageView.Note("CALVES_NOT_TRAINED", "Calves are not trained yet."))));

        mockMvc.perform(post("/api/v1/workouts/plans/coverage")
                        .with(authentication(token())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":[{"dayOfWeek":1,"kind":"STRENGTH","title":"Full body A",
                                  "estimatedDurationMinutes":45,"exercises":[],
                                  "entries":[{"entryType":"SET","exerciseId":"%s","position":1,
                                    "targetSets":3,"targetReps":10,"targetDurationSeconds":null}]}],
                                 "weeklyWorkoutDays":4,"preferredDurationMinutes":45}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].group").value("CHEST"))
                .andExpect(jsonPath("$.groups[0].setsPerWeek").value(6.0))
                .andExpect(jsonPath("$.notes[0].code").value("CALVES_NOT_TRAINED"))
                .andExpect(jsonPath("$.notes[0].text").value("Calves are not trained yet."));
    }

    @Test
    void coverageRefusesAnInvalidDraft() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/plans/coverage")
                        .with(authentication(token())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"days":[{"dayOfWeek":9,"kind":"CARDIO","title":"Run","entries":[
                                  {"entryType":"SEGMENT","kind":"WORK","position":1,"plannedSeconds":30,"rounds":0}]}]}
                                """))
                .andExpect(status().isBadRequest());
        verify(workoutService, never()).previewCoverage(any(), any());
    }

    @Test
    void coverageNeedsAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/plans/coverage").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void proposalReadsTheProfileAndTheZone() throws Exception {
        when(workoutService.proposePlan(any(), any(), eq("Asia/Singapore"))).thenReturn(plan());

        mockMvc.perform(post("/api/v1/workouts/plans/proposal")
                        .with(authentication(token())).with(csrf())
                        .header("X-Timezone", "Asia/Singapore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fitnessLevel":"BEGINNER","weeklyWorkoutDays":4,"preferredDurationMinutes":45,
                                 "equipment":["DUMBBELLS","BANDS"],"focusAreas":["FULL_BODY","CARDIO"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].kind").value("CARDIO"))
                .andExpect(jsonPath("$.days[0].targetKind").value("MINUTES"))
                .andExpect(jsonPath("$.days[0].entries[0].entryType").value("SEGMENT"))
                .andExpect(jsonPath("$.days[0].entries[0].kind").value("STEADY"))
                .andExpect(jsonPath("$.days[0].entries[0].plannedSeconds").value(1800))
                .andExpect(jsonPath("$.days[0].entries[0].entryId").isEmpty());
    }

    @Test
    void proposalRefusesCodesThatAreNotWords() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/plans/proposal")
                        .with(authentication(token())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fitnessLevel\":\"<script>\",\"weeklyWorkoutDays\":12}"))
                .andExpect(status().isBadRequest());
        verify(workoutService, never()).proposePlan(any(), any(), any());
    }

    @Test
    void theEntryRouteTakesASegmentsTime() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID entryId = UUID.randomUUID();
        when(workoutService.updateEntry(any(), eq(sessionId), eq(entryId), any())).thenReturn(session(entryId));

        mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/entries/{entryId}", sessionId, entryId)
                        .with(authentication(token())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualSeconds\":1560,\"startedAt\":\"2026-10-04T07:05:00Z\",\"status\":\"DONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].entryId").value(entryId.toString()))
                .andExpect(jsonPath("$.entries[0].entryType").value("SEGMENT"))
                .andExpect(jsonPath("$.entries[0].kind").value("STEADY"))
                .andExpect(jsonPath("$.entries[0].label").value("Steady"))
                .andExpect(jsonPath("$.entries[0].plannedSeconds").value(1200))
                .andExpect(jsonPath("$.entries[0].actualSeconds").value(1560))
                .andExpect(jsonPath("$.entries[0].status").value("DONE"));
    }

    @Test
    void theEntryRouteRefusesATimeOutOfRange() throws Exception {
        mockMvc.perform(put("/api/v1/workouts/sessions/{sessionId}/entries/{entryId}", UUID.randomUUID(), UUID.randomUUID())
                        .with(authentication(token())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualSeconds\":-5,\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
        verify(workoutService, never()).updateEntry(any(), any(), any(), any());
    }

    @Test
    void aSessionStartTakesSegmentsAndAMinutesGoalAndTheZone() throws Exception {
        when(workoutService.createSession(any(), any(), eq("Asia/Singapore"))).thenReturn(session(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(token())).with(csrf())
                        .header("X-Timezone", "Asia/Singapore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Free timer","workoutType":"CARDIO","status":"IN_PROGRESS","source":"MANUAL",
                                 "startedAt":"2026-10-04T07:00:00Z","exercises":[],"goalMinutes":20,
                                 "entries":[{"entryType":"SEGMENT","kind":"STEADY","label":"Steady",
                                   "plannedSeconds":1200,"position":1}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries[0].entryType").value("SEGMENT"));
    }

    private static WorkoutPlanView plan() {
        PlanEntryView steady = new PlanEntryView(null, EntryType.SEGMENT, 1, null, null, SegmentKind.STEADY, "Steady",
                1800, null, null, null, null, null, null, null, null, null, null);
        WorkoutPlanDayView day = new WorkoutPlanDayView(null, 2, SessionKind.CARDIO, "Easy run", null, null, 30,
                List.of(), com.fitsync.workout.domain.DayTargetKind.MINUTES, new BigDecimal("30"), null, 3,
                List.of(steady));
        return new WorkoutPlanView(null, "Strength and cardio, 4 days", null, GoalFocus.GENERAL_FITNESS,
                WorkoutType.CUSTOM, WorkoutDifficulty.BEGINNER, 45, null, List.of(), null, null, List.of(day), 4,
                new PlanCoverageView(List.of(), List.of()));
    }

    private static WorkoutSessionView session(UUID entryId) {
        Instant start = Instant.parse("2026-10-04T07:00:00Z");
        SessionEntryView steady = new SessionEntryView(entryId, EntryType.SEGMENT, 1, null, null, SegmentKind.STEADY,
                "Steady", 1200, 1560, start, WorkoutSetStatus.DONE, null, List.of());
        return new WorkoutSessionView(UUID.randomUUID(), null, "Free timer", WorkoutType.CARDIO,
                WorkoutSessionStatus.IN_PROGRESS, WorkoutSource.MANUAL, start, null, null, null, null, null, 0,
                List.of(), start, start, SessionKind.CARDIO, null, null, null, null, null, null, false, false,
                List.of(), List.of(steady));
    }

    private static UsernamePasswordAuthenticationToken token() {
        return new UsernamePasswordAuthenticationToken(USER, null, List.of(() -> "ROLE_USER"));
    }
}
