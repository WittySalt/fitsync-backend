package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.InsightPeriod;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CardioInsightView;
import com.fitsync.workout.dto.LiftProgressView;
import com.fitsync.workout.dto.MobilityInsightView;
import com.fitsync.workout.dto.SportActivityInsightView;
import com.fitsync.workout.dto.SportInsightView;
import com.fitsync.workout.dto.TrainingPageInsightView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.entity.ExerciseEntity;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-level coverage for the training page insight endpoint's computation. Mirrors the
 * setup style of {@link WeeklyTrainingInsightServiceTests}: real repositories and a real H2
 * database, driven through the public service layer so session validation stays realistic.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
@Import(TrainingPageInsightServiceTests.FixedClockConfig.class)
class TrainingPageInsightServiceTests {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    @Autowired
    private TrainingPageInsightService trainingPageInsightService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void computesFourWeekScenarioForPreviousWeekReferenceDate() {
        seedFourWeekScenario(currentUser);

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"));

        assertEquals("Asia/Singapore", result.timezone());
        assertEquals(LocalDate.parse("2026-09-28"), result.referenceDate());
        assertEquals(InsightPeriod.PREVIOUS_WEEK, result.period());
        assertEquals(LocalDate.parse("2026-09-21"), result.periodStart());
        assertEquals(LocalDate.parse("2026-09-27"), result.periodEnd());
        assertEquals(4, result.sessions());
        assertEquals(2, result.liftingSessions());
        assertEquals(0, new BigDecimal("1923.00").compareTo(result.volumePerSessionKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.earlierVolumePerSessionKg()));
        assertEquals(50, result.minutesPerLiftingSession());
        assertEquals(50, result.earlierMinutesPerLiftingSession());

        assertEquals("Dumbbell Bench Press", result.record().exerciseName());
        assertEquals(0, new BigDecimal("22.50").compareTo(result.record().weightKg()));
        assertEquals(LocalDate.parse("2026-09-21"), result.record().achievedOn());
        assertEquals(0, new BigDecimal("20.00").compareTo(result.record().previousBestKg()));

        assertEquals(MuscleGroup.CORE, result.mostTrained().muscleGroup());
        assertEquals(4, result.mostTrained().sessions());
        assertEquals(List.of("Plank Hold"), result.mostTrained().exerciseNames());

        assertEquals(3, result.lifts().size());
        LiftProgressView bench = result.lifts().get(0);
        assertEquals("Dumbbell Bench Press", bench.exerciseName());
        assertEquals(0, new BigDecimal("20.00").compareTo(bench.fromKg()));
        assertEquals(LocalDate.parse("2026-09-13"), bench.fromDate());
        assertEquals(0, new BigDecimal("22.50").compareTo(bench.toKg()));
        assertEquals(LocalDate.parse("2026-09-25"), bench.toDate());

        LiftProgressView row = result.lifts().get(1);
        assertEquals("Bent-Over Row", row.exerciseName());
        assertEquals(0, new BigDecimal("18.00").compareTo(row.fromKg()));
        assertEquals(0, new BigDecimal("20.00").compareTo(row.toKg()));

        LiftProgressView squat = result.lifts().get(2);
        assertEquals("Goblet Squat", squat.exerciseName());
        assertEquals(0, new BigDecimal("16.00").compareTo(squat.fromKg()));
        assertEquals(0, new BigDecimal("18.00").compareTo(squat.toKg()));
    }

    @Test
    void computesSameFourWeekScenarioForThisWeekReferenceDate() {
        seedFourWeekScenario(currentUser);

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(InsightPeriod.THIS_WEEK, result.period());
        assertEquals(LocalDate.parse("2026-09-21"), result.periodStart());
        assertEquals(LocalDate.parse("2026-09-27"), result.periodEnd());
        assertEquals(4, result.sessions());
        assertEquals(2, result.liftingSessions());
        assertEquals(0, new BigDecimal("1923.00").compareTo(result.volumePerSessionKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.earlierVolumePerSessionKg()));
        assertEquals(50, result.minutesPerLiftingSession());
        assertEquals(50, result.earlierMinutesPerLiftingSession());
        assertEquals("Dumbbell Bench Press", result.record().exerciseName());
        assertEquals(MuscleGroup.CORE, result.mostTrained().muscleGroup());
        assertEquals(4, result.mostTrained().sessions());
        assertEquals(3, result.lifts().size());
    }

    @Test
    void describesTheLastFullWeekWhileTheCurrentOneHasFewSessions() {
        seedFourWeekScenario(currentUser);

        // Tuesday 22 September: the current week holds one session, which says little about a week.
        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-22"));

        assertEquals(InsightPeriod.PREVIOUS_WEEK, result.period());
        assertEquals(LocalDate.parse("2026-09-14"), result.periodStart());
        assertEquals(LocalDate.parse("2026-09-20"), result.periodEnd());
        assertEquals(3, result.sessions());
        assertEquals(1, result.liftingSessions());
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.volumePerSessionKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.earlierVolumePerSessionKg()));
    }

    @Test
    void theThisWeekOnlyWindowReadsMondayToTheTuesdayAndNotTheLastFullWeek() {
        seedFourWeekScenario(currentUser);

        // Tuesday 22 September with one session this week: the coach reading still reads this week so far.
        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-22"), true);

        assertEquals(InsightPeriod.THIS_WEEK, result.period());
        assertEquals(LocalDate.parse("2026-09-21"), result.periodStart());
        assertEquals(LocalDate.parse("2026-09-22"), result.periodEnd());
        assertEquals(1, result.sessions());
    }

    @Test
    void theThisWeekOnlyWindowHasNothingToReadBeforeTheFirstSessionOfTheWeek() {
        seedFourWeekScenario(currentUser);

        // Monday 28 September: last week was trained, this week not yet.
        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"), true);

        assertEquals(InsightPeriod.NONE, result.period());
    }

    @Test
    void describesTheCurrentWeekOnceItHasThreeSessions() {
        seedFourWeekScenario(currentUser);

        // Friday 25 September: Monday, Thursday and Friday have been trained.
        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-25"));

        assertEquals(InsightPeriod.THIS_WEEK, result.period());
        assertEquals(LocalDate.parse("2026-09-21"), result.periodStart());
        assertEquals(LocalDate.parse("2026-09-25"), result.periodEnd());
        assertEquals(3, result.sessions());
        assertEquals(2, result.liftingSessions());
        assertEquals(0, new BigDecimal("1923.00").compareTo(result.volumePerSessionKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.earlierVolumePerSessionKg()));
    }

    @Test
    void ignoresAnotherUsersSessions() {
        UUID exerciseId = createExercise("cross-user-chest", "Cross User Chest", MuscleGroup.CHEST);
        createCompletedSession(otherUser, "Other User Session", WorkoutType.STRENGTH,
                LocalDateTime.of(2026, 9, 25, 18, 30), 50,
                List.of(weightedExerciseLog(exerciseId, 1, new BigDecimal("50.00"), 10)));

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(InsightPeriod.NONE, result.period());
        assertNull(result.periodStart());
        assertNull(result.periodEnd());
        assertEquals(0, result.sessions());
        assertEquals(0, result.liftingSessions());
        assertNull(result.volumePerSessionKg());
        assertNull(result.record());
        assertNull(result.mostTrained());
        assertTrue(result.lifts().isEmpty());
        assertNull(result.cardio());
        assertNull(result.mobility());
        assertNull(result.sport());
    }

    @Test
    void returnsNoneWithNullsForUserWithNoData() {
        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(InsightPeriod.NONE, result.period());
        assertNull(result.periodStart());
        assertNull(result.periodEnd());
        assertEquals(0, result.sessions());
        assertEquals(0, result.liftingSessions());
        assertNull(result.volumePerSessionKg());
        assertNull(result.earlierVolumePerSessionKg());
        assertNull(result.minutesPerLiftingSession());
        assertNull(result.earlierMinutesPerLiftingSession());
        assertNull(result.record());
        assertNull(result.mostTrained());
        assertTrue(result.lifts().isEmpty());
        assertNull(result.cardio());
        assertNull(result.mobility());
        assertNull(result.sport());
    }

    @Test
    void anExerciseLiftedForTheFirstTimeInThePeriodIsNotARecord() {
        UUID exerciseId = createExercise("first-time-chest", "First Time Press", MuscleGroup.CHEST);
        createStrengthSessionSingleExercise(currentUser, "2026-09-21", exerciseId, new BigDecimal("30.00"), new BigDecimal("900.00"));

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(InsightPeriod.THIS_WEEK, result.period());
        assertNull(result.record());
    }

    @Test
    void datesASessionByLocalDayInTheRequestedTimezone() {
        UUID exerciseId = createExercise("boundary-chest", "Boundary Chest", MuscleGroup.CHEST);
        // 2026-09-27T16:30:00Z is 2026-09-28 00:30 in Asia/Singapore (UTC+8, no DST) but is still
        // 2026-09-27 in UTC, so the same instant must land in two different local weeks.
        Instant startedAt = Instant.parse("2026-09-27T16:00:00Z");
        Instant completedAt = Instant.parse("2026-09-27T16:30:00Z");
        createCompletedSessionAtInstants(currentUser, "Boundary Session", WorkoutType.STRENGTH,
                startedAt, completedAt,
                List.of(weightedExerciseLog(exerciseId, 1, new BigDecimal("25.00"), 10)));

        TrainingPageInsightView sgtResult = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"));
        assertEquals(1, sgtResult.sessions(), "should count as 2026-09-28 in Asia/Singapore");
        assertEquals(LocalDate.parse("2026-09-28"), sgtResult.periodStart());

        TrainingPageInsightView utcResult = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "UTC", LocalDate.parse("2026-09-27"));
        assertEquals(1, utcResult.sessions(), "should count as 2026-09-27 in UTC");
    }

    @Test
    void computesCardioMobilityAndSportGroups() {
        // Period week (Monday to Thursday): two runs, one mobility session and three sport
        // sessions across two named activities plus one without an activity.
        createCardioSessionWithDistance(currentUser, "2026-09-21T06:00:00", 34, new BigDecimal("5.50"));
        createCardioSessionWithDistance(currentUser, "2026-09-21T18:00:00", 36, new BigDecimal("6.00"));
        createMobilitySession(currentUser, "2026-09-22T18:00:00", 20);
        createSportSession(currentUser, "2026-09-23T18:00:00", 60, SportActivity.BADMINTON);
        createSportSession(currentUser, "2026-09-23T19:30:00", 45, SportActivity.TENNIS);
        createSportSession(currentUser, "2026-09-24T18:00:00", 15, null);

        // Earlier week: one run and no mobility or sport session.
        createCardioSessionWithDistance(currentUser, "2026-09-16T18:00:00", 30, new BigDecimal("4.00"));

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-24"));

        assertEquals(InsightPeriod.THIS_WEEK, result.period());

        CardioInsightView cardio = result.cardio();
        assertEquals(2, cardio.sessions());
        assertEquals(1, cardio.earlierSessions());
        assertEquals(0, new BigDecimal("5.75").compareTo(cardio.distancePerSessionKm()));
        assertEquals(0, new BigDecimal("4.00").compareTo(cardio.earlierDistancePerSessionKm()));
        assertEquals(365, cardio.paceSecondsPerKm());
        assertEquals(450, cardio.earlierPaceSecondsPerKm());
        assertEquals(35, cardio.minutesPerSession());
        assertEquals(30, cardio.earlierMinutesPerSession());

        MobilityInsightView mobility = result.mobility();
        assertEquals(1, mobility.sessions());
        assertEquals(20, mobility.minutes());
        assertEquals(0, mobility.earlierSessions());
        assertEquals(0, mobility.earlierMinutes());

        SportInsightView sport = result.sport();
        assertEquals(3, sport.sessions());
        assertEquals(120, sport.minutes());
        assertEquals(3, sport.activities().size());
        SportActivityInsightView first = sport.activities().get(0);
        assertEquals(SportActivity.BADMINTON, first.activity());
        assertEquals("Badminton", first.label());
        assertEquals(60, first.minutes());
        SportActivityInsightView second = sport.activities().get(1);
        assertEquals(SportActivity.TENNIS, second.activity());
        assertEquals(45, second.minutes());
        SportActivityInsightView third = sport.activities().get(2);
        assertEquals(SportActivity.OTHER, third.activity());
        assertEquals(15, third.minutes());
    }

    @Test
    void cardioIsNullWhenThePeriodHasNoCardioSession() {
        createMobilitySession(currentUser, "2026-09-21T18:00:00", 20);
        createMobilitySession(currentUser, "2026-09-22T18:00:00", 20);
        createMobilitySession(currentUser, "2026-09-23T18:00:00", 20);

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-23"));

        assertEquals(InsightPeriod.THIS_WEEK, result.period());
        assertNull(result.cardio());
        assertNotNull(result.mobility());
    }

    @Test
    void cardioRunWithZeroDistanceDoesNotDivideByZero() {
        createCardioSessionWithDistance(currentUser, "2026-09-21T18:00:00", 20, BigDecimal.ZERO);

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-21"));

        CardioInsightView cardio = result.cardio();
        assertEquals(1, cardio.sessions());
        assertEquals(0, BigDecimal.ZERO.compareTo(cardio.distancePerSessionKm()));
        assertNull(cardio.paceSecondsPerKm());
        assertEquals(20, cardio.minutesPerSession());
    }

    @Test
    void cardioRunsWithoutADistanceGiveNullDistanceAndPaceButAMinutesValue() {
        createCardioSessionWithDistance(currentUser, "2026-09-21T18:00:00", 30, null);
        createCardioSessionWithDistance(currentUser, "2026-09-22T18:00:00", 40, null);

        TrainingPageInsightView result = trainingPageInsightService.getTrainingPageInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-22"));

        CardioInsightView cardio = result.cardio();
        assertEquals(2, cardio.sessions());
        assertNull(cardio.distancePerSessionKm());
        assertNull(cardio.paceSecondsPerKm());
        assertEquals(35, cardio.minutesPerSession());
    }

    /** Builds the shared 8-session, 4-week fixture described in the endpoint's acceptance scenario. */
    private void seedFourWeekScenario(CurrentUserPrincipal user) {
        UUID chestId = createExercise("scenario-chest", "Dumbbell Bench Press", MuscleGroup.CHEST);
        UUID legsId = createExercise("scenario-legs", "Goblet Squat", MuscleGroup.LEGS);
        UUID backId = createExercise("scenario-back", "Bent-Over Row", MuscleGroup.BACK);
        UUID coreId = createExercise("scenario-plank", "Plank Hold", MuscleGroup.CORE);
        UUID cardioId = createExercise("scenario-cardio", "Treadmill Run", MuscleGroup.CARDIO);
        UUID armsId = createExercise("scenario-arms", "Bicep Curl", MuscleGroup.ARMS);

        createStrengthSession(user, "2026-09-13",
                new BigDecimal("20.00"), new BigDecimal("600.00"),
                new BigDecimal("16.00"), new BigDecimal("576.00"),
                new BigDecimal("18.00"), new BigDecimal("540.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-14", cardioId, coreId);
        createStrengthSession(user, "2026-09-16",
                new BigDecimal("20.00"), new BigDecimal("600.00"),
                new BigDecimal("16.00"), new BigDecimal("576.00"),
                new BigDecimal("18.00"), new BigDecimal("540.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-18", cardioId, coreId);
        createStrengthSession(user, "2026-09-21",
                new BigDecimal("22.50"), new BigDecimal("675.00"),
                new BigDecimal("18.00"), new BigDecimal("648.00"),
                new BigDecimal("20.00"), new BigDecimal("600.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-24", cardioId, coreId);
        createStrengthSession(user, "2026-09-25",
                new BigDecimal("22.50"), new BigDecimal("675.00"),
                new BigDecimal("18.00"), new BigDecimal("648.00"),
                new BigDecimal("20.00"), new BigDecimal("600.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-26", cardioId, coreId);

        // An in-progress session must not influence any counter, record, or muscle-group signal.
        createInProgressSession(user, "2026-09-26", armsId);
    }

    private void createStrengthSession(CurrentUserPrincipal user,
                                       String localDate,
                                       BigDecimal chestWeight, BigDecimal chestVolume,
                                       BigDecimal legsWeight, BigDecimal legsVolume,
                                       BigDecimal backWeight, BigDecimal backVolume,
                                       UUID chestId, UUID legsId, UUID backId, UUID coreId) {
        // The domain layer recomputes totalVolumeKg from each set's reps * weight (see
        // WorkoutServiceImpl#deriveExerciseMetrics), so the reps per set are chosen here so that
        // reps * weight * 3 sets reproduces the endpoint spec's given log volume for each exercise.
        List<WorkoutExerciseLogInput> exercises = List.of(
                weightedExerciseLog(chestId, 1, chestWeight, repsForVolume(chestVolume, chestWeight)),
                weightedExerciseLog(legsId, 2, legsWeight, repsForVolume(legsVolume, legsWeight)),
                weightedExerciseLog(backId, 3, backWeight, repsForVolume(backVolume, backWeight)),
                bodyweightExerciseLog(coreId, 4));
        createCompletedSession(user, "Strength Day", WorkoutType.STRENGTH,
                LocalDateTime.parse(localDate + "T18:30:00"), 50, exercises);
    }

    private void createStrengthSessionSingleExercise(CurrentUserPrincipal user,
                                                      String localDate,
                                                      UUID exerciseId,
                                                      BigDecimal weightKg,
                                                      BigDecimal volumeKg) {
        createCompletedSession(user, "Solo Strength Day", WorkoutType.STRENGTH,
                LocalDateTime.parse(localDate + "T18:30:00"), 50,
                List.of(weightedExerciseLog(exerciseId, 1, weightKg, repsForVolume(volumeKg, weightKg))));
    }

    private void createCardioSession(CurrentUserPrincipal user, String localDate, UUID cardioId, UUID coreId) {
        List<WorkoutExerciseLogInput> exercises = List.of(
                bodyweightExerciseLog(cardioId, 1),
                bodyweightExerciseLog(coreId, 2));
        createCompletedSession(user, "Cardio Day", WorkoutType.CARDIO,
                LocalDateTime.parse(localDate + "T18:30:00"), 35, exercises);
    }

    /** A cardio session logged with its own distance directly on the session (K4), rather than
     * through an exercise log, at the given local date-time. A null distance leaves it unset. */
    private void createCardioSessionWithDistance(CurrentUserPrincipal user, String localDateTime, int durationMinutes, BigDecimal distanceKm) {
        Instant startedAt = LocalDateTime.parse(localDateTime).atZone(SGT).toInstant();
        Instant completedAt = startedAt.plusSeconds(durationMinutes * 60L);
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null, "Cardio Run", WorkoutType.CARDIO, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, completedAt, null, null, null, null, List.of(), null, distanceKm);
        workoutService.createSession(user, request);
    }

    private void createMobilitySession(CurrentUserPrincipal user, String localDateTime, int durationMinutes) {
        Instant startedAt = LocalDateTime.parse(localDateTime).atZone(SGT).toInstant();
        Instant completedAt = startedAt.plusSeconds(durationMinutes * 60L);
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null, "Mobility Session", WorkoutType.MOBILITY, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, completedAt, null, null, null, null, List.of());
        workoutService.createSession(user, request);
    }

    /** A sport session for the given activity, or without one (stored under OTHER) when null. */
    private void createSportSession(CurrentUserPrincipal user, String localDateTime, int durationMinutes, SportActivity sportActivity) {
        Instant startedAt = LocalDateTime.parse(localDateTime).atZone(SGT).toInstant();
        Instant completedAt = startedAt.plusSeconds(durationMinutes * 60L);
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null, "Sport Session", WorkoutType.SPORTS, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, completedAt, null, null, null, null, List.of(), sportActivity);
        workoutService.createSession(user, request);
    }

    private void createInProgressSession(CurrentUserPrincipal user, String localDate, UUID armsId) {
        Instant startedAt = LocalDateTime.parse(localDate + "T18:30:00").atZone(SGT).toInstant();
        List<WorkoutExerciseLogInput> exercises = List.of(weightedExerciseLog(armsId, 1, new BigDecimal("40.00"), 10));
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                "In Progress Day",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.IN_PROGRESS,
                WorkoutSource.MANUAL,
                startedAt,
                null,
                null,
                null,
                null,
                null,
                exercises);
        workoutService.createSession(user, request);
    }

    /** Three sets at the same weight, matching the endpoint spec's "each weighted exercise has
     * three sets" fixture description; the top-set weight used by the endpoint is simply that
     * weight. totalVolumeKg is left null on the request because the domain layer recomputes it
     * from the sets (see the comment in {@link #createStrengthSession}). */
    private WorkoutExerciseLogInput weightedExerciseLog(UUID exerciseId, int sequence, BigDecimal setWeightKg, int repsPerSet) {
        List<WorkoutExerciseSetLogInput> sets = List.of(
                new WorkoutExerciseSetLogInput(1, repsPerSet, setWeightKg, null, null, null, null, null),
                new WorkoutExerciseSetLogInput(2, repsPerSet, setWeightKg, null, null, null, null, null),
                new WorkoutExerciseSetLogInput(3, repsPerSet, setWeightKg, null, null, null, null, null));
        return new WorkoutExerciseLogInput(exerciseId, sequence, 3, repsPerSet * 3, null, null, null, null, null, null, sets);
    }

    /** The reps-per-set that reproduces the given total volume over 3 sets at the given weight:
     * volumeKg = weightKg * repsPerSet * 3 sets, solved for repsPerSet. */
    private int repsForVolume(BigDecimal volumeKg, BigDecimal weightKg) {
        return volumeKg.divide(weightKg.multiply(BigDecimal.valueOf(3)), 0, java.math.RoundingMode.UNNECESSARY).intValueExact();
    }

    private WorkoutExerciseLogInput bodyweightExerciseLog(UUID exerciseId, int sequence) {
        return new WorkoutExerciseLogInput(exerciseId, sequence, null, null, null, null, null, null, null, null, List.of());
    }

    private void createCompletedSession(CurrentUserPrincipal user,
                                        String name,
                                        WorkoutType workoutType,
                                        LocalDateTime localStart,
                                        int durationMinutes,
                                        List<WorkoutExerciseLogInput> exercises) {
        Instant startedAt = localStart.atZone(SGT).toInstant();
        Instant completedAt = startedAt.plusSeconds(durationMinutes * 60L);
        createCompletedSessionAtInstants(user, name, workoutType, startedAt, completedAt, exercises);
    }

    private void createCompletedSessionAtInstants(CurrentUserPrincipal user,
                                                   String name,
                                                   WorkoutType workoutType,
                                                   Instant startedAt,
                                                   Instant completedAt,
                                                   List<WorkoutExerciseLogInput> exercises) {
        UpsertWorkoutSessionRequest request = new UpsertWorkoutSessionRequest(
                null,
                name,
                workoutType,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.MANUAL,
                startedAt,
                completedAt,
                null,
                null,
                null,
                null,
                exercises);
        workoutService.createSession(user, request);
    }

    private UUID createExercise(String code, String name, MuscleGroup muscleGroup) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(muscleGroup);
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
        Clock trainingPageInsightTestClock() {
            return Clock.fixed(Instant.parse("2026-09-27T20:00:00Z"), ZoneOffset.UTC);
        }
    }
}
