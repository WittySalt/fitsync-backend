package com.fitsync.workout.service;

import java.io.InputStream;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.media.MediaStorageService;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CompleteWorkoutSessionRequest;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseCatalogImportResponse;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseMetadataView;
import com.fitsync.workout.dto.ExerciseRecordsView;
import com.fitsync.workout.dto.ImportWorkoutSessionRowRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionRowStatus;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpdateExerciseRequest;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseLogView;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutExportView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseSetTargetInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionResumeView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ForbiddenException;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
class WorkoutServicePersistenceTests {

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExerciseService exerciseService;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private UpstreamHttpImageSource upstreamHttpImageSource;

    @Autowired
    private ExerciseCatalogImportService exerciseCatalogImportService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final CurrentUserPrincipal currentUser = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com",
            "Alice",
            "USER");
    private final CurrentUserPrincipal otherUser = new CurrentUserPrincipal(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            "bob@example.com",
            "Bob",
            "USER");
    private final CurrentUserPrincipal adminUser = new CurrentUserPrincipal(
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            "admin@example.com",
            "Admin",
            "ADMIN");

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
    void shouldStartWorkoutPlanAsInProgressSessionWithSeededSkeletonAndNoOutboxEvent() {
        UUID squatId = createExercise("start-squat", "Start Squat");
        UUID rowId = createExercise("start-row", "Start Row");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Start Strength",
                "Plan to start",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                65,
                List.of(
                        new WorkoutPlanExerciseInput(
                                squatId,
                                1,
                                2,
                                null,
                                null,
                                null,
                                null,
                                180,
                                "Keep depth consistent",
                                List.of(
                                        new WorkoutPlanExerciseSetTargetInput(1, 5, new BigDecimal("100.00"), null, null, 180),
                                        new WorkoutPlanExerciseSetTargetInput(2, 5, new BigDecimal("105.00"), null, null, 180))),
                        new WorkoutPlanExerciseInput(
                                rowId,
                                2,
                                1,
                                null,
                                null,
                                null,
                                null,
                                120,
                                null,
                                List.of(new WorkoutPlanExerciseSetTargetInput(1, 10, new BigDecimal("60.00"), null, null, 120))))));

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId());
        WorkoutSessionView fetched = workoutService.getSession(currentUser, started.sessionId());

        assertEquals(plan.planId(), fetched.planId());
        assertEquals("Start Strength", fetched.name());
        assertEquals(WorkoutType.STRENGTH, fetched.workoutType());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, fetched.status());
        assertEquals(WorkoutSource.PLAN, fetched.source());
        assertNull(fetched.completedAt());
        assertNull(fetched.durationMinutes());
        assertEquals(2, fetched.exerciseCount());
        assertEquals(3, fetched.exercises().stream().mapToInt(exercise -> exercise.sets().size()).sum());
        assertEquals(plan.exercises().stream().mapToInt(exercise -> exercise.setTargets().size()).sum(),
                fetched.exercises().stream().mapToInt(exercise -> exercise.sets().size()).sum());
        assertEquals("Start Squat", fetched.exercises().get(0).exerciseNameSnapshot());
        assertNull(fetched.exercises().get(0).performedSets());
        assertNull(fetched.exercises().get(0).performedReps());
        assertNull(fetched.exercises().get(0).totalVolumeKg());
        assertEquals(1, fetched.exercises().get(0).sets().get(0).setNumber());
        assertEquals(5, fetched.exercises().get(0).sets().get(0).reps());
        assertEquals(new BigDecimal("100.00"), fetched.exercises().get(0).sets().get(0).weightKg());
        assertNull(fetched.exercises().get(0).sets().get(0).perceivedExertion());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldRejectStartingArchivedWorkoutPlan() {
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Archived Start",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of()));
        workoutService.archivePlan(currentUser, plan.planId());

        assertThrows(IllegalArgumentException.class, () -> workoutService.startPlan(currentUser, plan.planId()));
    }

    @Test
    void shouldReturnNotFoundWhenStartingMissingOrNonOwnedWorkoutPlan() {
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Owned Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of()));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.startPlan(otherUser, plan.planId()));
        assertThrows(ResourceNotFoundException.class, () -> workoutService.startPlan(currentUser, UUID.randomUUID()));
    }

    @Test
    void shouldDuplicateWorkoutPlanWithIndependentExerciseAndSetTargetIds() {
        UUID squatId = createExercise("duplicate-squat", "Duplicate Squat");
        WorkoutPlanView source = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Duplicate Strength",
                "Plan to copy",
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                70,
                List.of(new WorkoutPlanExerciseInput(
                        squatId,
                        1,
                        2,
                        null,
                        null,
                        null,
                        null,
                        180,
                        "Original cue",
                        List.of(
                                new WorkoutPlanExerciseSetTargetInput(1, 5, new BigDecimal("100.00"), null, null, 180),
                                new WorkoutPlanExerciseSetTargetInput(2, 5, new BigDecimal("105.00"), null, null, 180))))));
        workoutService.activatePlan(currentUser, source.planId());

        WorkoutPlanView copy = workoutService.duplicatePlan(currentUser, source.planId());

        assertNotEquals(source.planId(), copy.planId());
        assertEquals("Duplicate Strength (Copy)", copy.name());
        assertEquals(WorkoutPlanStatus.DRAFT, copy.status());
        assertEquals(currentUser.userId(), workoutPlanRepository.findByPlanIdAndUserId(copy.planId(), currentUser.userId())
                .orElseThrow()
                .getUserId());
        assertEquals(1, copy.exercises().size());
        assertNotEquals(source.exercises().get(0).planExerciseId(), copy.exercises().get(0).planExerciseId());
        assertEquals(source.exercises().get(0).exerciseId(), copy.exercises().get(0).exerciseId());
        assertEquals(2, copy.exercises().get(0).setTargets().size());
        assertNotEquals(source.exercises().get(0).setTargets().get(0).setTargetId(),
                copy.exercises().get(0).setTargets().get(0).setTargetId());

        var copyEntity = workoutPlanRepository.findByPlanIdAndUserId(copy.planId(), currentUser.userId()).orElseThrow();
        copyEntity.setName("Duplicate Strength Edited");
        workoutPlanRepository.save(copyEntity);

        WorkoutPlanView fetchedSource = workoutService.getPlan(currentUser, source.planId());
        WorkoutPlanView fetchedCopy = workoutService.getPlan(currentUser, copy.planId());
        assertEquals("Duplicate Strength", fetchedSource.name());
        assertEquals(WorkoutPlanStatus.ACTIVE, fetchedSource.status());
        assertEquals(new BigDecimal("100.00"), fetchedSource.exercises().get(0).setTargets().get(0).targetWeightKg());
        assertEquals("Duplicate Strength Edited", fetchedCopy.name());
    }

    @Test
    void shouldReturnNotFoundWhenDuplicatingMissingOrNonOwnedWorkoutPlan() {
        WorkoutPlanView plan = createPlan("Owner Only Duplicate Plan", currentUser);

        assertThrows(ResourceNotFoundException.class, () -> workoutService.duplicatePlan(otherUser, plan.planId()));
        assertThrows(ResourceNotFoundException.class, () -> workoutService.duplicatePlan(currentUser, UUID.randomUUID()));
    }

    @Test
    void shouldActivatePlanAndDemoteExistingActivePlanForSameUserOnly() {
        WorkoutPlanView planA = createPlan("Activation Plan A", currentUser);
        WorkoutPlanView planB = createPlan("Activation Plan B", currentUser);
        WorkoutPlanView otherPlan = createPlan("Other User Active Plan", otherUser);

        workoutService.activatePlan(otherUser, otherPlan.planId());
        workoutService.activatePlan(currentUser, planA.planId());
        WorkoutPlanView activatedB = workoutService.activatePlan(currentUser, planB.planId());

        assertEquals(WorkoutPlanStatus.ACTIVE, activatedB.status());
        assertEquals(WorkoutPlanStatus.DRAFT, workoutService.getPlan(currentUser, planA.planId()).status());
        assertEquals(WorkoutPlanStatus.ACTIVE, workoutService.getPlan(currentUser, planB.planId()).status());
        assertEquals(WorkoutPlanStatus.ACTIVE, workoutService.getPlan(otherUser, otherPlan.planId()).status());
        assertEquals(1, workoutService.listPlans(currentUser, WorkoutPlanStatus.ACTIVE, 0, 10).totalElements());
        assertEquals(1, workoutService.listPlans(otherUser, WorkoutPlanStatus.ACTIVE, 0, 10).totalElements());
        assertEquals(planB.planId(), workoutService.getActivePlan(currentUser).planId());
    }

    @Test
    void shouldActivatePlanIdempotently() {
        WorkoutPlanView plan = createPlan("Idempotent Active Plan", currentUser);

        WorkoutPlanView firstActivation = workoutService.activatePlan(currentUser, plan.planId());
        WorkoutPlanView secondActivation = workoutService.activatePlan(currentUser, plan.planId());

        assertEquals(firstActivation.planId(), secondActivation.planId());
        assertEquals(WorkoutPlanStatus.ACTIVE, secondActivation.status());
        assertEquals(1, workoutService.listPlans(currentUser, WorkoutPlanStatus.ACTIVE, 0, 10).totalElements());
    }

    @Test
    void shouldReactivateArchivedWorkoutPlan() {
        WorkoutPlanView plan = createPlan("Archived Reactivation Plan", currentUser);
        workoutService.archivePlan(currentUser, plan.planId());

        WorkoutPlanView activated = workoutService.activatePlan(currentUser, plan.planId());

        assertEquals(WorkoutPlanStatus.ACTIVE, activated.status());
        assertEquals(plan.planId(), workoutService.getActivePlan(currentUser).planId());
    }

    @Test
    void shouldReturnNotFoundWhenNoActiveWorkoutPlanExists() {
        assertThrows(ResourceNotFoundException.class, () -> workoutService.getActivePlan(currentUser));
    }

    @Test
    void shouldReturnNotFoundWhenActivatingNonOwnedWorkoutPlan() {
        WorkoutPlanView plan = createPlan("Owner Only Active Plan", currentUser);

        assertThrows(ResourceNotFoundException.class, () -> workoutService.activatePlan(otherUser, plan.planId()));
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
    void shouldStoreStrengthSessionWithStrengthKind() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Strength Kind Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));

        assertEquals(SessionKind.STRENGTH, session.kind());
        assertNull(session.sportActivity());
        assertNull(session.sportLabel());
    }

    @Test
    void shouldStoreHiitSessionWithCardioKind() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "HIIT Kind Session",
                WorkoutType.HIIT,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));

        assertEquals(SessionKind.CARDIO, session.kind());
    }

    @Test
    void shouldStoreSportsSessionWithSportKindAndBadmintonActivity() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Badminton Night",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                SportActivity.BADMINTON));

        assertEquals(SessionKind.SPORT, session.kind());
        assertEquals(SportActivity.BADMINTON, session.sportActivity());
        assertEquals("Badminton", session.sportLabel());
    }

    @Test
    void shouldStoreCustomSessionOfTwoCardioAndOneStrengthExerciseWithCardioKind() {
        UUID cardioExerciseA = createExerciseOfType("custom-kind-cardio-a", "Custom Kind Cardio A", ExerciseType.CARDIO);
        UUID cardioExerciseB = createExerciseOfType("custom-kind-cardio-b", "Custom Kind Cardio B", ExerciseType.CARDIO);
        UUID strengthExercise = createExerciseOfType("custom-kind-strength", "Custom Kind Strength", ExerciseType.STRENGTH);

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Custom Mixed Session",
                WorkoutType.CUSTOM,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new WorkoutExerciseLogInput(cardioExerciseA, 1, null, null, null, null, null, null, null, null, List.of()),
                        new WorkoutExerciseLogInput(cardioExerciseB, 2, null, null, null, null, null, null, null, null, List.of()),
                        new WorkoutExerciseLogInput(strengthExercise, 3, null, null, null, null, null, null, null, null, List.of()))));

        assertEquals(SessionKind.CARDIO, session.kind());
    }

    @Test
    void shouldFollowTheExercisesAddedToAFreeSession() {
        UUID run = createExerciseOfType("free-kind-run", "Free Kind Run", ExerciseType.CARDIO);

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Free Session",
                WorkoutType.CUSTOM,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));
        assertEquals(SessionKind.SPORT, session.kind());

        WorkoutSessionView withRun = workoutService.appendOrReplaceExercise(currentUser, session.sessionId(),
                new WorkoutExerciseLogInput(run, 1, null, null, null, null, null, null, null, null, List.of()));

        assertEquals(SessionKind.CARDIO, withRun.kind());
    }

    @Test
    void shouldSetKindOnSessionStartedFromPlan() {
        UUID squatId = createExercise("kind-start-squat", "Kind Start Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Kind Start Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))));

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId());

        assertEquals(SessionKind.STRENGTH, started.kind());
    }

    @Test
    void shouldChangeKindWhenSessionWorkoutTypeIsUpdated() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Kind Update Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));
        assertEquals(SessionKind.STRENGTH, session.kind());

        WorkoutSessionView updated = workoutService.updateSession(currentUser, session.sessionId(), new UpsertWorkoutSessionRequest(
                null,
                "Kind Update Session",
                WorkoutType.YOGA,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));

        assertEquals(SessionKind.MOBILITY, updated.kind());
    }

    @Test
    void shouldReturnFifteenSportActivitiesWithBasketballFirst() {
        ExerciseMetadataView metadata = exerciseService.getExerciseMetadata();

        assertEquals(15, metadata.sportActivities().size());
        assertEquals(SportActivity.BASKETBALL, metadata.sportActivities().get(0).code());
        assertEquals("Basketball", metadata.sportActivities().get(0).label());
    }

    @Test
    void shouldCompleteSessionOnceAndPublishWorkoutLogged() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Complete Later",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                250,
                7,
                null,
                List.of()));

        WorkoutSessionView completed = workoutService.completeSession(
                currentUser,
                session.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.parse("2026-06-11T18:45:00Z")));

        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status());
        assertEquals(45, completed.durationMinutes());
        assertEquals(1, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
        assertEquals("WorkoutLogged", outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).get(0).getEventType());
        assertThrows(IllegalArgumentException.class, () -> workoutService.completeSession(
                currentUser,
                session.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.parse("2026-06-11T18:50:00Z"))));
        assertThrows(IllegalArgumentException.class, () -> workoutService.cancelSession(currentUser, session.sessionId()));
        assertEquals(1, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldDefaultCompletedAtWhenCompletingSessionWithoutRequestBody() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Complete Now",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.now().minus(Duration.ofMinutes(5)),
                null,
                null,
                null,
                null,
                null,
                List.of()));

        WorkoutSessionView completed = workoutService.completeSession(currentUser, session.sessionId(), null);

        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status());
        assertNotNull(completed.completedAt());
        assertNotNull(completed.durationMinutes());
        assertEquals(1, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldCancelSessionWithoutPublishingEventAndRejectTerminalTransitions() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Cancel Later",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T07:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));

        WorkoutSessionView cancelled = workoutService.cancelSession(currentUser, session.sessionId());

        assertEquals(WorkoutSessionStatus.CANCELLED, cancelled.status());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
        assertThrows(IllegalArgumentException.class, () -> workoutService.cancelSession(currentUser, session.sessionId()));
        assertThrows(IllegalArgumentException.class, () -> workoutService.completeSession(currentUser, session.sessionId(), null));
    }

    @Test
    void shouldRejectLiveEditsForCompletedAndCancelledSessions() {
        UUID rowId = createExercise("terminal-edit-row", "Terminal Edit Row");
        WorkoutSessionView completed = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Completed Terminal",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:30:00Z"),
                30,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(rowId, 1, null, null, null, null, null, null, null, null, List.of()))));
        WorkoutSessionView cancelled = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Cancelled Terminal",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T19:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of()));
        workoutService.cancelSession(currentUser, cancelled.sessionId());
        WorkoutExerciseLogInput newExercise = new WorkoutExerciseLogInput(
                rowId, 1, null, null, null, null, null, null, null, null, List.of());
        UUID completedExerciseLogId = completed.exercises().get(0).exerciseLogId();

        assertThrows(IllegalArgumentException.class, () -> workoutService.appendOrReplaceExercise(currentUser, completed.sessionId(), newExercise));
        assertThrows(IllegalArgumentException.class, () -> workoutService.upsertSet(
                currentUser,
                completed.sessionId(),
                completedExerciseLogId,
                1,
                new UpsertWorkoutExerciseSetRequest(null, 8, new BigDecimal("80.00"), null, null, 120, 7, null)));
        assertThrows(IllegalArgumentException.class, () -> workoutService.appendOrReplaceExercise(currentUser, cancelled.sessionId(), newExercise));
    }

    @Test
    void shouldUpsertSingleSetWithoutTouchingSiblingSets() {
        UUID squatId = createExercise("single-set-squat", "Single Set Squat");
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Single Set Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        squatId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 7, "Keep"),
                                new WorkoutExerciseSetLogInput(2, 5, new BigDecimal("105.00"), null, null, 180, 8, "Change"))))));
        UUID exerciseLogId = session.exercises().get(0).exerciseLogId();

        WorkoutSessionView updated = workoutService.upsertSet(
                currentUser,
                session.sessionId(),
                exerciseLogId,
                2,
                new UpsertWorkoutExerciseSetRequest(null, 6, new BigDecimal("110.00"), null, null, 150, 9, "Changed"));

        assertEquals(2, updated.exercises().get(0).sets().size());
        assertEquals(5, updated.exercises().get(0).sets().get(0).reps());
        assertEquals(new BigDecimal("100.00"), updated.exercises().get(0).sets().get(0).weightKg());
        assertEquals("Keep", updated.exercises().get(0).sets().get(0).notes());
        assertEquals(6, updated.exercises().get(0).sets().get(1).reps());
        assertEquals(new BigDecimal("110.00"), updated.exercises().get(0).sets().get(1).weightKg());
        assertEquals("Changed", updated.exercises().get(0).sets().get(1).notes());
        assertEquals(2, updated.exercises().get(0).performedSets());
        assertEquals(11, updated.exercises().get(0).performedReps());
        assertEquals(new BigDecimal("1160.00"), updated.exercises().get(0).totalVolumeKg());
    }

    @Test
    void shouldExcludeUntouchedSeededSetsOnCompleteAndIncludeLoggedSetAfterUpsert() {
        UUID squatId = createExercise("logged-seed-squat", "Logged Seed Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Logged Seed Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
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

        Instant untouchedStartedAt = Instant.parse("2026-06-11T10:00:00Z");
        WorkoutSessionView untouched = workoutService.startPlan(currentUser, plan.planId());
        moveSessionStartedAt(untouched.sessionId(), untouchedStartedAt);
        assertEquals(2, untouched.exercises().get(0).sets().size());
        assertEquals(false, untouched.exercises().get(0).sets().get(0).logged());
        assertThrows(BadRequestException.class, () -> workoutService.completeSession(
                currentUser,
                untouched.sessionId(),
                new CompleteWorkoutSessionRequest(untouchedStartedAt.plus(Duration.ofMinutes(45)))));
        workoutService.cancelSession(currentUser, untouched.sessionId());

        Instant touchedStartedAt = Instant.parse("2026-06-11T11:00:00Z");
        WorkoutSessionView touched = workoutService.startPlan(currentUser, plan.planId());
        moveSessionStartedAt(touched.sessionId(), touchedStartedAt);
        WorkoutSessionView afterSet = workoutService.upsertSet(
                currentUser,
                touched.sessionId(),
                touched.exercises().get(0).exerciseLogId(),
                1,
                new UpsertWorkoutExerciseSetRequest(null, 5, new BigDecimal("100.00"), null, null, 180, 8, "Actual set"));
        WorkoutSessionView completedTouched = workoutService.completeSession(
                currentUser,
                afterSet.sessionId(),
                new CompleteWorkoutSessionRequest(touchedStartedAt.plus(Duration.ofMinutes(45))));

        assertEquals(true, completedTouched.exercises().get(0).sets().get(0).logged());
        assertEquals(false, completedTouched.exercises().get(0).sets().get(1).logged());
        assertEquals(1, completedTouched.exercises().get(0).performedSets());
        assertEquals(5, completedTouched.exercises().get(0).performedReps());
        assertEquals(new BigDecimal("500.00"), completedTouched.exercises().get(0).totalVolumeKg());
    }

    @Test
    void shouldResumeNextPendingSetAndExcludeSkippedSetsFromAggregatesAndRecords() {
        UUID squatId = createExercise("resume-squat", "Resume Squat");
        UUID rowId = createExercise("resume-row", "Resume Row");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Resume Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(
                        new WorkoutPlanExerciseInput(
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
                                        new WorkoutPlanExerciseSetTargetInput(2, 5, new BigDecimal("999.00"), null, null, 180))),
                        new WorkoutPlanExerciseInput(
                                rowId,
                                2,
                                1,
                                null,
                                null,
                                null,
                                null,
                                120,
                                null,
                                List.of(new WorkoutPlanExerciseSetTargetInput(1, 8, new BigDecimal("60.00"), null, null, 120))))));

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId());
        WorkoutSessionResumeView initialResume = workoutService.resumeSession(currentUser, started.sessionId());

        assertEquals(0, initialResume.doneSets());
        assertEquals(0, initialResume.skippedSets());
        assertEquals(3, initialResume.pendingSets());
        assertEquals(3, initialResume.totalSets());
        assertEquals(started.exercises().get(0).exerciseLogId(), initialResume.nextSet().exerciseLogId());
        assertEquals(1, initialResume.nextSet().setNumber());
        assertEquals(WorkoutSetStatus.PENDING, started.exercises().get(0).sets().get(0).status());
        assertEquals(false, started.exercises().get(0).sets().get(0).logged());

        WorkoutSessionView afterDone = workoutService.upsertSet(
                currentUser,
                started.sessionId(),
                started.exercises().get(0).exerciseLogId(),
                1,
                new UpsertWorkoutExerciseSetRequest(null, 5, new BigDecimal("100.00"), null, null, 180, 8, "Actual set"));
        WorkoutSessionView afterSkipped = workoutService.upsertSet(
                currentUser,
                afterDone.sessionId(),
                afterDone.exercises().get(0).exerciseLogId(),
                2,
                new UpsertWorkoutExerciseSetRequest(
                        WorkoutSetStatus.SKIPPED,
                        5,
                        new BigDecimal("999.00"),
                        null,
                        null,
                        180,
                        null,
                        "Skipped planned top set"));

        WorkoutSessionResumeView midResume = workoutService.resumeSession(currentUser, afterSkipped.sessionId());

        assertEquals(1, midResume.doneSets());
        assertEquals(1, midResume.skippedSets());
        assertEquals(1, midResume.pendingSets());
        assertEquals(afterSkipped.exercises().get(1).exerciseLogId(), midResume.nextSet().exerciseLogId());
        assertEquals(1, midResume.nextSet().setNumber());
        assertEquals(WorkoutSetStatus.DONE, afterSkipped.exercises().get(0).sets().get(0).status());
        assertEquals(true, afterSkipped.exercises().get(0).sets().get(0).logged());
        assertEquals(WorkoutSetStatus.SKIPPED, afterSkipped.exercises().get(0).sets().get(1).status());
        assertEquals(false, afterSkipped.exercises().get(0).sets().get(1).logged());

        WorkoutSessionView completed = workoutService.completeSession(
                currentUser,
                afterSkipped.sessionId(),
                new CompleteWorkoutSessionRequest(afterSkipped.startedAt().plus(Duration.ofMinutes(45))));
        WorkoutSessionResumeView terminalResume = workoutService.resumeSession(currentUser, completed.sessionId());
        ExerciseRecordsView records = workoutService.getExerciseRecords(currentUser, squatId);

        assertEquals(WorkoutSessionStatus.COMPLETED, terminalResume.status());
        assertNull(terminalResume.nextSet());
        assertEquals(1, completed.exercises().get(0).performedSets());
        assertEquals(5, completed.exercises().get(0).performedReps());
        assertEquals(new BigDecimal("500.00"), completed.exercises().get(0).totalVolumeKg());
        assertEquals(new BigDecimal("100.00"), records.maxWeightKg().value());
    }

    @Test
    void shouldRejectPendingStatusOnLiveSetUpsert() {
        UUID squatId = createExercise("pending-status-squat", "Pending Status Squat");
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Pending Status",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        squatId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()))));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workoutService.upsertSet(
                currentUser,
                session.sessionId(),
                session.exercises().get(0).exerciseLogId(),
                1,
                new UpsertWorkoutExerciseSetRequest(WorkoutSetStatus.PENDING, null, null, null, null, null, null, null)));

        assertEquals("Set status must be DONE or SKIPPED", exception.getMessage());
    }

    @Test
    void shouldDeriveStrengthAggregatesAndPayloadDuration() throws Exception {
        UUID benchId = createExercise("derived-bench", "Derived Bench");

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Derived Strength Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:50:00Z"),
                5,
                300,
                8,
                null,
                List.of(new WorkoutExerciseLogInput(
                        benchId,
                        1,
                        99,
                        999,
                        new BigDecimal("9999.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new WorkoutExerciseSetLogInput(1, 8, new BigDecimal("50.00"), null, null, 120, 7, null),
                                new WorkoutExerciseSetLogInput(2, 7, new BigDecimal("55.00"), null, null, 120, 8, null))))));

        WorkoutSessionView fetched = workoutService.getSession(currentUser, session.sessionId());
        JsonNode payload = objectMapper.readTree(outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .get(0)
                .getPayload());

        assertEquals(50, fetched.durationMinutes());
        assertEquals(2, fetched.exercises().get(0).performedSets());
        assertEquals(15, fetched.exercises().get(0).performedReps());
        assertEquals(new BigDecimal("785.00"), fetched.exercises().get(0).totalVolumeKg());
        assertEquals(50, payload.path("payload").path("durationMinutes").asInt());
    }

    @Test
    void shouldKeepClientExerciseScalarsWhenNoSetRowsExist() {
        UUID bikeId = createExercise("derived-bike", "Derived Bike");

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Cardio Scalar Session",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                250,
                6,
                null,
                List.of(new WorkoutExerciseLogInput(
                        bikeId,
                        1,
                        1,
                        200,
                        new BigDecimal("123.45"),
                        new BigDecimal("12.50"),
                        1800,
                        130,
                        155,
                        null,
                        List.of()))));

        WorkoutExerciseLogView exercise = workoutService.getSession(currentUser, session.sessionId()).exercises().get(0);

        assertEquals(1, exercise.performedSets());
        assertEquals(200, exercise.performedReps());
        assertEquals(new BigDecimal("123.45"), exercise.totalVolumeKg());
        assertEquals(new BigDecimal("12.50"), exercise.distanceKm());
        assertEquals(1800, exercise.durationSeconds());
    }

    @Test
    void shouldStoreTheRequestedSessionDistanceAsIs() {
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Sport Session With Distance",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                null,
                null,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("2.40")));

        assertEquals(new BigDecimal("2.40"), session.distanceKm());
        WorkoutSessionView fetched = workoutService.getSession(currentUser, session.sessionId());
        assertEquals(new BigDecimal("2.40"), fetched.distanceKm());
    }

    @Test
    void shouldSumExerciseDistancesWhenTheRequestSuppliesNone() {
        UUID cyclingId = createExerciseOfType("distance-cycling", "Distance Cycling", ExerciseType.CARDIO);
        UUID runningId = createExerciseOfType("distance-running", "Distance Running", ExerciseType.CARDIO);

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Multi Distance Session",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                null,
                null,
                null,
                List.of(
                        new WorkoutExerciseLogInput(cyclingId, 1, null, null, null, new BigDecimal("3.00"), null, null, null, null, List.of()),
                        new WorkoutExerciseLogInput(runningId, 2, null, null, null, new BigDecimal("2.75"), null, null, null, null, List.of()))));

        assertEquals(new BigDecimal("5.75"), session.distanceKm());
    }

    @Test
    void shouldFollowTheExerciseDistancesAsTheyChange() {
        UUID runningId = createExerciseOfType("distance-follow-run", "Distance Follow Run", ExerciseType.CARDIO);

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Run In Progress",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(runningId, 1, null, null, null, new BigDecimal("2.00"), null, null, null, null, List.of()))));
        assertEquals(new BigDecimal("2.00"), session.distanceKm());

        UUID cyclingId = createExerciseOfType("distance-follow-ride", "Distance Follow Ride", ExerciseType.CARDIO);
        WorkoutSessionView longer = workoutService.appendOrReplaceExercise(currentUser, session.sessionId(),
                new WorkoutExerciseLogInput(cyclingId, 2, null, null, null, new BigDecimal("3.75"), null, null, null, null, List.of()));

        assertEquals(new BigDecimal("5.75"), longer.distanceKm());
    }

    @Test
    void shouldLeaveSessionDistanceNullWhenNoLogHasOne() {
        UUID exerciseId = createExercise("distance-none", "No Distance Exercise");

        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "No Distance Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(exerciseId, 1, null, null, null, null, null, null, null, null, List.of()))));

        assertNull(session.distanceKm());
    }

    @Test
    void shouldStartARepeatedSessionWithNoDistanceEvenWhenTheSourceHadOne() {
        WorkoutSessionView source = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Sport Session To Repeat",
                WorkoutType.SPORTS,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T06:00:00Z"),
                Instant.parse("2026-06-11T06:30:00Z"),
                30,
                null,
                null,
                null,
                List.of(),
                SportActivity.SWIMMING,
                new BigDecimal("2.40")));

        WorkoutSessionView repeated = workoutService.repeatSession(currentUser, source.sessionId());

        assertNull(repeated.distanceKm());
    }

    @Test
    void shouldRejectFutureSessionStartAndNegativeScalarsInService() {
        UUID rowId = createExercise("invalid-derived-row", "Invalid Derived Row");

        assertThrows(IllegalArgumentException.class, () -> workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Future Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.now().plus(Duration.ofDays(1)),
                null,
                30,
                100,
                5,
                null,
                List.of())));
        assertThrows(IllegalArgumentException.class, () -> workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Negative Duration",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:30:00Z"),
                -1,
                100,
                5,
                null,
                List.of(new WorkoutExerciseLogInput(rowId, 1, null, null, null, null, null, null, null, null, List.of())))));
        assertThrows(IllegalArgumentException.class, () -> workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Negative Calories",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T18:00:00Z"),
                Instant.parse("2026-06-11T18:30:00Z"),
                30,
                -1,
                5,
                null,
                List.of(new WorkoutExerciseLogInput(rowId, 1, null, null, null, null, null, null, null, null, List.of())))));
    }

    @Test
    void shouldReturnExerciseHistoryForOwnerOnlyNewestFirstAndRejectOversizedRange() {
        UUID benchId = createHistoryBenchFixture();

        PageResponse<ExerciseHistoryEntryView> history = workoutService.getExerciseHistory(
                currentUser,
                benchId,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"),
                0,
                10);

        assertEquals(2, history.totalElements());
        assertEquals("Newer Bench", history.content().get(0).sessionName());
        assertEquals(new BigDecimal("120.00"), history.content().get(0).weightKg());
        assertEquals("Older Bench", history.content().get(1).sessionName());
        assertThrows(IllegalArgumentException.class, () -> workoutService.getExerciseHistory(
                currentUser,
                benchId,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"),
                0,
                10));
    }

    @Test
    void shouldReturnFullExerciseHistoryWhenDateRangeIsOmitted() {
        UUID benchId = createHistoryBenchFixture();

        PageResponse<ExerciseHistoryEntryView> boundedHistory = workoutService.getExerciseHistory(
                currentUser,
                benchId,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"),
                0,
                20);
        PageResponse<ExerciseHistoryEntryView> history = assertDoesNotThrow(() -> workoutService.getExerciseHistory(
                currentUser,
                benchId,
                null,
                null,
                0,
                20));

        assertEquals(2, history.totalElements());
        assertEquals(2, history.content().size());
        assertTrue(history.totalElements() >= boundedHistory.totalElements());
        assertTrue(history.content().size() >= boundedHistory.content().size());
        assertEquals("Newer Bench", history.content().get(0).sessionName());
        assertEquals(new BigDecimal("120.00"), history.content().get(0).weightKg());
        assertEquals("Older Bench", history.content().get(1).sessionName());
        assertEquals(new BigDecimal("100.00"), history.content().get(1).weightKg());
    }

    @Test
    void shouldReturnExerciseRecordsFromCompletedLoggedSetsOnly() {
        UUID benchId = createExercise("record-bench", "Record Bench");
        WorkoutSessionView bestVolumeSession = workoutService.createSession(currentUser, completedStrengthSession(
                "Volume Bench",
                Instant.parse("2026-06-10T10:00:00Z"),
                benchId,
                List.of(
                        new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 8, null),
                        new WorkoutExerciseSetLogInput(2, 10, new BigDecimal("80.00"), null, null, 180, 8, null))));
        WorkoutSessionView topWeightSession = workoutService.createSession(currentUser, completedStrengthSession(
                "Heavy Bench",
                Instant.parse("2026-06-12T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 3, new BigDecimal("120.00"), null, null, 180, 9, null))));

        ExerciseRecordsView records = workoutService.getExerciseRecords(currentUser, benchId);

        assertEquals(new BigDecimal("120.00"), records.maxWeightKg().value());
        assertEquals(topWeightSession.sessionId(), records.maxWeightKg().sessionId());
        assertEquals(new BigDecimal("10.00"), records.maxReps().value());
        assertEquals(new BigDecimal("800.00"), records.maxSingleSetVolumeKg().value());
        assertEquals(new BigDecimal("132.00"), records.estimatedOneRepMaxKg().value());
        assertEquals(topWeightSession.sessionId(), records.estimatedOneRepMaxKg().sessionId());
        assertEquals(new BigDecimal("1300.00"), records.bestSessionVolumeKg().value());
        assertEquals(bestVolumeSession.sessionId(), records.bestSessionVolumeKg().sessionId());
    }

    @Test
    void shouldPublishPersonalRecordAchievedExactlyOnceForNewHighOnly() {
        UUID benchId = createExercise("pr-once-bench", "PR Once Bench");
        workoutService.createSession(currentUser, completedStrengthSession(
                "Prior Bench",
                Instant.parse("2026-06-10T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 8, null))));
        outboxEventRepository.deleteAll();
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Pending PR Bench",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-12T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        benchId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("110.00"), null, null, 180, 9, null))))));

        workoutService.completeSession(
                currentUser,
                session.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.parse("2026-06-12T11:00:00Z")));

        assertEquals(1, pendingEventCount("WorkoutLogged"));
        assertEquals(1, pendingEventCount("PersonalRecordAchieved"));
        assertTrue(pendingEventPayload("PersonalRecordAchieved").contains("MAX_WEIGHT_KG"));
        assertThrows(IllegalArgumentException.class, () -> workoutService.completeSession(
                currentUser,
                session.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.parse("2026-06-12T11:10:00Z"))));
        assertEquals(1, pendingEventCount("PersonalRecordAchieved"));

        outboxEventRepository.deleteAll();
        workoutService.updateSession(currentUser, session.sessionId(), new UpsertWorkoutSessionRequest(
                null,
                "Updated Completed PR Bench",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-12T10:00:00Z"),
                Instant.parse("2026-06-12T11:00:00Z"),
                60,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        benchId,
                        2,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new WorkoutExerciseSetLogInput(1, 1, new BigDecimal("200.00"), null, null, 180, 9, null))))));

        assertEquals(1, pendingEventCount("WorkoutUpdated"));
        assertEquals(0, pendingEventCount("PersonalRecordAchieved"));
    }

    @Test
    void shouldNotPublishPersonalRecordForTies() {
        UUID benchId = createExercise("pr-tie-bench", "PR Tie Bench");
        workoutService.createSession(currentUser, completedStrengthSession(
                "Prior Tie Bench",
                Instant.parse("2026-06-10T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 8, null))));
        outboxEventRepository.deleteAll();
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Equal Tie Bench",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-12T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        benchId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 8, null))))));

        workoutService.completeSession(
                currentUser,
                session.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.parse("2026-06-12T11:00:00Z")));

        assertEquals(1, pendingEventCount("WorkoutLogged"));
        assertEquals(0, pendingEventCount("PersonalRecordAchieved"));
    }

    @Test
    void shouldExcludeUntouchedSeededSetsFromRecordsAndPublishWhenSeedSetIsLogged() {
        UUID squatId = createExercise("pr-seed-squat", "PR Seed Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "PR Seed Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(
                        squatId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        180,
                        null,
                        List.of(new WorkoutPlanExerciseSetTargetInput(1, 5, new BigDecimal("100.00"), null, null, 180))))));

        WorkoutSessionView untouched = workoutService.startPlan(currentUser, plan.planId());
        outboxEventRepository.deleteAll();
        assertThrows(BadRequestException.class, () -> workoutService.completeSession(
                currentUser,
                untouched.sessionId(),
                null));
        workoutService.cancelSession(currentUser, untouched.sessionId());

        ExerciseRecordsView emptyRecords = workoutService.getExerciseRecords(currentUser, squatId);
        assertEquals(0, pendingEventCount("WorkoutLogged"));
        assertEquals(0, pendingEventCount("PersonalRecordAchieved"));
        assertNull(emptyRecords.maxWeightKg());
        assertNull(emptyRecords.maxReps());

        WorkoutSessionView touched = workoutService.startPlan(currentUser, plan.planId());
        UUID exerciseLogId = touched.exercises().get(0).exerciseLogId();
        workoutService.upsertSet(
                currentUser,
                touched.sessionId(),
                exerciseLogId,
                1,
                new UpsertWorkoutExerciseSetRequest(null, 5, new BigDecimal("120.00"), null, null, 180, 9, null));
        outboxEventRepository.deleteAll();
        workoutService.completeSession(
                currentUser,
                touched.sessionId(),
                null);

        ExerciseRecordsView records = workoutService.getExerciseRecords(currentUser, squatId);
        assertEquals(new BigDecimal("120.00"), records.maxWeightKg().value());
        assertEquals(1, pendingEventCount("PersonalRecordAchieved"));
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
    void shouldAllowDuplicateCustomExerciseCodesAcrossUsersOnly() {
        ExerciseView currentUserExercise = createCustomExercise(currentUser, "shared-code", "Shared Code Lift");
        ExerciseView otherUserExercise = createCustomExercise(otherUser, "shared-code", "Other Shared Code Lift");

        assertEquals("shared-code", currentUserExercise.code());
        assertEquals("shared-code", otherUserExercise.code());
        assertThrows(IllegalArgumentException.class, () -> createCustomExercise(currentUser, "shared-code", "Duplicate"));
    }

    @Test
    void shouldGetUpdateAndArchiveCustomExerciseForOwner() {
        ExerciseView created = createCustomExercise(currentUser, "owner-edit", "Owner Edit Lift");

        ExerciseView fetched = exerciseService.getExercise(currentUser, created.exerciseId());
        ExerciseView updated = exerciseService.updateCustomExercise(currentUser, created.exerciseId(), new UpdateExerciseRequest(
                "Owner Edit Lift Updated",
                "Updated description",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.DUMBBELL,
                WorkoutDifficulty.ADVANCED));
        exerciseService.archiveCustomExercise(currentUser, created.exerciseId());

        assertEquals(created.exerciseId(), fetched.exerciseId());
        assertEquals("owner-edit", updated.code());
        assertEquals("Owner Edit Lift Updated", updated.name());
        assertEquals(WorkoutDifficulty.ADVANCED, updated.difficulty());
        assertEquals(ExerciseStatus.ARCHIVED, exerciseRepository.findById(created.exerciseId()).orElseThrow().getStatus());
        assertThrows(ResourceNotFoundException.class, () -> exerciseService.getExercise(currentUser, created.exerciseId()));
        assertEquals(0, exerciseService.listExercises(
                currentUser,
                "Owner Edit Lift Updated",
                null,
                null,
                null,
                null,
                0,
                10).totalElements());
    }

    @Test
    void shouldPersistExerciseVideoUrlOnCreateAndUpdate() {
        ExerciseView created = exerciseService.createCustomExercise(currentUser, new CreateExerciseRequest(
                "video-url-lift",
                "Video URL Lift",
                "Custom exercise",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.BEGINNER,
                " https://media.test/exercises/video-url-lift/original.mp4 "));

        ExerciseView updated = exerciseService.updateCustomExercise(currentUser, created.exerciseId(), new UpdateExerciseRequest(
                "Video URL Lift Updated",
                "Updated exercise",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.INTERMEDIATE,
                "https://media.test/exercises/video-url-lift/updated.webm"));
        ExerciseView fetched = exerciseService.getExercise(currentUser, created.exerciseId());

        assertEquals("https://media.test/exercises/video-url-lift/original.mp4", created.videoUrl());
        assertEquals("https://media.test/exercises/video-url-lift/updated.webm", updated.videoUrl());
        assertEquals("https://media.test/exercises/video-url-lift/updated.webm", fetched.videoUrl());
    }

    @Test
    void shouldUploadExerciseVideoForOwnerAndPersistUrl() {
        ExerciseView created = createCustomExercise(currentUser, "video-upload-lift", "Video Upload Lift");
        byte[] content = "video-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "demo.mp4", "video/mp4", content);
        String uploadedUrl = "https://media.test/exercises/" + created.exerciseId() + "/videos/demo.mp4";
        when(mediaStorageService.put(
                startsWith("exercises/" + created.exerciseId() + "/videos/"),
                any(InputStream.class),
                eq((long) content.length),
                eq("video/mp4")))
                .thenReturn(uploadedUrl);

        ExerciseView updated = exerciseService.uploadExerciseVideo(currentUser, created.exerciseId(), file);

        assertEquals(uploadedUrl, updated.videoUrl());
        assertEquals(uploadedUrl, exerciseRepository.findById(created.exerciseId()).orElseThrow().getVideoUrl());
        verify(mediaStorageService).put(
                startsWith("exercises/" + created.exerciseId() + "/videos/"),
                any(InputStream.class),
                eq((long) content.length),
                eq("video/mp4"));
    }

    @Test
    void shouldUploadExerciseImageForOwnerAndPersistImageEntity() {
        ExerciseView created = createCustomExercise(currentUser, "image-upload-lift", "Image Upload Lift");
        byte[] content = "image-content".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "demo.webp", "image/webp", content);
        String uploadedUrl = "https://media.test/exercises/" + created.exerciseId() + "/images/demo.webp";
        when(mediaStorageService.put(
                startsWith("exercises/" + created.exerciseId() + "/images/"),
                any(InputStream.class),
                eq((long) content.length),
                eq("image/webp")))
                .thenReturn(uploadedUrl);

        ExerciseView updated = exerciseService.uploadExerciseImage(currentUser, created.exerciseId(), file);

        assertEquals(1, updated.images().size());
        assertEquals(0, updated.images().get(0).position());
        assertEquals(uploadedUrl, updated.images().get(0).url());
        ExerciseView fetched = exerciseService.getExercise(currentUser, created.exerciseId());
        assertEquals(uploadedUrl, fetched.images().get(0).url());
        verify(mediaStorageService).put(
                startsWith("exercises/" + created.exerciseId() + "/images/"),
                any(InputStream.class),
                eq((long) content.length),
                eq("image/webp"));
    }

    @Test
    void shouldAllowAdminToUploadMediaForCustomExerciseOwnedByAnotherUser() {
        ExerciseView created = createCustomExercise(currentUser, "admin-media-lift", "Admin Media Lift");
        byte[] content = "admin-video".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "admin.webm", "video/webm", content);
        String uploadedUrl = "https://media.test/exercises/" + created.exerciseId() + "/videos/admin.webm";
        when(mediaStorageService.put(
                startsWith("exercises/" + created.exerciseId() + "/videos/"),
                any(InputStream.class),
                eq((long) content.length),
                eq("video/webm")))
                .thenReturn(uploadedUrl);

        ExerciseView updated = exerciseService.uploadExerciseVideo(adminUser, created.exerciseId(), file);

        assertEquals(uploadedUrl, updated.videoUrl());
    }

    @Test
    void shouldRejectMediaUploadForNonOwnerAndCatalogExercise() {
        ExerciseView ownedExercise = createCustomExercise(currentUser, "media-owner-only", "Media Owner Only");
        UUID globalExerciseId = createExercise("catalog-media", "Catalog Media");
        MockMultipartFile video = new MockMultipartFile("file", "demo.mp4", "video/mp4", "video".getBytes());
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "demo.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes());

        assertThrows(ResourceNotFoundException.class,
                () -> exerciseService.uploadExerciseVideo(otherUser, ownedExercise.exerciseId(), video));
        assertThrows(ResourceNotFoundException.class,
                () -> exerciseService.uploadExerciseImage(otherUser, ownedExercise.exerciseId(), image));
        assertThrows(ForbiddenException.class,
                () -> exerciseService.uploadExerciseVideo(currentUser, globalExerciseId, video));
        assertThrows(ForbiddenException.class,
                () -> exerciseService.uploadExerciseImage(adminUser, globalExerciseId, image));
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void shouldRejectUnsupportedExerciseMediaTypes() {
        ExerciseView created = createCustomExercise(currentUser, "invalid-media-lift", "Invalid Media Lift");
        MockMultipartFile video = new MockMultipartFile("file", "demo.mov", "video/quicktime", "video".getBytes());
        MockMultipartFile image = new MockMultipartFile("file", "demo.gif", MediaType.IMAGE_GIF_VALUE, "image".getBytes());

        assertThrows(com.fitsync.web.error.BadRequestException.class,
                () -> exerciseService.uploadExerciseVideo(currentUser, created.exerciseId(), video));
        assertThrows(com.fitsync.web.error.BadRequestException.class,
                () -> exerciseService.uploadExerciseImage(currentUser, created.exerciseId(), image));
        verifyNoInteractions(mediaStorageService);
    }

    @Test
    void shouldHideNonOwnedCustomExerciseAndForbidGlobalExerciseMutation() {
        ExerciseView ownedExercise = createCustomExercise(currentUser, "owner-only", "Owner Only Lift");
        UUID globalExerciseId = createExercise("global-push", "Global Push");

        assertThrows(ResourceNotFoundException.class, () -> exerciseService.getExercise(otherUser, ownedExercise.exerciseId()));
        assertThrows(ResourceNotFoundException.class, () -> exerciseService.updateCustomExercise(otherUser, ownedExercise.exerciseId(), updateExercise("Other Edit")));
        assertThrows(ResourceNotFoundException.class, () -> exerciseService.archiveCustomExercise(otherUser, ownedExercise.exerciseId()));
        assertEquals(globalExerciseId, exerciseService.getExercise(currentUser, globalExerciseId).exerciseId());
        assertThrows(ForbiddenException.class, () -> exerciseService.updateCustomExercise(currentUser, globalExerciseId, updateExercise("Global Edit")));
        assertThrows(ForbiddenException.class, () -> exerciseService.archiveCustomExercise(currentUser, globalExerciseId));
    }

    @Test
    void archivedExerciseShouldNotBeSearchableOrAttachableButExistingSnapshotsRemain() {
        ExerciseView customExercise = createCustomExercise(currentUser, "snapshot-lift", "Snapshot Lift");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Snapshot Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(customExercise.exerciseId(), 1, 3, 5, null, null, null, 90, null, null))));
        WorkoutSessionView session = workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Snapshot Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        customExercise.exerciseId(),
                        1,
                        3,
                        15,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of()))));

        exerciseService.archiveCustomExercise(currentUser, customExercise.exerciseId());

        assertEquals("Snapshot Lift", workoutService.getPlan(currentUser, plan.planId()).exercises().get(0).exerciseNameSnapshot());
        assertEquals("Snapshot Lift", workoutService.getSession(currentUser, session.sessionId()).exercises().get(0).exerciseNameSnapshot());
        assertEquals(0, exerciseService.listExercises(currentUser, "snapshot-lift", null, null, null, null, 0, 10).totalElements());
        assertThrows(ResourceNotFoundException.class, () -> workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "New Archived Exercise Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(customExercise.exerciseId(), 1, 3, 5, null, null, null, 90, null, null)))));
        assertThrows(ResourceNotFoundException.class, () -> workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "New Archived Exercise Session",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T11:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        customExercise.exerciseId(),
                        1,
                        3,
                        15,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of())))));
    }

    @Test
    void shouldImportCatalogIdempotentlyWithDemoContentAndFallbackSummary() {
        when(mediaStorageService.put(eq("exercises/Fixture_Bench_Press/0.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/0.jpg");
        when(mediaStorageService.put(eq("exercises/Fixture_Bench_Press/1.jpg"), any(byte[].class), eq("image/jpeg")))
                .thenReturn("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/1.jpg");

        ExerciseCatalogImportResponse first = exerciseCatalogImportService.importFreeExerciseDbCatalog();

        assertEquals(2, first.processed());
        assertEquals(2, first.created());
        assertEquals(0, first.updated());
        assertEquals(0, first.rejected());
        assertFallback(first, "level", "alien", "INTERMEDIATE", 1);
        assertFallback(first, "category", "unknown category", "STRENGTH", 1);
        assertFallback(first, "equipment", "mystery sled", "OTHER", 1);
        assertFallback(first, "primaryMuscles", "neck", "FULL_BODY", 1);
        assertFallback(first, "secondaryMuscles", "unknown muscle", "FULL_BODY", 1);

        ExerciseEntity bench = exerciseRepository
                .findByExternalSourceAndExternalRef("free-exercise-db", "Fixture_Bench_Press")
                .orElseThrow();
        ExerciseView benchDetail = exerciseService.getExercise(currentUser, bench.getExerciseId());
        assertNull(benchDetail.ownerUserId());
        assertFalse(benchDetail.custom());
        assertEquals("fixture_bench_press", benchDetail.code());
        assertEquals("free-exercise-db", benchDetail.externalSource());
        assertEquals("Fixture_Bench_Press", benchDetail.externalRef());
        assertEquals(3, benchDetail.instructions().size());
        assertEquals(1, benchDetail.instructions().get(0).stepNumber());
        assertEquals("Lie on the bench with your eyes under the bar.", benchDetail.instructions().get(0).text());
        assertEquals(2, benchDetail.images().size());
        assertEquals(0, benchDetail.images().get(0).position());
        assertEquals("http://media.test/fitsync-media/exercises/Fixture_Bench_Press/0.jpg", benchDetail.images().get(0).url());
        assertEquals(List.of(MuscleGroup.ARMS, MuscleGroup.SHOULDERS), benchDetail.secondaryMuscleGroups());

        ExerciseEntity fallback = exerciseRepository
                .findByExternalSourceAndExternalRef("free-exercise-db", "Fixture_Fallback_Move")
                .orElseThrow();
        assertEquals(WorkoutDifficulty.INTERMEDIATE, fallback.getDifficulty());
        assertEquals(ExerciseType.STRENGTH, fallback.getExerciseType());
        assertEquals(ExerciseEquipment.OTHER, fallback.getEquipment());
        assertEquals(MuscleGroup.FULL_BODY, fallback.getPrimaryMuscleGroup());

        ExerciseCatalogImportResponse second = exerciseCatalogImportService.importFreeExerciseDbCatalog();
        ExerciseView benchAfterSecondImport = exerciseService.getExercise(currentUser, bench.getExerciseId());

        assertEquals(2, second.processed());
        assertEquals(0, second.created());
        assertEquals(2, second.updated());
        assertEquals(0, second.rejected());
        assertEquals(2, exerciseRepository.findAll().stream()
                .filter(exercise -> "free-exercise-db".equals(exercise.getExternalSource()))
                .count());
        assertEquals(3, benchAfterSecondImport.instructions().size());
        assertEquals(2, benchAfterSecondImport.images().size());
        assertEquals(2, benchAfterSecondImport.secondaryMuscleGroups().size());
        verify(mediaStorageService, times(2))
                .put(eq("exercises/Fixture_Bench_Press/0.jpg"), any(byte[].class), eq("image/jpeg"));
        verify(mediaStorageService, times(2))
                .put(eq("exercises/Fixture_Bench_Press/1.jpg"), any(byte[].class), eq("image/jpeg"));
        verifyNoInteractions(upstreamHttpImageSource);
    }

    @Test
    void shouldSerializeExerciseWithoutDemoContent() {
        ExerciseView created = createCustomExercise(currentUser, "no-media", "No Media Lift");

        ExerciseView detail = exerciseService.getExercise(currentUser, created.exerciseId());
        JsonNode json = objectMapper.valueToTree(detail);

        assertNull(detail.videoUrl());
        assertNull(detail.animationUrl());
        assertNull(detail.externalSource());
        assertNull(detail.externalRef());
        assertTrue(detail.instructions().isEmpty());
        assertTrue(detail.images().isEmpty());
        assertTrue(detail.secondaryMuscleGroups().isEmpty());
        assertTrue(json.get("instructions").isArray());
        assertEquals(0, json.get("instructions").size());
        assertTrue(json.get("images").isArray());
        assertEquals(0, json.get("images").size());
        assertTrue(json.get("secondaryMuscleGroups").isArray());
        assertEquals(0, json.get("secondaryMuscleGroups").size());
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
                null,
                null,
                null,
                null,
                0,
                1);

        assertEquals(2, page.totalElements());
        assertEquals(1, page.content().size());
        assertEquals("Evening Run", page.content().get(0).name());
    }

    @Test
    void shouldListSessionsWhenNullableFiltersAreAbsentOrPartiallyPresent() {
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Morning Strength",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-10T06:00:00Z"),
                Instant.parse("2026-06-10T06:45:00Z"),
                45,
                320,
                7,
                null,
                List.of()));
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Lunch Cardio",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T12:00:00Z"),
                Instant.parse("2026-06-11T12:25:00Z"),
                25,
                220,
                6,
                null,
                List.of()));
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Evening Mobility",
                WorkoutType.MOBILITY,
                WorkoutSessionStatus.PLANNED,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-12T19:00:00Z"),
                null,
                20,
                null,
                null,
                null,
                List.of()));

        PageResponse<WorkoutSessionView> allSessions = assertDoesNotThrow(() -> workoutService.listSessions(
                currentUser,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20));
        PageResponse<WorkoutSessionView> cardioSessions = assertDoesNotThrow(() -> workoutService.listSessions(
                currentUser,
                null,
                null,
                null,
                WorkoutType.CARDIO,
                null,
                null,
                null,
                null,
                0,
                20));
        PageResponse<WorkoutSessionView> longSessions = assertDoesNotThrow(() -> workoutService.listSessions(
                currentUser,
                null,
                null,
                null,
                null,
                40,
                null,
                null,
                null,
                0,
                20));
        PageResponse<WorkoutSessionView> shortSessions = assertDoesNotThrow(() -> workoutService.listSessions(
                currentUser,
                null,
                null,
                null,
                null,
                null,
                25,
                null,
                null,
                0,
                20));

        assertEquals(3, allSessions.totalElements());
        assertEquals(3, allSessions.content().size());
        assertEquals(List.of("Evening Mobility", "Lunch Cardio", "Morning Strength"),
                allSessions.content().stream().map(WorkoutSessionView::name).toList());
        assertEquals(1, cardioSessions.totalElements());
        assertEquals(List.of("Lunch Cardio"), cardioSessions.content().stream().map(WorkoutSessionView::name).toList());
        assertEquals(1, longSessions.totalElements());
        assertEquals(List.of("Morning Strength"), longSessions.content().stream().map(WorkoutSessionView::name).toList());
        assertEquals(2, shortSessions.totalElements());
        assertEquals(List.of("Evening Mobility", "Lunch Cardio"),
                shortSessions.content().stream().map(WorkoutSessionView::name).toList());
    }

    @Test
    void shouldListSessionsWhoseNameContainsTheTextIgnoringCase() {
        namedSession("Morning Run", WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        namedSession("evening RUN", WorkoutSessionStatus.COMPLETED, "2026-06-11T18:00:00Z");
        namedSession("Leg Day", WorkoutSessionStatus.COMPLETED, "2026-06-12T09:00:00Z");

        PageResponse<WorkoutSessionView> byUpper = searchSessions("RUN", null, 0, 20);
        PageResponse<WorkoutSessionView> byLower = searchSessions("orning r", null, 0, 20);

        assertEquals(List.of("evening RUN", "Morning Run"), sessionNames(byUpper));
        assertEquals(List.of("Morning Run"), sessionNames(byLower));
    }

    @Test
    void shouldMatchWildcardCharactersTypedInTheSearchLiterally() {
        namedSession("100% Effort", WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        namedSession("Leg_Day", WorkoutSessionStatus.COMPLETED, "2026-06-11T06:00:00Z");
        namedSession("Legs Day", WorkoutSessionStatus.COMPLETED, "2026-06-12T06:00:00Z");
        namedSession("Back\\Slash", WorkoutSessionStatus.COMPLETED, "2026-06-13T06:00:00Z");

        assertEquals(List.of("100% Effort"), sessionNames(searchSessions("%", null, 0, 20)));
        assertEquals(List.of("Leg_Day"), sessionNames(searchSessions("g_d", null, 0, 20)));
        assertEquals(List.of("Back\\Slash"), sessionNames(searchSessions("\\", null, 0, 20)));
        assertEquals(0, searchSessions("%%", null, 0, 20).totalElements());
    }

    @Test
    void shouldCombineTheNameSearchWithTheStatusFilter() {
        namedSession("Easy Run", WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        namedSession("Planned Run", WorkoutSessionStatus.PLANNED, "2026-06-11T06:00:00Z");
        namedSession("Easy Swim", WorkoutSessionStatus.COMPLETED, "2026-06-12T06:00:00Z");

        PageResponse<WorkoutSessionView> page = searchSessions("run", WorkoutSessionStatus.COMPLETED, 0, 20);

        assertEquals(List.of("Easy Run"), sessionNames(page));
        assertEquals(1, page.totalElements());
    }

    @Test
    void shouldPageAndCountOnlyTheSessionsThatMatchTheNameSearch() {
        namedSession("Run one", WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        namedSession("Run two", WorkoutSessionStatus.COMPLETED, "2026-06-11T06:00:00Z");
        namedSession("Run three", WorkoutSessionStatus.COMPLETED, "2026-06-12T06:00:00Z");
        namedSession("Swim", WorkoutSessionStatus.COMPLETED, "2026-06-13T06:00:00Z");

        PageResponse<WorkoutSessionView> first = searchSessions("run", null, 0, 2);
        PageResponse<WorkoutSessionView> second = searchSessions("run", null, 1, 2);

        assertEquals(3, first.totalElements());
        assertEquals(List.of("Run three", "Run two"), sessionNames(first));
        assertEquals(3, second.totalElements());
        assertEquals(List.of("Run one"), sessionNames(second));
    }

    @Test
    void shouldNotFilterWhenTheNameSearchIsBlankOrAbsent() {
        namedSession("Morning Run", WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        namedSession("Leg Day", WorkoutSessionStatus.COMPLETED, "2026-06-11T06:00:00Z");

        assertEquals(2, searchSessions(null, null, 0, 20).totalElements());
        assertEquals(2, searchSessions("", null, 0, 20).totalElements());
        assertEquals(2, searchSessions("   ", null, 0, 20).totalElements());
    }

    @Test
    void shouldKeepSessionsThatStartAtTheSameInstantInASteadyOrder() {
        for (int index = 0; index < 4; index++) {
            namedSession("Same start " + index, WorkoutSessionStatus.COMPLETED, "2026-06-10T06:00:00Z");
        }

        List<UUID> expected = searchSessions(null, null, 0, 20).content().stream()
                .map(WorkoutSessionView::sessionId)
                .toList();
        List<UUID> pagedTogether = new java.util.ArrayList<>();
        for (int page = 0; page < 4; page++) {
            searchSessions(null, null, page, 1).content().forEach(view -> pagedTogether.add(view.sessionId()));
        }

        assertEquals(4, expected.stream().distinct().count());
        assertEquals(expected, pagedTogether);
    }

    private void namedSession(String name, WorkoutSessionStatus status, String startedAt) {
        Instant start = Instant.parse(startedAt);
        boolean completed = status == WorkoutSessionStatus.COMPLETED;
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                name,
                WorkoutType.CARDIO,
                status,
                WorkoutSource.MANUAL,
                start,
                completed ? start.plus(Duration.ofMinutes(30)) : null,
                30,
                completed ? 200 : null,
                completed ? 6 : null,
                null,
                List.of()));
    }

    private PageResponse<WorkoutSessionView> searchSessions(String q, WorkoutSessionStatus status, int page, int size) {
        return workoutService.listSessions(currentUser, null, null, status, null, null, null, null, q, page, size);
    }

    private List<String> sessionNames(PageResponse<WorkoutSessionView> page) {
        return page.content().stream().map(WorkoutSessionView::name).toList();
    }

    @Test
    void shouldRejectInvalidSessionDurationFilterRange() {
        assertThrows(IllegalArgumentException.class, () -> workoutService.listSessions(
                currentUser,
                null,
                null,
                null,
                null,
                45,
                30,
                null,
                null,
                0,
                20));
    }

    @Test
    void shouldListSessionsWithBoundedQueryCountForExerciseDetails() {
        UUID squatId = createExercise("history-query-squat", "History Query Squat");
        for (int index = 0; index < 6; index++) {
            workoutService.createSession(currentUser, completedStrengthSession(
                    "Bounded Query Session " + index,
                    Instant.parse("2026-06-11T10:00:00Z").plus(Duration.ofHours(index)),
                    squatId,
                    List.of(new WorkoutExerciseSetLogInput(
                            1,
                            5 + index,
                            new BigDecimal("100.00"),
                            null,
                            null,
                            120,
                            7,
                            null))));
        }
        Statistics statistics = hibernateStatistics();
        statistics.clear();

        PageResponse<WorkoutSessionView> page = workoutService.listSessions(
                currentUser,
                null,
                null,
                WorkoutSessionStatus.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                0,
                6);

        assertEquals(6, page.content().size());
        assertEquals("Bounded Query Session 5", page.content().get(0).name());
        assertTrue(page.content().stream().allMatch(session -> session.exercises().size() == 1));
        assertTrue(page.content().stream().allMatch(session -> session.exercises().get(0).sets().size() == 1));
        assertTrue(
                statistics.getPrepareStatementCount() <= 5,
                "Expected bounded session history queries, but got " + statistics.getPrepareStatementCount());
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
    void shouldImportCompletedWorkoutSessionWithLoggedSetsAndWorkoutLoggedOnly() {
        UUID squatId = createExercise("import-squat", "Import Squat");
        ImportWorkoutSessionsResponse response = workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(
                List.of(importRow(
                        "device-session-1",
                        "DEVICE",
                        "Device Strength",
                        Instant.parse("2026-06-11T10:00:00Z"),
                        squatId,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 120, 8, null))))));

        assertEquals(1, response.imported());
        assertEquals(0, response.skippedDuplicate());
        assertEquals(0, response.rejected());
        assertEquals(ImportWorkoutSessionRowStatus.IMPORTED, response.results().get(0).status());

        WorkoutSessionView imported = workoutService.getSession(currentUser, response.results().get(0).sessionId());
        assertEquals(WorkoutSource.DEVICE, imported.source());
        assertEquals(WorkoutSessionStatus.COMPLETED, imported.status());
        assertEquals(60, imported.durationMinutes());
        assertEquals(1, imported.exercises().get(0).performedSets());
        assertEquals(5, imported.exercises().get(0).performedReps());
        assertEquals(new BigDecimal("500.00"), imported.exercises().get(0).totalVolumeKg());
        assertEquals(true, imported.exercises().get(0).sets().get(0).logged());
        assertEquals("device-session-1", workoutSessionRepository.findBySessionIdAndUserId(
                imported.sessionId(),
                currentUser.userId()).orElseThrow().getExternalId());
        assertEquals(1, pendingEventCount("WorkoutLogged"));
        assertEquals(0, pendingEventCount("PersonalRecordAchieved"));
    }

    @Test
    void shouldSkipDuplicateImportedWorkoutSessionWithoutReimportingOrRepublishing() {
        UUID squatId = createExercise("import-duplicate-squat", "Import Duplicate Squat");
        ImportWorkoutSessionRowRequest first = importRow(
                "device-session-duplicate",
                "DEVICE",
                "Device Strength",
                Instant.parse("2026-06-11T10:00:00Z"),
                squatId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 120, 8, null)));
        workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(List.of(first)));
        outboxEventRepository.deleteAll();

        ImportWorkoutSessionsResponse duplicate = workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(
                List.of(importRow(
                        "device-session-duplicate",
                        "DEVICE",
                        "Changed Device Strength",
                        Instant.parse("2026-06-12T10:00:00Z"),
                        squatId,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("150.00"), null, null, 120, 9, null)),
                        Instant.parse("2026-06-12T09:59:00Z")))));

        assertEquals(0, duplicate.imported());
        assertEquals(1, duplicate.skippedDuplicate());
        assertEquals(0, duplicate.rejected());
        assertEquals(ImportWorkoutSessionRowStatus.SKIPPED_DUPLICATE, duplicate.results().get(0).status());
        assertEquals(1, workoutSessionRepository.findAll().size());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldRejectBadImportRowsWithoutRollingBackValidRows() {
        UUID squatId = createExercise("import-partial-squat", "Import Partial Squat");

        ImportWorkoutSessionsResponse response = workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(List.of(
                importRow(
                        "bad-device-session",
                        "DEVICE",
                        "Bad Device Strength",
                        Instant.parse("2026-06-11T10:00:00Z"),
                        squatId,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 120, 8, null)),
                        Instant.parse("2026-06-11T09:59:00Z")),
                importRow(
                        "good-device-session",
                        "DEVICE",
                        "Good Device Strength",
                        Instant.parse("2026-06-12T10:00:00Z"),
                        squatId,
                        List.of(new WorkoutExerciseSetLogInput(1, 6, new BigDecimal("100.00"), null, null, 120, 8, null))))));

        assertEquals(1, response.imported());
        assertEquals(0, response.skippedDuplicate());
        assertEquals(1, response.rejected());
        assertEquals(ImportWorkoutSessionRowStatus.REJECTED, response.results().get(0).status());
        assertEquals("completedAt must not be before startedAt", response.results().get(0).reason());
        assertEquals(ImportWorkoutSessionRowStatus.IMPORTED, response.results().get(1).status());
        assertEquals(1, workoutSessionRepository.findAll().size());
        assertEquals(1, pendingEventCount("WorkoutLogged"));
    }

    @Test
    void shouldRejectImportRowsWithUnsupportedSource() {
        UUID squatId = createExercise("import-source-squat", "Import Source Squat");

        ImportWorkoutSessionsResponse response = workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(
                List.of(importRow(
                        "manual-session-rejected",
                        "MANUAL",
                        "Manual Imported Strength",
                        Instant.parse("2026-06-11T10:00:00Z"),
                        squatId,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 120, 8, null))))));

        assertEquals(0, response.imported());
        assertEquals(0, response.skippedDuplicate());
        assertEquals(1, response.rejected());
        assertEquals(ImportWorkoutSessionRowStatus.REJECTED, response.results().get(0).status());
        assertEquals("source must be DEVICE or IMPORTED", response.results().get(0).reason());
        assertEquals(0, workoutSessionRepository.findAll().size());
    }

    @Test
    void shouldScopeImportIdempotencyByUser() {
        UUID squatId = createExercise("import-user-squat", "Import User Squat");
        ImportWorkoutSessionRowRequest row = importRow(
                "shared-external-session",
                "IMPORTED",
                "Shared External Strength",
                Instant.parse("2026-06-11T10:00:00Z"),
                squatId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 120, 8, null)));

        ImportWorkoutSessionsResponse currentImport = workoutService.importSessions(currentUser, new ImportWorkoutSessionsRequest(List.of(row)));
        ImportWorkoutSessionsResponse otherImport = workoutService.importSessions(otherUser, new ImportWorkoutSessionsRequest(List.of(row)));

        assertEquals(1, currentImport.imported());
        assertEquals(1, otherImport.imported());
        assertEquals(2, workoutSessionRepository.findAll().size());
        assertEquals(currentUser.userId(), workoutSessionRepository.findBySessionIdAndUserId(
                currentImport.results().get(0).sessionId(),
                currentUser.userId()).orElseThrow().getUserId());
        assertEquals(otherUser.userId(), workoutSessionRepository.findBySessionIdAndUserId(
                otherImport.results().get(0).sessionId(),
                otherUser.userId()).orElseThrow().getUserId());
    }

    @Test
    void shouldExportOnlyCurrentUserWorkoutDataWithPlansAndSessionDetails() {
        UUID globalSquatId = createExercise("export-global-squat", "Export Global Squat");
        ExerciseView currentCustom = createCustomExercise(currentUser, "export-current-row", "Export Current Row");
        ExerciseView otherCustom = createCustomExercise(otherUser, "export-other-row", "Export Other Row");
        WorkoutPlanView draftPlan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Export Draft Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(
                        globalSquatId,
                        1,
                        1,
                        null,
                        null,
                        null,
                        null,
                        120,
                        null,
                        List.of(new WorkoutPlanExerciseSetTargetInput(
                                1,
                                5,
                                new BigDecimal("100.00"),
                                null,
                                null,
                                120))))));
        WorkoutPlanView archivedPlan = createPlan("Export Archived Plan", currentUser);
        workoutService.archivePlan(currentUser, archivedPlan.planId());
        WorkoutPlanView otherPlan = createPlan("Export Other Plan", otherUser);
        WorkoutSessionView currentSession = workoutService.createSession(currentUser, completedStrengthSession(
                "Export Completed Session",
                Instant.parse("2026-06-11T10:00:00Z"),
                globalSquatId,
                List.of(new WorkoutExerciseSetLogInput(
                        1,
                        6,
                        new BigDecimal("110.00"),
                        null,
                        null,
                        120,
                        8,
                        "Exported set"))));
        WorkoutSessionView otherSession = workoutService.createSession(otherUser, completedStrengthSession(
                "Export Other Session",
                Instant.parse("2026-06-12T10:00:00Z"),
                globalSquatId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("95.00"), null, null, 120, 7, null))));

        WorkoutExportView export = workoutService.exportWorkoutData(currentUser);

        assertEquals(1, export.schemaVersion());
        assertNotNull(export.exportedAt());
        assertEquals(List.of(currentCustom.exerciseId()), export.exercises().stream()
                .map(ExerciseView::exerciseId)
                .toList());
        assertFalse(export.exercises().stream()
                .anyMatch(exercise -> exercise.exerciseId().equals(otherCustom.exerciseId())
                        || exercise.exerciseId().equals(globalSquatId)));
        assertTrue(export.plans().stream().anyMatch(plan -> plan.planId().equals(draftPlan.planId())));
        assertTrue(export.plans().stream().anyMatch(plan -> plan.planId().equals(archivedPlan.planId())
                && plan.status() == WorkoutPlanStatus.ARCHIVED));
        assertFalse(export.plans().stream().anyMatch(plan -> plan.planId().equals(otherPlan.planId())));
        assertEquals(List.of(currentSession.sessionId()), export.sessions().stream()
                .map(WorkoutSessionView::sessionId)
                .toList());
        assertFalse(export.sessions().stream().anyMatch(session -> session.sessionId().equals(otherSession.sessionId())));
        WorkoutSessionView exportedSession = export.sessions().get(0);
        assertEquals(1, exportedSession.exercises().size());
        assertEquals(1, exportedSession.exercises().get(0).sets().size());
        assertEquals(6, exportedSession.exercises().get(0).sets().get(0).reps());
        assertEquals(new BigDecimal("110.00"), exportedSession.exercises().get(0).sets().get(0).weightKg());
        assertEquals(true, exportedSession.exercises().get(0).sets().get(0).logged());
        assertEquals(1, export.plans().stream()
                .filter(plan -> plan.planId().equals(draftPlan.planId()))
                .findFirst()
                .orElseThrow()
                .exercises()
                .get(0)
                .setTargets()
                .size());
    }

    @Test
    void shouldExportWellFormedEmptyWorkoutDocumentForUserWithNoData() {
        CurrentUserPrincipal emptyUser = new CurrentUserPrincipal(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                "empty@example.com",
                "Empty",
                "USER");

        WorkoutExportView export = workoutService.exportWorkoutData(emptyUser);

        assertEquals(1, export.schemaVersion());
        assertNotNull(export.exportedAt());
        assertEquals(List.of(), export.exercises());
        assertEquals(List.of(), export.plans());
        assertEquals(List.of(), export.sessions());
    }

    @Test
    void shouldRepeatSessionFromLoggedSetsOnlyAsUnloggedEditableSkeletonWithoutEvents() {
        UUID squatId = createExercise("repeat-seed-squat", "Repeat Seed Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Repeat Seed Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
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
        Instant startedAt = Instant.parse("2026-06-11T10:00:00Z");
        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId());
        moveSessionStartedAt(started.sessionId(), startedAt);
        workoutService.upsertSet(
                currentUser,
                started.sessionId(),
                started.exercises().get(0).exerciseLogId(),
                2,
                new UpsertWorkoutExerciseSetRequest(null, 6, new BigDecimal("120.00"), null, null, 150, 8, "Touched set"));
        WorkoutSessionView completed = workoutService.completeSession(
                currentUser,
                started.sessionId(),
                new CompleteWorkoutSessionRequest(startedAt.plus(Duration.ofMinutes(45))));
        outboxEventRepository.deleteAll();

        WorkoutSessionView repeated = workoutService.repeatSession(currentUser, completed.sessionId());

        assertNotEquals(completed.sessionId(), repeated.sessionId());
        assertEquals(plan.planId(), repeated.planId());
        assertEquals("Repeat Seed Plan", repeated.name());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, repeated.status());
        assertEquals(WorkoutSource.MANUAL, repeated.source());
        assertNotNull(repeated.startedAt());
        assertNull(repeated.completedAt());
        assertNull(repeated.durationMinutes());
        assertNull(repeated.caloriesBurned());
        assertNull(repeated.perceivedExertion());
        assertEquals(1, repeated.exerciseCount());
        assertNull(repeated.exercises().get(0).performedSets());
        assertNull(repeated.exercises().get(0).performedReps());
        assertNull(repeated.exercises().get(0).totalVolumeKg());
        assertEquals(1, repeated.exercises().get(0).sets().size());
        assertEquals(2, repeated.exercises().get(0).sets().get(0).setNumber());
        assertEquals(6, repeated.exercises().get(0).sets().get(0).reps());
        assertEquals(new BigDecimal("120.00"), repeated.exercises().get(0).sets().get(0).weightKg());
        assertEquals(false, repeated.exercises().get(0).sets().get(0).logged());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());

        WorkoutSessionView edited = workoutService.upsertSet(
                currentUser,
                repeated.sessionId(),
                repeated.exercises().get(0).exerciseLogId(),
                2,
                new UpsertWorkoutExerciseSetRequest(null, 7, new BigDecimal("122.50"), null, null, 150, 9, "Confirmed"));
        assertEquals(true, edited.exercises().get(0).sets().get(0).logged());
    }

    @Test
    void shouldCarryThePlanTempoIntoTheSessionAndKeepTheSessionCopyWhenThePlanChanges() {
        UUID squatId = createExercise("tempo-goblet-squat", "Tempo Goblet Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Tempo Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                40,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 2, 10, null, null, null, 75, null,
                        List.of(new WorkoutPlanExerciseSetTargetInput(1, 10, new BigDecimal("20.00"), null, null, 75),
                                new WorkoutPlanExerciseSetTargetInput(2, 10, new BigDecimal("20.00"), null, null, 75)),
                        new BigDecimal("2.5")))));
        assertEquals(0, new BigDecimal("2.5").compareTo(plan.exercises().get(0).tempoSeconds()));

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId());
        assertEquals(0, new BigDecimal("2.5").compareTo(started.exercises().get(0).tempoSeconds()));

        WorkoutPlanView copy = workoutService.duplicatePlan(currentUser, plan.planId());
        assertEquals(0, new BigDecimal("2.5").compareTo(copy.exercises().get(0).tempoSeconds()));

        WorkoutPlanView changed = workoutService.updatePlan(currentUser, plan.planId(), new UpdateWorkoutPlanRequest(
                "Tempo Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                40,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 2, 10, null, null, null, 75, null, List.of())),
                List.of()));
        assertNull(changed.exercises().get(0).tempoSeconds());
        WorkoutSessionView stillStarted = workoutService.getSession(currentUser, started.sessionId());
        assertEquals(0, new BigDecimal("2.5").compareTo(stillStarted.exercises().get(0).tempoSeconds()));

        workoutService.upsertSet(currentUser, started.sessionId(), started.exercises().get(0).exerciseLogId(), 1,
                new UpsertWorkoutExerciseSetRequest(null, 10, new BigDecimal("20.00"), null, null, 75, 7, null));
        WorkoutSessionView completed = workoutService.completeSession(currentUser, started.sessionId(),
                new CompleteWorkoutSessionRequest(Instant.now()));
        WorkoutSessionView repeated = workoutService.repeatSession(currentUser, completed.sessionId());
        assertEquals(0, new BigDecimal("2.5").compareTo(repeated.exercises().get(0).tempoSeconds()));
    }

    @Test
    void shouldReturnNotFoundWhenRepeatingNonOwnedWorkoutSession() {
        UUID squatId = createExercise("repeat-non-owned-squat", "Repeat Non-Owned Squat");
        WorkoutSessionView session = workoutService.createSession(currentUser, completedStrengthSession(
                "Owner Session",
                Instant.parse("2026-06-11T10:00:00Z"),
                squatId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("90.00"), null, null, 120, 7, null))));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.repeatSession(otherUser, session.sessionId()));
    }

    @Test
    void shouldRepeatLatestCompletedSessionOnly() {
        UUID squatId = createExercise("repeat-latest-squat", "Repeat Latest Squat");
        workoutService.createSession(currentUser, completedStrengthSession(
                "Older Strength",
                Instant.parse("2026-06-10T10:00:00Z"),
                squatId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("90.00"), null, null, 120, 7, null))));
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Not Completed Later",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-12T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                // No sets: a set logged now would make this session current, and the point here is only
                // that repeat-latest ignores a session that is not completed.
                List.of()));
        workoutService.createSession(currentUser, completedStrengthSession(
                "Latest Strength",
                Instant.parse("2026-06-11T10:00:00Z"),
                squatId,
                List.of(new WorkoutExerciseSetLogInput(1, 3, new BigDecimal("130.00"), null, null, 180, 9, null))));
        outboxEventRepository.deleteAll();

        WorkoutSessionView repeated = workoutService.repeatLatestSession(currentUser);

        assertEquals("Latest Strength", repeated.name());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, repeated.status());
        assertEquals(WorkoutSource.MANUAL, repeated.source());
        assertEquals(new BigDecimal("130.00"), repeated.exercises().get(0).sets().get(0).weightKg());
        assertEquals(false, repeated.exercises().get(0).sets().get(0).logged());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldReturnNotFoundWhenRepeatingLatestWithoutCompletedSession() {
        UUID squatId = createExercise("repeat-latest-empty-squat", "Repeat Latest Empty Squat");
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Current Draft",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-11T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        squatId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("90.00"), null, null, 120, 7, null))))));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.repeatLatestSession(currentUser));
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

    private UpsertWorkoutSessionRequest completedStrengthSession(String name,
                                                                 Instant startedAt,
                                                                 UUID exerciseId,
                                                                 List<WorkoutExerciseSetLogInput> sets) {
        return new UpsertWorkoutSessionRequest(
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
                        sets)));
    }

    private ImportWorkoutSessionRowRequest importRow(String externalId,
                                                     String source,
                                                     String name,
                                                     Instant startedAt,
                                                     UUID exerciseId,
                                                     List<WorkoutExerciseSetLogInput> sets) {
        return importRow(externalId, source, name, startedAt, exerciseId, sets, startedAt.plus(Duration.ofHours(1)));
    }

    private ImportWorkoutSessionRowRequest importRow(String externalId,
                                                     String source,
                                                     String name,
                                                     Instant startedAt,
                                                     UUID exerciseId,
                                                     List<WorkoutExerciseSetLogInput> sets,
                                                     Instant completedAt) {
        return new ImportWorkoutSessionRowRequest(
                externalId,
                source,
                null,
                name,
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                startedAt,
                completedAt,
                999,
                400,
                8,
                null,
                List.of(new WorkoutExerciseLogInput(
                        exerciseId,
                        1,
                        99,
                        999,
                        new BigDecimal("99999.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        sets)));
    }

    private long pendingEventCount(String eventType) {
        return outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .count();
    }

    private void assertFallback(ExerciseCatalogImportResponse response,
                                String field,
                                String term,
                                String mappedTo,
                                int count) {
        assertTrue(response.unmappedTerms().stream().anyMatch(unmappedTerm ->
                        field.equals(unmappedTerm.field())
                                && term.equals(unmappedTerm.term())
                                && mappedTo.equals(unmappedTerm.mappedTo())
                                && count == unmappedTerm.count()),
                () -> "Expected fallback " + field + "=" + term + " -> " + mappedTo
                        + " in " + response.unmappedTerms());
    }

    private String pendingEventPayload(String eventType) {
        return outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .findFirst()
                .orElseThrow()
                .getPayload();
    }

    private Statistics hibernateStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
    }

    @Test
    void shouldCarryThePrimaryCatalogImageOnPlanExercises() {
        // Regression for functional-defects-2026-09-26 defect 8: the Today view issued one
        // GET /exercises/{id} per plan exercise purely to read imageUrls[0]. The plan response
        // carries it now, so the client needs no extra request at all.
        UUID squatId = createExerciseWithImages("thumb-squat", "Thumb Squat",
                "https://media.example/thumb-squat-0.png", "https://media.example/thumb-squat-1.png");
        UUID rowId = createExercise("thumb-row", "Thumb Row");

        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Thumbnail Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                30,
                List.of(
                        new WorkoutPlanExerciseInput(squatId, 1, 3, 8, null, null, null, 60, null, null),
                        new WorkoutPlanExerciseInput(rowId, 2, 3, 8, null, null, null, 60, null, null))));

        WorkoutPlanView reloaded = workoutService.getPlan(currentUser, plan.planId());

        assertEquals(
                "https://media.example/thumb-squat-0.png",
                reloaded.exercises().get(0).primaryImageUrl(),
                "the lowest-position image is the thumbnail");
        assertNull(
                reloaded.exercises().get(1).primaryImageUrl(),
                "an exercise with no catalog image degrades to null, not an error");
    }

    /** Images are attached before the first save so the collection is a plain list, not a proxy. */
    private UUID createExerciseWithImages(String code, String name, String... urls) {
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
        for (int position = 0; position < urls.length; position++) {
            com.fitsync.workout.entity.ExerciseImageEntity image =
                    new com.fitsync.workout.entity.ExerciseImageEntity();
            image.setImageId(UUID.randomUUID());
            image.setExercise(entity);
            image.setPosition(position);
            image.setUrl(urls[position]);
            entity.getImages().add(image);
        }
        return exerciseRepository.save(entity).getExerciseId();
    }

    private UUID createExercise(String code, String name) {
        return createExerciseOfType(code, name, ExerciseType.STRENGTH);
    }

    private UUID createExerciseOfType(String code, String name, ExerciseType exerciseType) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(exerciseType);
        entity.setPrimaryMuscleGroup(MuscleGroup.BACK);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return exerciseRepository.save(entity).getExerciseId();
    }

    private void moveSessionStartedAt(UUID sessionId, Instant startedAt) {
        var entity = workoutSessionRepository.findById(sessionId).orElseThrow();
        entity.setStartedAt(startedAt);
        workoutSessionRepository.save(entity);
    }

    private ExerciseView createCustomExercise(CurrentUserPrincipal user, String code, String name) {
        return exerciseService.createCustomExercise(user, new CreateExerciseRequest(
                code,
                name,
                "Custom exercise",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.BEGINNER));
    }

    private UUID createHistoryBenchFixture() {
        UUID benchId = createExercise("history-bench", "History Bench");
        workoutService.createSession(currentUser, completedStrengthSession(
                "Older Bench",
                Instant.parse("2026-06-10T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 5, new BigDecimal("100.00"), null, null, 180, 8, null))));
        workoutService.createSession(currentUser, completedStrengthSession(
                "Newer Bench",
                Instant.parse("2026-06-12T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 3, new BigDecimal("120.00"), null, null, 180, 9, null))));
        workoutService.createSession(otherUser, completedStrengthSession(
                "Other User Bench",
                Instant.parse("2026-06-13T10:00:00Z"),
                benchId,
                List.of(new WorkoutExerciseSetLogInput(1, 1, new BigDecimal("200.00"), null, null, 180, 10, null))));
        workoutService.createSession(currentUser, new UpsertWorkoutSessionRequest(
                null,
                "Unfinished Bench",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                Instant.parse("2026-06-14T10:00:00Z"),
                null,
                null,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(
                        benchId,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(new WorkoutExerciseSetLogInput(1, 1, new BigDecimal("300.00"), null, null, 180, 10, null))))));
        return benchId;
    }

    private UpdateExerciseRequest updateExercise(String name) {
        return new UpdateExerciseRequest(
                name,
                "Updated exercise",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BARBELL,
                WorkoutDifficulty.INTERMEDIATE);
    }

    private WorkoutPlanView createPlan(String name, CurrentUserPrincipal user) {
        return workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                name,
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of()));
    }
}
