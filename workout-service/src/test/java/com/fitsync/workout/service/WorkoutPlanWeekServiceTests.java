package com.fitsync.workout.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.UpdateWorkoutPlanRequest;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanDayView;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-level coverage for a plan with a week: creating, updating, and starting a session
 * from one of its days. Mirrors the setup style of {@link WorkoutServicePersistenceTests}: real
 * repositories and a real H2 database, driven through the public service layer.
 *
 * <p>The fixed clock below resolves to Thursday in Asia/Singapore and, at the very same instant,
 * Wednesday in UTC — the two zones disagree about the day of the week, which several tests rely on.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
@Import(WorkoutPlanWeekServiceTests.FixedClockConfig.class)
class WorkoutPlanWeekServiceTests {

    private static final Instant THURSDAY_IN_SINGAPORE = Instant.parse("2026-06-10T17:00:00Z");

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

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

    @BeforeEach
    void clearData() {
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void shouldCreateAndReadBackAFourDayWeek() {
        UUID squatId = createExercise("week-squat", "Week Squat");
        UUID rowId = createExercise("week-row", "Week Row");
        UUID runId = createExercise("week-run", "Week Run");

        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));

        WorkoutPlanView fetched = workoutService.getPlan(currentUser, plan.planId());

        assertEquals(4, fetched.days().size());
        assertEquals(4, fetched.sessionsPerWeek());
        assertTrue(fetched.exercises().isEmpty());
        assertEquals(List.of(1, 4, 5, 6), fetched.days().stream().map(WorkoutPlanDayView::dayOfWeek).toList());
        WorkoutPlanDayView monday = fetched.days().get(0);
        assertEquals(SessionKind.STRENGTH, monday.kind());
        assertEquals(2, monday.exercises().size());
        assertEquals(1, monday.exercises().get(0).sequence());
        assertEquals(2, monday.exercises().get(1).sequence());
        WorkoutPlanDayView thursday = fetched.days().get(1);
        assertEquals(SessionKind.CARDIO, thursday.kind());
        assertEquals(1, thursday.exercises().size());
        WorkoutPlanDayView saturday = fetched.days().get(3);
        assertEquals(SessionKind.SPORT, saturday.kind());
        assertEquals(SportActivity.BADMINTON, saturday.sportActivity());
        assertEquals("Badminton", saturday.sportLabel());
        assertTrue(saturday.exercises().isEmpty());
    }

    @Test
    void shouldKeepRetainedDayIdAndRemoveDroppedDayOnUpdate() {
        UUID squatId = createExercise("update-squat", "Update Squat");
        UUID rowId = createExercise("update-row", "Update Row");
        UUID runId = createExercise("update-run", "Update Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));
        UUID mondayPlanDayId = plan.days().get(0).planDayId();
        UUID thursdayPlanDayId = plan.days().get(1).planDayId();

        WorkoutPlanView updated = workoutService.updatePlan(currentUser, plan.planId(), new UpdateWorkoutPlanRequest(
                plan.name(),
                plan.description(),
                plan.goalFocus(),
                plan.workoutType(),
                plan.difficulty(),
                plan.estimatedDurationMinutes(),
                List.of(),
                List.of(
                        new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Upper Push", null, 45,
                                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))),
                        new WorkoutPlanDayInput(null, 2, SessionKind.MOBILITY, "Recovery", null, 30, List.of()))));

        assertEquals(2, updated.days().size());
        assertEquals(mondayPlanDayId, updated.days().get(0).planDayId(), "the retained Monday keeps its id");
        assertTrue(updated.days().stream().noneMatch(day -> day.planDayId().equals(thursdayPlanDayId)),
                "Thursday was dropped and is gone");
        assertEquals(2, updated.days().get(1).dayOfWeek());
    }

    @Test
    void shouldRejectTwoDaysOnTheSameDayOfWeek() {
        UUID squatId = createExercise("dup-day-squat", "Dup Day Squat");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workoutService.createPlan(currentUser,
                new CreateWorkoutPlanRequest(
                        "Duplicate Day Plan",
                        null,
                        GoalFocus.STRENGTH,
                        WorkoutType.STRENGTH,
                        WorkoutDifficulty.BEGINNER,
                        45,
                        List.of(),
                        List.of(
                                new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Push", null, 45,
                                        List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))),
                                new WorkoutPlanDayInput(null, 1, SessionKind.MOBILITY, "Stretch", null, 30, List.of())))));

        assertEquals("A plan can have one entry for each day of the week.", exception.getMessage());
    }

    @Test
    void shouldRejectASportDayWithoutASportActivity() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workoutService.createPlan(currentUser,
                new CreateWorkoutPlanRequest(
                        "Sport No Activity Plan",
                        null,
                        GoalFocus.STRENGTH,
                        WorkoutType.STRENGTH,
                        WorkoutDifficulty.BEGINNER,
                        45,
                        List.of(),
                        List.of(new WorkoutPlanDayInput(null, 6, SessionKind.SPORT, "Weekend Game", null, 60, List.of())))));

        assertEquals("A sport day needs a sport activity.", exception.getMessage());
    }

    @Test
    void shouldRejectASportDayWithExercises() {
        UUID squatId = createExercise("sport-exercise-squat", "Sport Exercise Squat");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workoutService.createPlan(currentUser,
                new CreateWorkoutPlanRequest(
                        "Sport With Exercises Plan",
                        null,
                        GoalFocus.STRENGTH,
                        WorkoutType.STRENGTH,
                        WorkoutDifficulty.BEGINNER,
                        45,
                        List.of(),
                        List.of(new WorkoutPlanDayInput(null, 6, SessionKind.SPORT, "Weekend Game", SportActivity.BADMINTON, 60,
                                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null)))))));

        assertEquals("A sport day has no exercises.", exception.getMessage());
    }

    @Test
    void shouldRejectDaysTogetherWithTopLevelExercises() {
        UUID squatId = createExercise("mixed-plan-squat", "Mixed Plan Squat");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> workoutService.createPlan(currentUser,
                new CreateWorkoutPlanRequest(
                        "Mixed Plan",
                        null,
                        GoalFocus.STRENGTH,
                        WorkoutType.STRENGTH,
                        WorkoutDifficulty.BEGINNER,
                        45,
                        List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null)),
                        List.of(new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Push", null, 45, List.of())))));

        assertEquals("A plan with a week lists its exercises under its days.", exception.getMessage());
    }

    @Test
    void shouldStillCreateReadUpdateAndStartAFlatPlanAsBefore() {
        UUID squatId = createExercise("flat-squat", "Flat Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Flat Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))));

        assertTrue(plan.days().isEmpty());
        assertNull(plan.sessionsPerWeek());
        assertEquals(1, plan.exercises().size());

        WorkoutPlanView updated = workoutService.updatePlan(currentUser, plan.planId(), new UpdateWorkoutPlanRequest(
                "Flat Plan Updated",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                50,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 4, 5, null, null, null, 120, null, null))));

        assertTrue(updated.days().isEmpty());
        assertNull(updated.sessionsPerWeek());

        WorkoutSessionView started = workoutService.startPlan(currentUser, updated.planId());

        assertEquals(updated.planId(), started.planId());
        assertEquals(WorkoutSessionStatus.IN_PROGRESS, started.status());
    }

    @Test
    void shouldStartTheDayMatchingTodayInTheGivenTimezone() {
        UUID squatId = createExercise("start-squat", "Start Squat");
        UUID rowId = createExercise("start-row", "Start Row");
        UUID runId = createExercise("start-run", "Start Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));
        UUID thursdayPlanDayId = plan.days().get(1).planDayId();

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId(), null, "Asia/Singapore");

        assertEquals(SessionKind.CARDIO, started.kind());
        assertEquals(WorkoutType.CARDIO, started.workoutType());
        assertEquals(thursdayPlanDayId, workoutSessionRepository.findById(started.sessionId()).orElseThrow().getPlanDayId());
        assertEquals(thursdayPlanDayId, started.planDayId());
    }

    @Test
    void shouldDisagreeBetweenUtcAndSingaporeAtTheSameInstant() {
        UUID squatId = createExercise("tz-squat", "Tz Squat");
        UUID rowId = createExercise("tz-row", "Tz Row");
        UUID runId = createExercise("tz-run", "Tz Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));

        // At THURSDAY_IN_SINGAPORE the UTC calendar date is a Wednesday, and this plan has
        // nothing scheduled on a Wednesday, so the default (UTC) timezone finds no day at all.
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> workoutService.startPlan(currentUser, plan.planId(), null, "UTC"));
        assertEquals("The plan has nothing on this day.", exception.getMessage());

        WorkoutSessionView startedInSingapore = workoutService.startPlan(currentUser, plan.planId(), null, "Asia/Singapore");
        assertEquals(SessionKind.CARDIO, startedInSingapore.kind());
    }

    @Test
    void shouldRejectWhenThePlanHasNothingScheduledOnTheResolvedDay() {
        UUID squatId = createExercise("nothing-squat", "Nothing Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Two Day Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(),
                List.of(
                        new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Push", null, 45,
                                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))),
                        new WorkoutPlanDayInput(null, 5, SessionKind.STRENGTH, "Pull", null, 45, List.of()))));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> workoutService.startPlan(currentUser, plan.planId(), null, "Asia/Singapore"));

        assertEquals("The plan has nothing on this day.", exception.getMessage());
    }

    @Test
    void shouldStartAnExplicitPlanDayRegardlessOfToday() {
        UUID squatId = createExercise("explicit-squat", "Explicit Squat");
        UUID rowId = createExercise("explicit-row", "Explicit Row");
        UUID runId = createExercise("explicit-run", "Explicit Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));
        UUID saturdayPlanDayId = plan.days().get(3).planDayId();

        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId(), saturdayPlanDayId, "Asia/Singapore");

        assertEquals(SessionKind.SPORT, started.kind());
        assertEquals(SportActivity.BADMINTON, started.sportActivity());
        assertEquals(WorkoutType.SPORTS, started.workoutType());
        assertEquals(saturdayPlanDayId, workoutSessionRepository.findById(started.sessionId()).orElseThrow().getPlanDayId());
    }

    @Test
    void shouldReturnNotFoundForAPlanDayFromAnotherPlan() {
        UUID squatId = createExercise("other-plan-squat", "Other Plan Squat");
        UUID rowId = createExercise("other-plan-row", "Other Plan Row");
        UUID runId = createExercise("other-plan-run", "Other Plan Run");
        WorkoutPlanView planA = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));
        WorkoutPlanView planB = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.startPlan(
                currentUser, planB.planId(), planA.days().get(0).planDayId(), "Asia/Singapore"));
    }

    @Test
    void shouldRejectAPlanDayIdForAFlatPlan() {
        UUID squatId = createExercise("flat-day-squat", "Flat Day Squat");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, new CreateWorkoutPlanRequest(
                "Flat Day Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null))));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> workoutService.startPlan(currentUser, plan.planId(), UUID.randomUUID(), "UTC"));

        assertEquals("This plan has no week.", exception.getMessage());
    }

    @Test
    void shouldRejectAnotherUsersStartOfAPlanDay() {
        UUID squatId = createExercise("owner-only-squat", "Owner Only Squat");
        UUID rowId = createExercise("owner-only-row", "Owner Only Row");
        UUID runId = createExercise("owner-only-run", "Owner Only Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.startPlan(
                otherUser, plan.planId(), plan.days().get(1).planDayId(), "Asia/Singapore"));
    }

    @Test
    void shouldKeepThePlanDayIdWhenASessionIsRepeated() {
        UUID squatId = createExercise("repeat-day-squat", "Repeat Day Squat");
        UUID rowId = createExercise("repeat-day-row", "Repeat Day Row");
        UUID runId = createExercise("repeat-day-run", "Repeat Day Run");
        WorkoutPlanView plan = workoutService.createPlan(currentUser, fourDayWeekRequest(squatId, rowId, runId));
        UUID thursdayPlanDayId = plan.days().get(1).planDayId();
        WorkoutSessionView started = workoutService.startPlan(currentUser, plan.planId(), thursdayPlanDayId, "Asia/Singapore");
        workoutService.upsertSet(currentUser, started.sessionId(), started.exercises().get(0).exerciseLogId(), 1,
                new com.fitsync.workout.dto.UpsertWorkoutExerciseSetRequest(null, 5, new java.math.BigDecimal("60.00"), null, null, 90, 7, null));
        workoutService.completeSession(currentUser, started.sessionId(), null);

        WorkoutSessionView repeated = workoutService.repeatSession(currentUser, started.sessionId());

        WorkoutSessionEntity repeatedEntity = workoutSessionRepository.findById(repeated.sessionId()).orElseThrow();
        assertEquals(thursdayPlanDayId, repeatedEntity.getPlanDayId());
    }

    private CreateWorkoutPlanRequest fourDayWeekRequest(UUID squatId, UUID rowId, UUID runId) {
        return new CreateWorkoutPlanRequest(
                "Four Day Week",
                "A plan with a week",
                GoalFocus.STRENGTH,
                WorkoutType.CUSTOM,
                WorkoutDifficulty.INTERMEDIATE,
                null,
                List.of(),
                List.of(
                        new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Upper Push", null, 60,
                                List.of(
                                        new WorkoutPlanExerciseInput(squatId, 1, 3, 5, null, null, null, 120, null, null),
                                        new WorkoutPlanExerciseInput(rowId, 2, 3, 8, null, null, null, 90, null, null))),
                        new WorkoutPlanDayInput(null, 4, SessionKind.CARDIO, "Easy Run", null, 30,
                                List.of(new WorkoutPlanExerciseInput(runId, 1, null, null, null, 1800, null, null, null, null))),
                        new WorkoutPlanDayInput(null, 5, SessionKind.STRENGTH, "Lower Push", null, 60,
                                List.of(
                                        new WorkoutPlanExerciseInput(squatId, 1, 4, 5, null, null, null, 120, null, null),
                                        new WorkoutPlanExerciseInput(rowId, 2, 4, 8, null, null, null, 90, null, null))),
                        new WorkoutPlanDayInput(null, 6, SessionKind.SPORT, "Badminton Night", SportActivity.BADMINTON, 60, List.of())));
    }

    private UUID createExercise(String code, String name) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.LEGS);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return exerciseRepository.save(entity).getExerciseId();
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock planWeekTestClock() {
            return Clock.fixed(THURSDAY_IN_SINGAPORE, ZoneOffset.UTC);
        }
    }
}
