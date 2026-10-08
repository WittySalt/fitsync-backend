package com.fitsync.workout.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.CardioLocation;
import com.fitsync.workout.domain.DayTargetKind;
import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanDraftRequest;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.PlanEntryView;
import com.fitsync.workout.dto.PlanProposalRequest;
import com.fitsync.workout.dto.SessionEntryUpdateRequest;
import com.fitsync.workout.dto.SessionEntryView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanDayView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.entity.OutboxEventEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Plan and session entries end to end on H2: plan days with moves and time segments, the default
 * shape, the copy into a session with rounds expanded, the per-entry update, the settling at the end,
 * the events that carry moves only, the coverage and the proposal.
 *
 * <p>The fixed clock is Thursday 11 June 2026 01:00 in Singapore and Wednesday 10 June 17:00 in UTC.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
@Import(PlanEntriesServiceTests.FixedClockConfig.class)
class PlanEntriesServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-10T17:00:00Z");

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), "carol@example.com", "Carol", "USER");

    private UUID squat;
    private UUID bench;

    @BeforeEach
    void clearData() {
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
        squat = exercise("entries-squat", "Goblet Squat", MuscleGroup.LEGS, ExerciseType.STRENGTH);
        bench = exercise("entries-bench", "Dumbbell Bench Press", MuscleGroup.CHEST, ExerciseType.STRENGTH);
    }

    @Test
    void aPlanDayKeepsItsMovesSegmentsAndCardioFields() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(
                strengthDay(1),
                new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Intervals", null, 22, List.of(),
                        DayTargetKind.MINUTES, new BigDecimal("22"), CardioLocation.TREADMILL, 8, intervals()))));

        WorkoutPlanView read = workoutService.getPlan(user, plan.planId());

        WorkoutPlanDayView monday = read.days().get(0);
        assertEquals(2, monday.entries().size());
        assertEquals(2, monday.exercises().size());
        PlanEntryView first = monday.entries().get(0);
        assertEquals(EntryType.SET, first.entryType());
        assertEquals(squat, first.exerciseId());
        assertEquals("Goblet Squat", first.exerciseNameSnapshot());
        assertEquals(3, first.targetSets());
        assertNotNull(first.entryId());

        WorkoutPlanDayView thursday = read.days().get(1);
        assertEquals(DayTargetKind.MINUTES, thursday.targetKind());
        assertEquals(0, new BigDecimal("22").compareTo(thursday.targetValue()));
        assertEquals(CardioLocation.TREADMILL, thursday.location());
        assertEquals(8, thursday.effortTarget());
        assertTrue(thursday.exercises().isEmpty());
        assertEquals(List.of(SegmentKind.WARMUP, SegmentKind.WORK, SegmentKind.EASY, SegmentKind.COOLDOWN),
                thursday.entries().stream().map(PlanEntryView::kind).toList());
        PlanEntryView work = thursday.entries().get(1);
        assertNull(work.exerciseId());
        assertEquals(8, work.rounds());
        assertEquals(30, work.plannedSeconds());
        assertEquals(2, work.position());

        assertNotNull(read.coverage());
        assertEquals(new BigDecimal("3.0"), read.coverage().groups().stream()
                .filter(group -> group.group() == MuscleGroup.CHEST).findFirst().orElseThrow().setsPerWeek());
    }

    @Test
    void theOlderExercisesShapeIsStoredAsSetEntries() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(
                new WorkoutPlanDayInput(null, 2, SessionKind.STRENGTH, "Legs", null, 40, List.of(
                        new WorkoutPlanExerciseInput(squat, 1, 4, 8, new BigDecimal("20"), null, null, 90, null,
                                List.of()))))));

        PlanEntryView entry = plan.days().get(0).entries().get(0);
        assertEquals(EntryType.SET, entry.entryType());
        assertEquals(squat, entry.exerciseId());
        assertEquals(1, entry.position());
        assertEquals(4, entry.targetSets());
    }

    @Test
    void aCardioOrSportDaySentEmptyGetsTheDefaultShape() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(
                new WorkoutPlanDayInput(null, 3, SessionKind.CARDIO, "Run", null, 40, List.of()),
                new WorkoutPlanDayInput(null, 6, SessionKind.SPORT, "Badminton", SportActivity.BADMINTON, 60,
                        List.of()))));

        assertEquals(List.of(300, 1800, 300), plan.days().get(0).entries().stream()
                .map(PlanEntryView::plannedSeconds).toList());
        assertEquals("Badminton", plan.days().get(1).entries().get(1).label());
        assertEquals(List.of(300, 3000, 300), plan.days().get(1).entries().stream()
                .map(PlanEntryView::plannedSeconds).toList());
    }

    @Test
    void segmentsThatDoNotFillTheDayAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> workoutService.createPlan(user, week(List.of(
                new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Intervals", null, 40, List.of(),
                        null, null, null, null, intervals())))));
    }

    @Test
    void startingACardioDayCopiesItsSegmentsWithRoundsExpandedOnTheLocalDate() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(
                new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Intervals", null, 22, List.of(),
                        null, null, null, null, intervals()))));

        WorkoutSessionView session = workoutService.startPlan(user, plan.planId(), plan.days().get(0).planDayId(),
                "Asia/Singapore");

        List<SessionEntryView> segments = session.entries();
        assertEquals(18, segments.size());
        assertEquals("Warm-up", segments.get(0).label());
        assertEquals("Work 1", segments.get(1).label());
        assertEquals("Easy 1", segments.get(2).label());
        assertEquals("Cool-down", segments.get(17).label());
        assertTrue(segments.stream().allMatch(entry -> entry.status() == WorkoutSetStatus.PENDING
                && entry.actualSeconds() == null && entry.entryType() == EntryType.SEGMENT));
        assertEquals(LocalDate.parse("2026-06-11"), segments.get(0).loggedOn());
        assertEquals(0, session.exerciseCount());
        assertTrue(session.exercises().isEmpty());
    }

    @Test
    void repeatingACardioSessionDatesItsSegmentsOnTheCallersLocalDate() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(
                new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Intervals", null, 22, List.of(),
                        null, null, null, null, intervals()))));
        WorkoutSessionView started = workoutService.startPlan(user, plan.planId(), plan.days().get(0).planDayId(),
                "Asia/Singapore");
        workoutService.completeSession(user, started.sessionId(), null);

        WorkoutSessionView repeated = workoutService.repeatSession(user, started.sessionId(), "Asia/Singapore");

        assertEquals(18, repeated.entries().size());
        // 01:00 on 11 June in Singapore is still 10 June on the server's UTC clock.
        assertTrue(repeated.entries().stream()
                .allMatch(entry -> LocalDate.parse("2026-06-11").equals(entry.loggedOn())));
    }

    @Test
    void startingAStrengthDayCopiesItsMovesAsSetEntries() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(strengthDay(3))));

        WorkoutSessionView session = workoutService.startPlan(user, plan.planId(), plan.days().get(0).planDayId(), "UTC");

        assertEquals(2, session.exerciseCount());
        assertEquals(2, session.entries().size());
        SessionEntryView first = session.entries().get(0);
        assertEquals(EntryType.SET, first.entryType());
        assertEquals(3, first.sets().size());
        assertEquals(LocalDate.parse("2026-06-10"), first.loggedOn());
    }

    @Test
    void aFreeTimerWithAMinutesGoalStartsWithOneSteadySegmentAndHiitWithTheIntervals() {
        WorkoutSessionView timer = workoutService.createSession(user, freeStart(WorkoutType.CARDIO, List.of(), 25),
                "UTC");
        assertEquals(1, timer.entries().size());
        assertEquals(SegmentKind.STEADY, timer.entries().get(0).kind());
        assertEquals(1500, timer.entries().get(0).plannedSeconds());
        workoutService.cancelSession(user, timer.sessionId());

        WorkoutSessionView hiit = workoutService.createSession(user, freeStart(WorkoutType.HIIT, List.of(), null), "UTC");
        assertEquals(18, hiit.entries().size());
        assertEquals(300, hiit.entries().get(0).plannedSeconds());
        assertEquals(30, hiit.entries().get(1).plannedSeconds());
        assertEquals(60, hiit.entries().get(2).plannedSeconds());
        workoutService.cancelSession(user, hiit.sessionId());

        WorkoutSessionView sent = workoutService.createSession(user, freeStart(WorkoutType.CARDIO, List.of(
                PlanEntryInput.segment(SegmentKind.WARMUP, "Warm-up", 1, 120, null),
                PlanEntryInput.segment(SegmentKind.STEADY, "Steady", 2, 600, null)), 25), "UTC");
        assertEquals(List.of(120, 600), sent.entries().stream().map(SessionEntryView::plannedSeconds).toList());
    }

    @Test
    void aSegmentIsGivenItsTimeAndFinishingSettlesTheCurrentOne() {
        WorkoutSessionView session = workoutService.createSession(user, freeStart(WorkoutType.CARDIO, List.of(
                PlanEntryInput.segment(SegmentKind.WARMUP, "Warm-up", 1, 300, null),
                PlanEntryInput.segment(SegmentKind.STEADY, "Steady", 2, 1200, null),
                PlanEntryInput.segment(SegmentKind.COOLDOWN, "Cool-down", 3, 300, null)), null), "UTC");
        UUID warmup = session.entries().get(0).entryId();

        WorkoutSessionView updated = workoutService.updateEntry(user, session.sessionId(), warmup,
                SessionEntryUpdateRequest.segment(300, NOW.minusSeconds(900), WorkoutSetStatus.DONE));
        assertEquals(300, updated.entries().get(0).actualSeconds());
        assertEquals(WorkoutSetStatus.DONE, updated.entries().get(0).status());
        assertEquals(NOW.minusSeconds(900), updated.entries().get(0).startedAt());

        WorkoutSessionView finished = workoutService.completeSession(user, session.sessionId(), null);

        assertEquals(WorkoutSessionStatus.COMPLETED, finished.status());
        SessionEntryView steady = finished.entries().get(1);
        assertEquals(WorkoutSetStatus.DONE, steady.status());
        assertEquals(600, steady.actualSeconds());
        assertEquals(WorkoutSetStatus.PENDING, finished.entries().get(2).status());
        assertNull(finished.entries().get(2).actualSeconds());

        List<OutboxEventEntity> events = outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(
                OutboxEventStatus.PENDING);
        OutboxEventEntity logged = events.stream().filter(event -> "WorkoutLogged".equals(event.getEventType()))
                .findFirst().orElseThrow();
        assertTrue(logged.getPayload().contains("\"exerciseCount\":0"));
    }

    @Test
    void theEntryRouteLogsTheNextSetOfAMove() {
        WorkoutPlanView plan = workoutService.createPlan(user, week(List.of(strengthDay(3))));
        WorkoutSessionView session = workoutService.startPlan(user, plan.planId(), plan.days().get(0).planDayId(), "UTC");
        UUID move = session.entries().get(0).entryId();

        WorkoutSessionView updated = workoutService.updateEntry(user, session.sessionId(), move,
                new SessionEntryUpdateRequest(null, null, WorkoutSetStatus.DONE, null, 10, new BigDecimal("16"),
                        null, null, null, null, null));

        SessionEntryView entry = updated.entries().get(0);
        assertEquals(WorkoutSetStatus.DONE, entry.sets().get(0).status());
        assertEquals(10, entry.sets().get(0).reps());
        assertEquals(WorkoutSetStatus.PENDING, entry.sets().get(1).status());
        assertNull(entry.status());
    }

    @Test
    void coverageOfADraftAndAProposal() {
        PlanCoverageView coverage = workoutService.previewCoverage(user, new PlanDraftRequest(
                List.of(strengthDay(1), strengthDay(2)), 2, 30));
        assertEquals(new BigDecimal("6.0"), coverage.groups().stream()
                .filter(group -> group.group() == MuscleGroup.CHEST).findFirst().orElseThrow().setsPerWeek());
        assertTrue(coverage.notes().stream().anyMatch(note -> "STRENGTH_BACK_TO_BACK".equals(note.code())));
        assertTrue(coverage.notes().stream().anyMatch(note -> "OVER_PROFILE_MINUTES".equals(note.code())));

        WorkoutPlanView proposal = workoutService.proposePlan(user, new PlanProposalRequest("BEGINNER", 3, 45,
                List.of("DUMBBELLS"), List.of("FULL_BODY")), "UTC");
        assertNull(proposal.planId());
        assertEquals(List.of(1, 4, 6), proposal.days().stream().map(WorkoutPlanDayView::dayOfWeek).toList());
        assertEquals("Full body A", proposal.days().get(0).title());
        Set<UUID> picked = Set.of(squat, bench);
        assertTrue(proposal.days().get(0).entries().stream().map(PlanEntryView::exerciseId).allMatch(picked::contains));
        assertTrue(proposal.days().get(0).entries().size() >= 2);
        assertNotNull(proposal.coverage());
        PlanEntryView first = proposal.days().get(0).entries().get(0);
        assertEquals("Goblet Squat", first.exerciseName());
        assertEquals(MuscleGroup.LEGS, first.primaryMuscleGroup());
        assertEquals(ExerciseEquipment.DUMBBELL, first.equipment());
        assertNull(first.targetWeightKg());
        assertEquals(false, first.bodyweight());
        assertEquals(10, first.targetReps());
    }

    private static List<PlanEntryInput> intervals() {
        return List.of(
                PlanEntryInput.segment(SegmentKind.WARMUP, null, 1, 300, null),
                PlanEntryInput.segment(SegmentKind.WORK, null, 2, 30, 8),
                PlanEntryInput.segment(SegmentKind.EASY, null, 3, 60, 8),
                PlanEntryInput.segment(SegmentKind.COOLDOWN, null, 4, 300, null));
    }

    private WorkoutPlanDayInput strengthDay(int dayOfWeek) {
        return new WorkoutPlanDayInput(null, dayOfWeek, SessionKind.STRENGTH, "Full body A", null, 45, List.of(),
                null, null, null, null, List.of(
                        PlanEntryInput.set(squat, 1, 3, 10, new BigDecimal("16"), 75),
                        PlanEntryInput.set(bench, 2, 3, 10, new BigDecimal("14"), 75)));
    }

    private static CreateWorkoutPlanRequest week(List<WorkoutPlanDayInput> days) {
        return new CreateWorkoutPlanRequest("Entries week", null, GoalFocus.GENERAL_FITNESS, WorkoutType.CUSTOM,
                WorkoutDifficulty.BEGINNER, 45, List.of(), days);
    }

    private static UpsertWorkoutSessionRequest freeStart(WorkoutType type, List<PlanEntryInput> entries,
                                                         Integer goalMinutes) {
        return new UpsertWorkoutSessionRequest(null, "Free timer", type, WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL, NOW.minusSeconds(1200), null, null, null, null, null, List.of(), null, null,
                entries, goalMinutes);
    }

    private UUID exercise(String code, String name, MuscleGroup group, ExerciseType type) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(type);
        entity.setPrimaryMuscleGroup(group);
        entity.setEquipment(ExerciseEquipment.DUMBBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return exerciseRepository.save(entity).getExerciseId();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock entriesTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
