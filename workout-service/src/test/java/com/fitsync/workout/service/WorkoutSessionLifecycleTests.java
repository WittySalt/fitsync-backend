package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.media.MediaStorageService;
import com.fitsync.web.error.ConflictException;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.ActiveWorkoutSessionView;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionRowRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionRowStatus;
import com.fitsync.workout.dto.ImportWorkoutSessionsRequest;
import com.fitsync.workout.dto.ImportWorkoutSessionsResponse;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.messaging.DomainEventPublisher;
import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * The life of a workout in progress: which one is current, that only one may be, and that one that
 * was left is closed by the server. Real repositories on H2, driven through the service.
 *
 * <p>Open sessions are inserted straight through the repository where a test needs several, or
 * needs one that is old, because the service itself refuses to create a second one.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
class WorkoutSessionLifecycleTests {

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @MockBean
    private MediaStorageService mediaStorageService;

    @MockBean
    private UpstreamHttpImageSource upstreamHttpImageSource;

    @SpyBean
    private DomainEventPublisher domainEventPublisher;

    private final CurrentUserPrincipal alice = user("11111111-1111-1111-1111-111111111111", "alice@example.com");
    private final CurrentUserPrincipal bob = user("22222222-2222-2222-2222-222222222222", "bob@example.com");
    private final CurrentUserPrincipal carol = user("33333333-3333-3333-3333-333333333333", "carol@example.com");

    private Instant now;

    @BeforeEach
    void clean() {
        Mockito.reset(domainEventPublisher);
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void resetSpy() {
        Mockito.reset(domainEventPublisher);
    }

    // ---- the active session -------------------------------------------------------------------

    @Test
    void shouldAnswerNothingWhenThereIsNoActiveSessionAndAskTheDatabaseOnce() {
        Statistics statistics = statistics();
        statistics.clear();

        Optional<ActiveWorkoutSessionView> active = workoutService.getActiveSession(alice);

        assertTrue(active.isEmpty());
        assertEquals(1, statistics.getPrepareStatementCount());
    }

    @Test
    void shouldAnswerTheActiveSessionWithItsExercisesSetsAndResumeState() {
        UUID squat = createExercise("active-squat", "Active Squat");
        UUID sessionId = insertOpenSession(alice, "Leg day", now.minus(Duration.ofMinutes(40)), squat,
                List.of(now.minus(Duration.ofMinutes(30))), List.of(now.minus(Duration.ofMinutes(20))));
        Statistics statistics = statistics();
        statistics.clear();

        ActiveWorkoutSessionView active = workoutService.getActiveSession(alice).orElseThrow();

        assertEquals(sessionId, active.session().sessionId());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, active.session().status());
        assertEquals(1, active.session().exercises().size());
        assertEquals(2, active.session().exercises().get(0).sets().size());
        assertEquals(sessionId, active.resume().sessionId());
        assertEquals(1, active.resume().doneSets());
        assertEquals(1, active.resume().skippedSets());
        assertEquals(2, active.resume().totalSets());
        assertNull(active.session().autoClosedAt());
        // Open sessions, their activity, the session with its exercises, and the sets.
        assertTrue(statistics.getPrepareStatementCount() == 4,
                "Expected 4 statements, got " + statistics.getPrepareStatementCount());
    }

    @Test
    void shouldAnswerAnActiveSessionWithoutExercisesInThreeStatements() {
        insertOpenSession(alice, "Free session", now.minus(Duration.ofMinutes(5)), null, List.of(), List.of());
        Statistics statistics = statistics();
        statistics.clear();

        assertTrue(workoutService.getActiveSession(alice).isPresent());

        assertTrue(statistics.getPrepareStatementCount() == 3,
                "Expected 3 statements, got " + statistics.getPrepareStatementCount());
    }

    @Test
    void shouldNeverAnswerAnotherUsersSession() {
        insertOpenSession(bob, "Bob's session", now.minus(Duration.ofMinutes(5)), null, List.of(), List.of());

        assertTrue(workoutService.getActiveSession(alice).isEmpty());
        assertTrue(workoutService.getActiveSession(bob).isPresent());
    }

    @Test
    void shouldKeepTheNewestOfSeveralOpenSessionsAndCloseTheOthers() {
        UUID squat = createExercise("several-squat", "Several Squat");
        Instant lastSet = now.minus(Duration.ofHours(1));
        UUID withSets = insertOpenSession(alice, "Older with sets", now.minus(Duration.ofHours(2)), squat,
                List.of(lastSet), List.of());
        UUID withoutSets = insertOpenSession(alice, "Older without sets", now.minus(Duration.ofMinutes(90)), null,
                List.of(), List.of());
        UUID newest = insertOpenSession(alice, "Newest", now.minus(Duration.ofMinutes(10)), null, List.of(), List.of());

        ActiveWorkoutSessionView active = workoutService.getActiveSession(alice).orElseThrow();

        assertEquals(newest, active.session().sessionId());
        WorkoutSessionView completed = workoutService.getSession(alice, withSets);
        assertEquals(WorkoutSessionStatus.COMPLETED, completed.status());
        assertEquals(lastSet, completed.completedAt());
        assertNotNull(completed.autoClosedAt());
        WorkoutSessionView cancelled = workoutService.getSession(alice, withoutSets);
        assertEquals(WorkoutSessionStatus.CANCELLED, cancelled.status());
        assertNull(cancelled.completedAt());
        assertNotNull(cancelled.autoClosedAt());
        assertEquals(1, pendingEventCount("WorkoutLogged"));
    }

    // ---- closing a left workout ---------------------------------------------------------------

    @Test
    void shouldCompleteAStaleSessionAtItsLastSetAndPublishTheSameEventsAsAManualComplete() {
        UUID squat = createExercise("stale-squat", "Stale Squat");
        Instant startedAt = now.minus(Duration.ofHours(5));
        Instant lastSet = now.minus(Duration.ofHours(4));
        UUID sessionId = insertOpenSession(alice, "Left behind", startedAt, squat,
                List.of(now.minus(Duration.ofMinutes(290)), lastSet), List.of());

        assertTrue(workoutService.getActiveSession(alice).isEmpty());

        WorkoutSessionView closed = workoutService.getSession(alice, sessionId);
        assertEquals(WorkoutSessionStatus.COMPLETED, closed.status());
        assertEquals(lastSet, closed.completedAt());
        assertEquals(Integer.valueOf(60), closed.durationMinutes());
        assertEquals(SessionKind.STRENGTH, closed.kind());
        assertNotNull(closed.autoClosedAt());
        assertEquals(1, pendingEventCount("WorkoutLogged"));
        assertEquals(1, pendingEventCount("PersonalRecordAchieved"));
        assertEquals(Integer.valueOf(2), closed.exercises().get(0).performedSets());
    }

    @Test
    void shouldCancelAStaleSessionWithoutDoneSetsAndPublishNothing() {
        UUID squat = createExercise("skip-squat", "Skip Squat");
        UUID onlySkipped = insertOpenSession(alice, "Only skipped", now.minus(Duration.ofHours(6)), squat,
                List.of(), List.of(now.minus(Duration.ofHours(5))));

        assertTrue(workoutService.getActiveSession(alice).isEmpty());

        WorkoutSessionView closed = workoutService.getSession(alice, onlySkipped);
        assertEquals(WorkoutSessionStatus.CANCELLED, closed.status());
        assertNotNull(closed.autoClosedAt());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldLeaveAFreshSessionAlone() {
        UUID squat = createExercise("fresh-squat", "Fresh Squat");
        UUID sessionId = insertOpenSession(alice, "Fresh", now.minus(Duration.ofMinutes(20)), squat,
                List.of(now.minus(Duration.ofMinutes(10))), List.of());

        ActiveWorkoutSessionView active = workoutService.getActiveSession(alice).orElseThrow();

        assertEquals(sessionId, active.session().sessionId());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, active.session().status());
        assertNull(active.session().autoClosedAt());
        assertEquals(0, outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING).size());
    }

    @Test
    void shouldKeepALongWorkoutThatWasStillBeingLoggedTo() {
        UUID squat = createExercise("long-squat", "Long Squat");
        UUID sessionId = insertOpenSession(alice, "Long one", now.minus(Duration.ofHours(5)), squat,
                List.of(now.minus(Duration.ofMinutes(10))), List.of());

        assertEquals(sessionId, workoutService.getActiveSession(alice).orElseThrow().session().sessionId());
    }

    @Test
    void shouldCloseAnIdleSessionWithoutAnySetAtOnceItsIdleTimeIsUp() {
        UUID sessionId = insertOpenSession(alice, "Never started", now.minus(Duration.ofHours(3)).minusSeconds(30), null,
                List.of(), List.of());

        assertTrue(workoutService.getActiveSession(alice).isEmpty());
        assertEquals(WorkoutSessionStatus.CANCELLED, workoutService.getSession(alice, sessionId).status());
    }

    @Test
    void shouldEndAStaleSessionOneMinuteAfterItStartedWhenTheLastSetIsAtItsStart() {
        UUID squat = createExercise("minute-squat", "Minute Squat");
        Instant startedAt = now.minus(Duration.ofHours(6));
        UUID sessionId = insertOpenSession(alice, "Instant sets", startedAt, squat, List.of(startedAt), List.of());

        workoutService.getActiveSession(alice);

        WorkoutSessionView closed = workoutService.getSession(alice, sessionId);
        assertEquals(startedAt.plus(Duration.ofMinutes(1)), closed.completedAt());
        assertEquals(Integer.valueOf(1), closed.durationMinutes());
    }

    // ---- one workout in progress at a time ----------------------------------------------------

    @Test
    void shouldRefuseEveryWayOfStartingASecondSessionWithTheActiveOneNamed() {
        UUID squat = createExercise("guard-squat", "Guard Squat");
        WorkoutPlanView plan = createPlan("Guard plan", alice);
        WorkoutSessionView completed = workoutService.createSession(alice, completedSession("Done before", squat));
        WorkoutSessionView active = workoutService.startPlan(alice, plan.planId());

        assertConflictNaming(active, () -> workoutService.startPlan(alice, plan.planId()));
        assertConflictNaming(active, () -> workoutService.startPlan(alice, plan.planId(), null, "UTC"));
        assertConflictNaming(active, () -> workoutService.createSession(alice, inProgressRequest("Another")));
        assertConflictNaming(active, () -> workoutService.repeatSession(alice, completed.sessionId()));
        assertConflictNaming(active, () -> workoutService.repeatLatestSession(alice));
        assertConflictNaming(active, () -> workoutService.updateSession(
                alice, completed.sessionId(), inProgressRequest("Reopened")));

        assertEquals(1, workoutSessionRepository.findAll().stream()
                .filter(session -> session.getStatus() == WorkoutSessionStatus.IN_PROGRESS)
                .count());
    }

    @Test
    void shouldAllowEveryPathAgainOnceTheActiveSessionIsCompletedOrCancelled() {
        UUID squat = createExercise("again-squat", "Again Squat");
        WorkoutPlanView plan = createPlan("Again plan", alice);
        workoutService.createSession(alice, completedSession("Done before", squat));

        WorkoutSessionView first = workoutService.startPlan(alice, plan.planId());
        workoutService.completeSession(alice, first.sessionId(), null);
        WorkoutSessionView second = workoutService.startPlan(alice, plan.planId());
        workoutService.cancelSession(alice, second.sessionId());
        WorkoutSessionView third = workoutService.createSession(alice, inProgressRequest("Free session"));
        workoutService.cancelSession(alice, third.sessionId());
        WorkoutSessionView repeated = workoutService.repeatLatestSession(alice);

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, repeated.status());
        assertEquals(repeated.sessionId(), workoutService.getActiveSession(alice).orElseThrow().session().sessionId());
    }

    @Test
    void shouldNotRefuseLoggingHistoryOrPlanningWhileASessionIsInProgress() {
        UUID squat = createExercise("history-squat", "History Squat");
        workoutService.startPlan(alice, createPlan("History plan", alice).planId());

        WorkoutSessionView history = workoutService.createSession(alice, completedSession("Yesterday", squat));

        assertEquals(WorkoutSessionStatus.COMPLETED, history.status());
    }

    @Test
    void shouldLetTheActiveSessionBeUpdatedWithoutConflictingWithItself() {
        WorkoutSessionView active = workoutService.createSession(alice, inProgressRequest("Editable"));

        WorkoutSessionView updated = workoutService.updateSession(alice, active.sessionId(), inProgressRequest("Renamed"));

        assertEquals("Renamed", updated.name());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, updated.status());
    }

    @Test
    void shouldReopenACompletedSessionWhenNothingElseIsInProgress() {
        UUID squat = createExercise("reopen-squat", "Reopen Squat");
        WorkoutSessionView completed = workoutService.createSession(alice, completedSession("Finished", squat));

        WorkoutSessionView reopened = workoutService.updateSession(alice, completed.sessionId(), inProgressRequest("Finished"));

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, reopened.status());
    }

    @Test
    void shouldNotLetAForgottenSessionBlockANewStart() {
        UUID forgotten = insertOpenSession(alice, "Forgotten", now.minus(Duration.ofHours(8)), null, List.of(), List.of());
        WorkoutPlanView plan = createPlan("Fresh start plan", alice);

        WorkoutSessionView started = workoutService.startPlan(alice, plan.planId());

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, started.status());
        assertEquals(WorkoutSessionStatus.CANCELLED, workoutService.getSession(alice, forgotten).status());
    }

    @Test
    void shouldScopeTheGuardToTheCaller() {
        workoutService.createSession(alice, inProgressRequest("Alice's"));

        WorkoutSessionView bobs = workoutService.createSession(bob, inProgressRequest("Bob's"));

        assertEquals(WorkoutSessionStatus.IN_PROGRESS, bobs.status());
    }

    @Test
    void shouldRejectAnImportedRowThatIsInProgressAndKeepTheRest() {
        ImportWorkoutSessionsResponse response = workoutService.importSessions(alice, new ImportWorkoutSessionsRequest(List.of(
                importRow("live-1", WorkoutSessionStatus.IN_PROGRESS),
                importRow("done-1", WorkoutSessionStatus.COMPLETED))));

        assertEquals(1, response.rejected());
        assertEquals(1, response.imported());
        assertEquals(ImportWorkoutSessionRowStatus.REJECTED, response.results().get(0).status());
        assertEquals("An imported workout is history and cannot be in progress.", response.results().get(0).reason());
        assertEquals(ImportWorkoutSessionRowStatus.IMPORTED, response.results().get(1).status());
        assertTrue(workoutService.getActiveSession(alice).isEmpty());
    }

    // ---- editing what the server closed -------------------------------------------------------

    @Test
    void shouldKeepAutoClosedAtAndPublishWorkoutUpdatedWhenAnAutoClosedSessionIsEdited() {
        UUID squat = createExercise("edit-squat", "Edit Squat");
        Instant startedAt = now.minus(Duration.ofHours(5));
        UUID sessionId = insertOpenSession(alice, "Left behind", startedAt, squat,
                List.of(now.minus(Duration.ofHours(4))), List.of());
        workoutService.getActiveSession(alice);
        WorkoutSessionView closed = workoutService.getSession(alice, sessionId);
        outboxEventRepository.deleteAll();

        Instant laterEnd = now.minus(Duration.ofMinutes(210));
        WorkoutSessionView edited = workoutService.updateSession(alice, sessionId, new UpsertWorkoutSessionRequest(
                null, "Left behind", WorkoutType.STRENGTH, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, laterEnd, null, null, null, "Finished it properly.", List.of()));

        assertEquals(WorkoutSessionStatus.COMPLETED, edited.status());
        assertEquals(laterEnd, edited.completedAt());
        assertEquals(closed.autoClosedAt(), edited.autoClosedAt());
        assertEquals(1, pendingEventCount("WorkoutUpdated"));
        assertEquals(0, pendingEventCount("WorkoutLogged"));
    }

    @Test
    void shouldShowThePlanDayAndTheAutoCloseTimeInTheView() {
        UUID squat = createExercise("day-squat", "Day Squat");
        WorkoutPlanView plan = workoutService.createPlan(alice, new CreateWorkoutPlanRequest(
                "Week plan", null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of(),
                List.of(new com.fitsync.workout.dto.WorkoutPlanDayInput(
                        null, java.time.LocalDate.now(java.time.ZoneOffset.UTC).getDayOfWeek().getValue(),
                        SessionKind.STRENGTH, "Today's day", null, 45,
                        List.of(new com.fitsync.workout.dto.WorkoutPlanExerciseInput(
                                squat, 1, 3, 8, null, null, null, 60, null, null))))));

        WorkoutSessionView started = workoutService.startPlan(alice, plan.planId(), plan.days().get(0).planDayId(), "UTC");

        assertEquals(plan.days().get(0).planDayId(), started.planDayId());
        assertNull(started.autoClosedAt());
        assertEquals(started.planDayId(), workoutService.getActiveSession(alice).orElseThrow().session().planDayId());
    }

    // ---- the sweep ----------------------------------------------------------------------------

    @Test
    void shouldSweepStaleSessionsOfSeveralUsers() {
        UUID squat = createExercise("sweep-squat", "Sweep Squat");
        UUID aliceSession = insertOpenSession(alice, "Alice left", now.minus(Duration.ofHours(5)), squat,
                List.of(now.minus(Duration.ofHours(4))), List.of());
        UUID bobSession = insertOpenSession(bob, "Bob left", now.minus(Duration.ofHours(6)), null, List.of(), List.of());
        UUID carolSession = insertOpenSession(carol, "Carol fresh", now.minus(Duration.ofMinutes(15)), null,
                List.of(), List.of());

        StaleSessionSweepResult result = workoutService.closeStaleSessions();

        assertEquals(1, result.completed());
        assertEquals(1, result.cancelled());
        assertEquals(0, result.failed());
        assertEquals(WorkoutSessionStatus.COMPLETED, workoutService.getSession(alice, aliceSession).status());
        assertEquals(WorkoutSessionStatus.CANCELLED, workoutService.getSession(bob, bobSession).status());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, workoutService.getSession(carol, carolSession).status());
        assertEquals(1, pendingEventCount("WorkoutLogged"));
    }

    @Test
    void shouldSweepAnOlderDuplicateThatIsStillRecentAndKeepTheNewest() {
        UUID older = insertOpenSession(alice, "Older", now.minus(Duration.ofMinutes(30)), null, List.of(), List.of());
        UUID newer = insertOpenSession(alice, "Newer", now.minus(Duration.ofMinutes(5)), null, List.of(), List.of());

        StaleSessionSweepResult result = workoutService.closeStaleSessions();

        assertEquals(1, result.closed());
        assertEquals(WorkoutSessionStatus.CANCELLED, workoutService.getSession(alice, older).status());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, workoutService.getSession(alice, newer).status());
    }

    @Test
    void shouldKeepSweepingWhenOneSessionFailsToClose() {
        UUID squat = createExercise("fail-squat", "Fail Squat");
        UUID good = insertOpenSession(alice, "Good", now.minus(Duration.ofHours(5)), squat,
                List.of(now.minus(Duration.ofHours(4))), List.of());
        UUID bad = insertOpenSession(bob, "Bad", now.minus(Duration.ofHours(5)), squat,
                List.of(now.minus(Duration.ofHours(4))), List.of());
        UUID empty = insertOpenSession(carol, "Empty", now.minus(Duration.ofHours(7)), null, List.of(), List.of());
        doThrow(new IllegalStateException("simulated failure"))
                .when(domainEventPublisher).publish(any(), any(), eq(bad.toString()), any());

        StaleSessionSweepResult result = workoutService.closeStaleSessions();

        assertEquals(1, result.completed());
        assertEquals(1, result.cancelled());
        assertEquals(1, result.failed());
        assertEquals(WorkoutSessionStatus.COMPLETED, workoutService.getSession(alice, good).status());
        assertEquals(WorkoutSessionStatus.CANCELLED, workoutService.getSession(carol, empty).status());
        WorkoutSessionView untouched = workoutService.getSession(bob, bad);
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, untouched.status());
        assertNull(untouched.autoClosedAt());
    }

    @Test
    void shouldSweepMoreSessionsThanOnePageHolds() {
        for (int index = 0; index < 105; index++) {
            insertOpenSession(user(new UUID(1L, index).toString(), "user" + index + "@example.com"),
                    "Left " + index, now.minus(Duration.ofHours(4)).minusSeconds(index), null, List.of(), List.of());
        }

        StaleSessionSweepResult result = workoutService.closeStaleSessions();

        assertEquals(105, result.cancelled());
        assertEquals(0, result.failed());
        assertFalse(workoutSessionRepository.findAll().stream()
                .anyMatch(session -> session.getStatus() == WorkoutSessionStatus.IN_PROGRESS));
    }

    // ---- the flaky path -----------------------------------------------------------------------

    @Test
    void shouldCompleteASessionRightAfterItStartedWithoutTrippingTheTimelineCheck() {
        WorkoutPlanView plan = createPlan("Quick plan", alice);
        for (int index = 0; index < 25; index++) {
            WorkoutSessionView started = workoutService.startPlan(alice, plan.planId());
            WorkoutSessionView completed = workoutService.completeSession(alice, started.sessionId(), null);
            assertFalse(completed.completedAt().isBefore(completed.startedAt()));
        }
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void assertConflictNaming(WorkoutSessionView active, org.junit.jupiter.api.function.Executable call) {
        ConflictException conflict = assertThrows(ConflictException.class, call);
        assertEquals("ACTIVE_SESSION_EXISTS", conflict.getCode());
        assertEquals("You have a workout in progress.", conflict.getMessage());
        assertEquals(active.sessionId().toString(), conflict.getData().get("sessionId"));
        assertEquals(active.name(), conflict.getData().get("name"));
        // The database keeps microseconds, so the stored start is compared, not the one in memory.
        assertEquals(workoutService.getSession(alice, active.sessionId()).startedAt().toString(),
                conflict.getData().get("startedAt"));
    }

    private Statistics statistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
    }

    private long pendingEventCount(String eventType) {
        return outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING)
                .stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .count();
    }

    private static CurrentUserPrincipal user(String id, String email) {
        return new CurrentUserPrincipal(UUID.fromString(id), email, "Test", "USER");
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

    private WorkoutPlanView createPlan(String name, CurrentUserPrincipal owner) {
        return workoutService.createPlan(owner, new CreateWorkoutPlanRequest(
                name, null, GoalFocus.STRENGTH, WorkoutType.STRENGTH, WorkoutDifficulty.BEGINNER, 45, List.of()));
    }

    /**
     * Inserts an in-progress session directly. Each done or skipped set is stamped with the given
     * time, which is how the sets of a workout that was left in the past are made.
     */
    private UUID insertOpenSession(CurrentUserPrincipal owner,
                                   String name,
                                   Instant startedAt,
                                   UUID exerciseId,
                                   List<Instant> doneAt,
                                   List<Instant> skippedAt) {
        WorkoutSessionEntity session = new WorkoutSessionEntity();
        session.setSessionId(UUID.randomUUID());
        session.setUserId(owner.userId());
        session.setName(name);
        session.setWorkoutType(WorkoutType.STRENGTH);
        session.setKind(SessionKind.STRENGTH);
        session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
        session.setSource(WorkoutSource.MANUAL);
        session.setStartedAt(startedAt);
        if (exerciseId != null && (!doneAt.isEmpty() || !skippedAt.isEmpty())) {
            ExerciseEntity exercise = exerciseRepository.findById(exerciseId).orElseThrow();
            WorkoutExerciseLogEntity log = new WorkoutExerciseLogEntity();
            log.setExerciseLogId(UUID.randomUUID());
            log.setSession(session);
            log.setExercise(exercise);
            log.setExerciseNameSnapshot(exercise.getName());
            log.setSequence(1);
            int setNumber = 1;
            for (Instant at : doneAt) {
                log.getSets().add(set(log, setNumber++, WorkoutSetStatus.DONE, at));
            }
            for (Instant at : skippedAt) {
                log.getSets().add(set(log, setNumber++, WorkoutSetStatus.SKIPPED, at));
            }
            session.getExercises().add(log);
        }
        return workoutSessionRepository.save(session).getSessionId();
    }

    private WorkoutExerciseSetLogEntity set(WorkoutExerciseLogEntity log, int number, WorkoutSetStatus status, Instant at) {
        WorkoutExerciseSetLogEntity set = new WorkoutExerciseSetLogEntity();
        set.setSetLogId(UUID.randomUUID());
        set.setExerciseLog(log);
        set.setSetNumber(number);
        set.setReps(5);
        set.setWeightKg(new BigDecimal("100.00"));
        set.setStatus(status);
        set.setUpdatedAt(at);
        return set;
    }

    private UpsertWorkoutSessionRequest inProgressRequest(String name) {
        return new UpsertWorkoutSessionRequest(
                null, name, WorkoutType.STRENGTH, WorkoutSessionStatus.IN_PROGRESS, WorkoutSource.MANUAL,
                Instant.now().minusSeconds(60), null, null, null, null, null, List.of());
    }

    private UpsertWorkoutSessionRequest completedSession(String name, UUID exerciseId) {
        Instant startedAt = Instant.now().minus(Duration.ofDays(1));
        return new UpsertWorkoutSessionRequest(
                null, name, WorkoutType.STRENGTH, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, startedAt.plus(Duration.ofHours(1)), 60, null, null, null,
                List.of(new com.fitsync.workout.dto.WorkoutExerciseLogInput(
                        exerciseId, 1, null, null, null, null, null, null, null, null,
                        List.of(new com.fitsync.workout.dto.WorkoutExerciseSetLogInput(
                                1, 5, new BigDecimal("90.00"), null, null, 120, 7, null)))));
    }

    private ImportWorkoutSessionRowRequest importRow(String externalId, WorkoutSessionStatus status) {
        Instant startedAt = Instant.now().minus(Duration.ofDays(2));
        return new ImportWorkoutSessionRowRequest(
                externalId, "IMPORTED", null, "Imported " + externalId, WorkoutType.CARDIO, status,
                startedAt, status == WorkoutSessionStatus.COMPLETED ? startedAt.plus(Duration.ofMinutes(40)) : null,
                40, null, null, null, List.of());
    }
}
