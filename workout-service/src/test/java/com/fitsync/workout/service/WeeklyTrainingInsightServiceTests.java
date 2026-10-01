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

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.DayInsightView;
import com.fitsync.workout.dto.MuscleGroupInsightView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WeeklyTrainingInsightView;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-level coverage for the weekly training insight endpoint's computation. Mirrors the
 * setup style of {@link WorkoutServicePersistenceTests}: real repositories and a real H2 database,
 * driven through the public service layer so session/plan validation stays realistic.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
@Import(WeeklyTrainingInsightServiceTests.FixedClockConfig.class)
class WeeklyTrainingInsightServiceTests {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");

    @Autowired
    private WeeklyTrainingInsightService weeklyTrainingInsightService;

    @Autowired
    private WorkoutService workoutService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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
    void computesFourWeekScenarioForCurrentWeekReferenceDate() {
        seedFourWeekScenario(currentUser);

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals("Asia/Singapore", result.timezone());
        assertEquals(LocalDate.parse("2026-09-27"), result.referenceDate());
        assertEquals(LocalDate.parse("2026-09-21"), result.weekStart());
        assertEquals(LocalDate.parse("2026-09-27"), result.weekEnd());
        assertEquals(4, result.completedSessions());
        assertEquals(170, result.totalMinutes());
        assertEquals(3, result.previousWeekCompletedSessions());
        assertEquals(120, result.previousWeekTotalMinutes());

        assertEquals(0, new BigDecimal("3846.00").compareTo(result.volume().thisWeekKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.volume().previousWeekKg()));
        assertEquals(0, new BigDecimal("1716.00").compareTo(result.volume().weekBeforePreviousKg()));
        assertEquals(2, result.volume().thisWeekSessionsWithVolume());

        List<MuscleGroup> order = result.muscleGroups().stream().map(MuscleGroupInsightView::muscleGroup).toList();
        assertEquals(List.of(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.CORE, MuscleGroup.LEGS, MuscleGroup.CARDIO), order);

        MuscleGroupInsightView chest = groupView(result, MuscleGroup.CHEST);
        assertEquals(2, chest.sessionsThisWeek());
        assertTrue(chest.inActivePlan());
        assertFalse(chest.trainedYesterday());
        assertEquals(LocalDate.parse("2026-09-25"), chest.lastTrainedOn());
        assertEquals(0, chest.consecutiveSessions());

        MuscleGroupInsightView core = groupView(result, MuscleGroup.CORE);
        assertEquals(4, core.sessionsThisWeek());
        assertTrue(core.trainedYesterday());
        assertEquals(LocalDate.parse("2026-09-26"), core.lastTrainedOn());
        assertEquals(8, core.consecutiveSessions());
        assertEquals(List.of("Plank"), core.exerciseNames());

        MuscleGroupInsightView cardio = groupView(result, MuscleGroup.CARDIO);
        assertEquals(2, cardio.sessionsThisWeek());
        assertFalse(cardio.inActivePlan());
        assertTrue(cardio.trainedYesterday());
        assertEquals(1, cardio.consecutiveSessions());

        assertTrue(result.muscleGroups().stream().noneMatch(g -> g.muscleGroup() == MuscleGroup.ARMS));
        assertTrue(result.muscleGroups().stream().noneMatch(g -> g.muscleGroup() == MuscleGroup.SHOULDERS));
    }

    @Test
    void computesNewWeekForMondayReferenceDate() {
        seedFourWeekScenario(currentUser);

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"));

        assertEquals(0, result.completedSessions());
        assertEquals(0, result.totalMinutes());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.volume().thisWeekKg()));
        assertEquals(0, new BigDecimal("3846.00").compareTo(result.volume().previousWeekKg()));

        for (MuscleGroupInsightView group : result.muscleGroups()) {
            assertEquals(0, group.sessionsThisWeek());
        }
        assertEquals(List.of(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.CORE, MuscleGroup.LEGS, MuscleGroup.CARDIO),
                result.muscleGroups().stream().map(MuscleGroupInsightView::muscleGroup).toList());
        assertEquals(LocalDate.parse("2026-09-26"), groupView(result, MuscleGroup.CORE).lastTrainedOn());
    }

    @Test
    void ignoresAnotherUsersSessions() {
        UUID exerciseId = createExercise("cross-user-chest", "Cross User Chest", MuscleGroup.CHEST);
        createCompletedSession(otherUser, "Other User Session", WorkoutType.STRENGTH,
                LocalDateTime.of(2026, 9, 25, 18, 30), 50,
                List.of(exerciseLog(exerciseId, 1, new BigDecimal("500.00"))));

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(0, result.completedSessions());
        assertEquals(0, result.previousWeekCompletedSessions());
        assertTrue(result.muscleGroups().isEmpty());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.volume().thisWeekKg()));
    }

    @Test
    void returnsZerosAndEmptyGroupsForUserWithNoData() {
        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertEquals(0, result.completedSessions());
        assertEquals(0, result.totalMinutes());
        assertEquals(0, result.previousWeekCompletedSessions());
        assertEquals(0, result.previousWeekTotalMinutes());
        assertTrue(result.muscleGroups().isEmpty());
        assertEquals(0, new BigDecimal("0.00").compareTo(result.volume().thisWeekKg()));
        assertEquals(0, new BigDecimal("0.00").compareTo(result.volume().previousWeekKg()));
        assertEquals(0, new BigDecimal("0.00").compareTo(result.volume().weekBeforePreviousKg()));
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
                List.of(exerciseLog(exerciseId, 1, new BigDecimal("100.00"))));

        WeeklyTrainingInsightView sgtResult = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"));
        assertEquals(1, sgtResult.completedSessions(), "should count as 2026-09-28 in Asia/Singapore");

        WeeklyTrainingInsightView utcResult = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "UTC", LocalDate.parse("2026-09-27"));
        assertEquals(1, utcResult.completedSessions(), "should count as 2026-09-27 in UTC");
    }

    @Test
    void weekByDayReflectsAnActivePlanWeekAndItsFulfilment() {
        WorkoutPlanView plan = createPlanWithWeek(currentUser, List.of(
                workoutPlanDayInput(1, SessionKind.STRENGTH, "Full body", null, 50),
                workoutPlanDayInput(4, SessionKind.CARDIO, "Treadmill run", null, 35)));
        UUID mondayPlanDayId = plan.days().get(0).planDayId();
        UUID thursdayPlanDayId = plan.days().get(1).planDayId();

        saveDaySession(currentUser, SessionKind.STRENGTH, WorkoutType.STRENGTH, WorkoutSessionStatus.COMPLETED,
                LocalDate.parse("2026-09-28"), 50, mondayPlanDayId, null);

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-29"));

        DayInsightView monday = result.days().get(0);
        assertEquals(DayKind.STRENGTH, monday.kind());
        assertEquals(DaySource.DONE, monday.source());
        assertTrue(monday.planned().fulfilled());

        DayInsightView thursday = result.days().get(3);
        assertEquals(DayKind.CARDIO, thursday.kind());
        assertEquals(DaySource.PLAN, thursday.source());
        assertEquals(thursdayPlanDayId, thursday.planned().planDayId());

        assertEquals(DayKind.REST, result.today().kind());
        assertEquals("Treadmill run", result.nextTraining().title());
        assertEquals(LocalDate.parse("2026-10-01"), result.nextTraining().date());
    }

    @Test
    void weekByDayIgnoresAnotherUsersPlanAndSessions() {
        WorkoutPlanView otherPlan = createPlanWithWeek(otherUser, List.of(
                workoutPlanDayInput(2, SessionKind.CARDIO, "Other user's run", null, 30)));
        saveDaySession(otherUser, SessionKind.CARDIO, WorkoutType.CARDIO, WorkoutSessionStatus.COMPLETED,
                LocalDate.parse("2026-09-29"), 30, otherPlan.days().get(0).planDayId(), null);

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-29"));

        for (DayInsightView day : result.days()) {
            assertNull(day.planned());
            assertTrue(day.sessions().isEmpty());
        }
        assertEquals(DayKind.OPEN, result.today().kind());
        assertEquals(DaySource.NONE, result.today().source());
    }

    @Test
    void datesADayKindSessionByLocalDayInTheRequestedTimezone() {
        // Same boundary instant as datesASessionByLocalDayInTheRequestedTimezone above: it is
        // 2026-09-28 in Asia/Singapore but still 2026-09-27 in UTC.
        WorkoutSessionEntity entity = new WorkoutSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(currentUser.userId());
        entity.setName("Boundary Cardio");
        entity.setWorkoutType(WorkoutType.CARDIO);
        entity.setKind(SessionKind.CARDIO);
        entity.setStatus(WorkoutSessionStatus.COMPLETED);
        entity.setSource(WorkoutSource.MANUAL);
        entity.setStartedAt(Instant.parse("2026-09-27T16:00:00Z"));
        entity.setCompletedAt(Instant.parse("2026-09-27T16:30:00Z"));
        entity.setDurationMinutes(30);
        workoutSessionRepository.save(entity);

        WeeklyTrainingInsightView sgtResult = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-28"));
        assertEquals(DayKind.CARDIO, sgtResult.days().get(0).kind(), "2026-09-28 is a Monday in Asia/Singapore");

        WeeklyTrainingInsightView utcResult = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "UTC", LocalDate.parse("2026-09-27"));
        assertEquals(DayKind.CARDIO, utcResult.days().get(6).kind(), "2026-09-27 is a Sunday in UTC");
    }

    private WorkoutPlanView createPlanWithWeek(CurrentUserPrincipal user, List<WorkoutPlanDayInput> days) {
        WorkoutPlanView plan = workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                "Week Plan",
                "Weekly schedule for day-kinds coverage",
                GoalFocus.GENERAL_FITNESS,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                List.of(),
                days));
        return workoutService.activatePlan(user, plan.planId());
    }

    private WorkoutPlanDayInput workoutPlanDayInput(int dayOfWeek,
                                                    SessionKind kind,
                                                    String title,
                                                    SportActivity sportActivity,
                                                    Integer estimatedMinutes) {
        return new WorkoutPlanDayInput(null, dayOfWeek, kind, title, sportActivity, estimatedMinutes, List.of());
    }

    private void saveDaySession(CurrentUserPrincipal user,
                                SessionKind kind,
                                WorkoutType workoutType,
                                WorkoutSessionStatus status,
                                LocalDate localDate,
                                Integer durationMinutes,
                                UUID planDayId,
                                SportActivity sportActivity) {
        Instant startedAt = localDate.atTime(18, 30).atZone(SGT).toInstant();
        WorkoutSessionEntity entity = new WorkoutSessionEntity();
        entity.setSessionId(UUID.randomUUID());
        entity.setUserId(user.userId());
        entity.setName("Session");
        entity.setWorkoutType(workoutType);
        entity.setKind(kind);
        entity.setSportActivity(sportActivity);
        entity.setPlanDayId(planDayId);
        entity.setStatus(status);
        entity.setSource(WorkoutSource.MANUAL);
        entity.setStartedAt(startedAt);
        entity.setCompletedAt(status == WorkoutSessionStatus.COMPLETED
                ? startedAt.plusSeconds(durationMinutes * 60L) : null);
        entity.setDurationMinutes(durationMinutes);
        workoutSessionRepository.save(entity);
    }

    private MuscleGroupInsightView groupView(WeeklyTrainingInsightView result, MuscleGroup group) {
        return result.muscleGroups().stream()
                .filter(g -> g.muscleGroup() == group)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected muscle group " + group + " to be present"));
    }

    /** Builds the shared 8-session, 4-week fixture described in the endpoint's acceptance scenario. */
    private void seedFourWeekScenario(CurrentUserPrincipal user) {
        UUID chestId = createExercise("scenario-chest", "Chest Press", MuscleGroup.CHEST);
        UUID legsId = createExercise("scenario-legs", "Leg Press", MuscleGroup.LEGS);
        UUID backId = createExercise("scenario-back", "Barbell Row", MuscleGroup.BACK);
        UUID coreId = createExercise("scenario-plank", "Plank", MuscleGroup.CORE);
        UUID cardioId = createExercise("scenario-cardio", "Treadmill Run", MuscleGroup.CARDIO);
        UUID armsId = createExercise("scenario-arms", "Bicep Curl", MuscleGroup.ARMS);
        UUID shouldersId = createExercise("scenario-shoulders", "Shoulder Press", MuscleGroup.SHOULDERS);

        createStrengthSession(user, "2026-09-13", new BigDecimal("600.00"), new BigDecimal("576.00"), new BigDecimal("540.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-14", cardioId, coreId);
        createStrengthSession(user, "2026-09-16", new BigDecimal("600.00"), new BigDecimal("576.00"), new BigDecimal("540.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-18", cardioId, coreId);
        createStrengthSession(user, "2026-09-21", new BigDecimal("675.00"), new BigDecimal("648.00"), new BigDecimal("600.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-24", cardioId, coreId);
        createStrengthSession(user, "2026-09-25", new BigDecimal("675.00"), new BigDecimal("648.00"), new BigDecimal("600.00"),
                chestId, legsId, backId, coreId);
        createCardioSession(user, "2026-09-26", cardioId, coreId);

        // An in-progress session must not influence any counter or the muscle-group set.
        createInProgressSession(user, "2026-09-26", armsId, shouldersId);

        WorkoutPlanExerciseInput chestPlanExercise = planExercise(chestId, 1);
        WorkoutPlanExerciseInput legsPlanExercise = planExercise(legsId, 2);
        WorkoutPlanExerciseInput backPlanExercise = planExercise(backId, 3);
        WorkoutPlanExerciseInput corePlanExercise = planExercise(coreId, 4);
        WorkoutPlanView plan = workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                "Full Body Plan",
                "Active plan for weekly insight coverage",
                GoalFocus.GENERAL_FITNESS,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.INTERMEDIATE,
                60,
                List.of(chestPlanExercise, legsPlanExercise, backPlanExercise, corePlanExercise)));
        workoutService.activatePlan(user, plan.planId());
    }

    private WorkoutPlanExerciseInput planExercise(UUID exerciseId, int sequence) {
        return new WorkoutPlanExerciseInput(exerciseId, sequence, null, null, null, null, null, null, null, null);
    }

    private void createStrengthSession(CurrentUserPrincipal user,
                                       String localDate,
                                       BigDecimal chestVolume,
                                       BigDecimal legsVolume,
                                       BigDecimal backVolume,
                                       UUID chestId,
                                       UUID legsId,
                                       UUID backId,
                                       UUID coreId) {
        List<WorkoutExerciseLogInput> exercises = List.of(
                exerciseLog(chestId, 1, chestVolume),
                exerciseLog(legsId, 2, legsVolume),
                exerciseLog(backId, 3, backVolume),
                exerciseLog(coreId, 4, null));
        createCompletedSession(user, "Strength Day", WorkoutType.STRENGTH,
                LocalDateTime.parse(localDate + "T18:30:00"), 50, exercises);
    }

    private void createCardioSession(CurrentUserPrincipal user, String localDate, UUID cardioId, UUID coreId) {
        List<WorkoutExerciseLogInput> exercises = List.of(
                exerciseLog(cardioId, 1, null),
                exerciseLog(coreId, 2, null));
        createCompletedSession(user, "Cardio Day", WorkoutType.CARDIO,
                LocalDateTime.parse(localDate + "T18:30:00"), 35, exercises);
    }

    private void createInProgressSession(CurrentUserPrincipal user, String localDate, UUID armsId, UUID shouldersId) {
        Instant startedAt = LocalDateTime.parse(localDate + "T18:30:00").atZone(SGT).toInstant();
        List<WorkoutExerciseLogInput> exercises = List.of(
                exerciseLog(armsId, 1, null),
                exerciseLog(shouldersId, 2, null));
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

    private WorkoutExerciseLogInput exerciseLog(UUID exerciseId, int sequence, BigDecimal totalVolumeKg) {
        return new WorkoutExerciseLogInput(
                exerciseId, sequence, null, null, totalVolumeKg, null, null, null, null, null, List.of());
    }

    @Test
    void lastOfKindNamesTheSessionAndItsFirstSixExercisesAndCostsOneQuery() {
        List<WorkoutExerciseLogInput> exercises = new java.util.ArrayList<>();
        for (int index = 1; index <= 7; index++) {
            UUID exerciseId = createExercise("last-kind-" + index, "Lift " + index, MuscleGroup.CHEST);
            exercises.add(exerciseLog(exerciseId, index, null));
        }
        createCompletedSession(currentUser, "Earlier strength", WorkoutType.STRENGTH,
                LocalDateTime.parse("2026-09-24T18:30:00"), 50, exercises);
        createCompletedSession(currentUser, "Today strength", WorkoutType.STRENGTH,
                LocalDateTime.parse("2026-09-27T10:00:00"), 50, exercises);
        UUID earlierId = workoutSessionRepository.findAll().stream()
                .filter(session -> "Earlier strength".equals(session.getName()))
                .findFirst().orElseThrow().getSessionId();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));
        long statements = statistics.getPrepareStatementCount();

        assertEquals(earlierId, result.today().lastOfKind().sessionId());
        assertEquals(List.of("Lift 1", "Lift 2", "Lift 3", "Lift 4", "Lift 5", "Lift 6"),
                result.today().lastOfKind().exerciseNames());
        // The three the weekly insight always runs, plus one bounded read of the names.
        assertEquals(4, statements);
    }

    @Test
    void lastOfKindOfASessionWithoutExercisesHasNoNames() {
        createCompletedSession(currentUser, "Earlier cardio", WorkoutType.CARDIO,
                LocalDateTime.parse("2026-09-24T18:30:00"), 30, List.of());
        createCompletedSession(currentUser, "Today cardio", WorkoutType.CARDIO,
                LocalDateTime.parse("2026-09-27T10:00:00"), 30, List.of());

        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertTrue(result.today().lastOfKind().sessionId() != null);
        assertEquals(List.of(), result.today().lastOfKind().exerciseNames());
    }

    @Test
    void lastOfKindStaysNullWithoutAnEarlierSessionOfTheKindAndAddsNoQuery() {
        createCompletedSession(currentUser, "Only today", WorkoutType.CARDIO,
                LocalDateTime.parse("2026-09-27T10:00:00"), 30, List.of());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        WeeklyTrainingInsightView result = weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, "Asia/Singapore", LocalDate.parse("2026-09-27"));

        assertNull(result.today().lastOfKind());
        assertEquals(3, statistics.getPrepareStatementCount());
    }

    @Test
    void usualStartTimeIsTheSharedHalfHourWhenThreeSessionsAgree() {
        seedStartTimes("2026-09-21T18:30", "2026-09-23T18:30", "2026-09-25T18:30");
        assertEquals("18:30", usualStartTime("Asia/Singapore"));
    }

    @Test
    void usualStartTimeRoundsTheMedianToTheNearestHalfHour() {
        seedStartTimes("2026-09-21T18:10", "2026-09-23T18:40", "2026-09-25T18:50");
        assertEquals("18:30", usualStartTime("Asia/Singapore"));
    }

    @Test
    void usualStartTimeIsNullBelowThreeSessions() {
        seedStartTimes("2026-09-21T18:30", "2026-09-23T18:30");
        assertNull(usualStartTime("Asia/Singapore"));
    }

    @Test
    void usualStartTimeIsNullWhenTheSessionsDoNotAgree() {
        seedStartTimes("2026-09-20T06:00", "2026-09-22T12:00", "2026-09-24T18:00", "2026-09-26T22:00");
        assertNull(usualStartTime("Asia/Singapore"));
    }

    @Test
    void usualStartTimeIgnoresSessionsOlderThanTwentyEightDays() {
        // 2026-09-27 is the reference day, so the window starts on 2026-08-31; 08-29 is 29 days back.
        seedStartTimes("2026-08-29T18:30", "2026-09-23T18:30", "2026-09-25T18:30");
        assertNull(usualStartTime("Asia/Singapore"));
        seedStartTimes("2026-08-31T18:30");
        assertEquals("18:30", usualStartTime("Asia/Singapore"));
    }

    @Test
    void usualStartTimeFollowsTheRequestedTimezone() {
        Instant tenThirtyUtc = Instant.parse("2026-09-21T10:30:00Z");
        for (int day = 0; day < 3; day++) {
            Instant start = tenThirtyUtc.plusSeconds(day * 2L * 86400);
            createCompletedSessionAtInstants(currentUser, "Zone " + day, WorkoutType.CARDIO,
                    start, start.plusSeconds(1800), List.of());
        }
        assertEquals("18:30", usualStartTime("Asia/Singapore"));
        assertEquals("10:30", usualStartTime("UTC"));
    }

    @Test
    void usualStartTimeAddsNoQueryToTheWeeklyInsight() {
        seedStartTimes("2026-09-21T18:30", "2026-09-23T18:30", "2026-09-25T18:30");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        weeklyTrainingInsightService.getWeeklyTrainingInsight(currentUser, "Asia/Singapore",
                LocalDate.parse("2026-09-27"));
        long statements = statistics.getPrepareStatementCount();

        // Active plan, completed sessions, and the wider window the day-by-day
        // breakdown needs; the usual start time is worked out from the sessions already loaded.
        assertEquals(3, statements);
    }

    private String usualStartTime(String timezone) {
        return weeklyTrainingInsightService.getWeeklyTrainingInsight(
                currentUser, timezone, LocalDate.parse("2026-09-27")).usualStartTime();
    }

    private void seedStartTimes(String... localStarts) {
        for (String localStart : localStarts) {
            createCompletedSession(currentUser, "Usual " + localStart, WorkoutType.CARDIO,
                    LocalDateTime.parse(localStart), 30, List.of());
        }
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
        Clock weeklyTrainingInsightTestClock() {
            return Clock.fixed(Instant.parse("2026-09-27T20:00:00Z"), ZoneOffset.UTC);
        }
    }
}
