package com.fitsync.workout.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.response.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class WorkoutServicePersistenceTests {

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final CurrentUserPrincipal currentUser = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com",
            "Alice",
            "USER");

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void shouldCreateAndArchiveWorkoutPlan() {
        UUID squatId = createExercise("squat", "Back Squat");
        WorkoutPlanView created = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Leg Day",
                "Lower body workout",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                70,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 5, 5, null, null, null, 180, null, null))));

        workoutService.archivePlan(currentUser, created.planId());

        WorkoutPlanView archived = workoutService.getPlan(currentUser, created.planId());
        assertEquals("Leg Day", archived.name());
        assertEquals(WorkoutPlanStatus.ARCHIVED, archived.status());
        assertEquals(1, workoutPlanRepository.findByPlanIdAndUserId(created.planId(), currentUser.userId()).orElseThrow().getExercises().size());
    }

    @Test
    void shouldCreateCompletedSessionAndPersistOutboxEvent() {
        UUID rowId = createExercise("barbell-row", "Barbell Row");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Pull Day",
                null,
                GoalFocus.MUSCLE_GAIN,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                55,
                List.of()));

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                plan.planId(),
                "Evening Pull Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.PLAN,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:50:00Z"),
                50,
                380,
                7,
                "Felt strong",
                List.of(new WorkoutExerciseLogInput(
                        rowId,
                        1,
                        4,
                        8,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))));

        WorkoutSessionView latest = workoutService.getLatestSession(currentUser);

        assertEquals(session.sessionId(), latest.sessionId());
        assertEquals(1, latest.exerciseCount());
        assertEquals("WorkoutLogged", outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow()
                .getEventType());
    }

    @Test
    void shouldFilterAndPageExercises() {
        UUID squatId = createExercise("search-squat", "Search Squat");
        createExercise("search-row", "Search Row");

        PageResponse<ExerciseView> page = exerciseService.listExercises(
                currentUser,
                "squat",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.BEGINNER,
                0,
                10);

        assertEquals(1, page.totalElements());
        assertEquals(squatId, page.content().get(0).exerciseId());
    }

    @Test
    void shouldFilterAndPagePlansByStatus() {
        UUID squatId = createExercise("plan-page-squat", "Plan Page Squat");
        WorkoutPlanView active = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Active Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))));
        WorkoutPlanView archived = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Archived Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of()));
        workoutService.archivePlan(currentUser, archived.planId());

        PageResponse<WorkoutPlanView> page = workoutService.listPlans(currentUser, WorkoutPlanStatus.DRAFT, 0, 10);

        assertEquals(1, page.totalElements());
        assertEquals(active.planId(), page.content().get(0).planId());
    }

    @Test
    void shouldFilterAndPageSessions() {
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Morning Run",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                250,
                6,
                null,
                List.of()));
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Evening Run",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:30:00Z"),
                30,
                260,
                7,
                null,
                List.of()));

        PageResponse<WorkoutSessionView> page = workoutService.listSessions(
                currentUser,
                Instant.parse("2026-06-11T00:00:00Z"),
                Instant.parse("2026-06-11T23:59:59Z"),
                WorkoutSessionStatus.COMPLETED,
                null,
                0,
                1);

        assertEquals(2, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals("Evening Run", page.content().get(0).name());
    }

    @Test
    void shouldRejectInvalidSessionTimeline() {
        assertThrows(IllegalArgumentException.class, () -> workoutService.createSession(
                currentUser,
                new UpsertWorkoutSessionRequest(
                        null,
                        "Broken Session",
                        WorkoutType.CARDIO,
                        WorkoutSessionStatus.COMPLETED,
                        WorkoutSource.MANUAL,
                        Instant.parse("2026-06-11T20:00:00Z"),
                        Instant.parse("2026-06-11T19:59:00Z"),
                        10,
                        120,
                        4,
                        null,
                        List.of())));
    }

    @Test
    void shouldPersistPlanSetTargetsAndSessionSetLogs() {
        UUID squatId = createExercise("set-squat", "Set Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Detailed Leg Day",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                75,
                List.of(new WorkoutPlanExerciseInput(
                        squatId,
                        1,
                        2,
                        null,
                        null,
                        null,
                        null,
                        180,
                        null,
                        List.of(
                                new WorkoutPlanExerciseSetTargetInput(1, 5, new BigDecimal("100.00"), null, null, 180),
                                new WorkoutPlanExerciseSetTargetInput(2, 5, new BigDecimal("105.00"), null, null, 180))))));

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                plan.planId(),
                "Detailed Leg Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.PLAN,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T19:00:00Z"),
                60,
                450,
                8,
                null,
                List.of(new WorkoutExerciseLogInput(
                        squatId,
                        1,
                        2,
                        10,
                        new BigDecimal("1025.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 7, null),
                                new WorkoutExerciseSetLogInput(2, 5, new BigDecimal("105.00"), null, null, 180, 8, null))))));

        WorkoutPlanView fetchedPlan = workoutService.getPlan(currentUser, plan.planId());
        WorkoutSessionView fetchedSession = workoutService.getSession(currentUser, session.sessionId());

        assertEquals(2, fetchedPlan.exercises().get(0).setTargets().size());
        assertEquals(new BigDecimal("105.00"), fetchedPlan.exercises().get(0).setTargets().get(1).targetWeightKg());
        assertEquals(2, fetchedSession.exercises().get(0).sets().size());
        assertEquals(8, fetchedSession.exercises().get(0).sets().get(1).perceivedExertion());
    }

    @Test
    void shouldPublishWorkoutUpdatedWhenCompletedSessionChanges() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Morning Ride",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:45:00Z"),
                45,
                320,
                6,
                null,
                List.of()));

        outboxEventRepository.deleteAll();

        workoutService.updateSession(currentUser, session.sessionId(), new UpsertWorkoutSessionRequest(
                null,
                "Morning Ride Updated",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:50:00Z"),
                50,
                350,
                7,
                null,
                List.of()));

        assertEquals("WorkoutUpdated", outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow()
                .getEventType());
    }

    @Test
    void shouldPublishWorkoutDeletedWhenCompletedSessionIsRemoved() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Delete Me",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T07:00:00Z"),
                Instant.parse("2026-06-11T07:30:00Z"),
                30,
                240,
                5,
                null,
                List.of()));

        outboxEventRepository.deleteAll();

        workoutService.deleteSession(currentUser, session.sessionId());

        assertEquals("WorkoutDeleted", outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow()
                .getEventType());
    }

    private UUID createExercise(String code, String name) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.BACK);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return exerciseRepository.save(entity).getExerciseId();
    }
}
