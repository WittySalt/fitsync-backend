package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.MonthlyDayView;
import com.fitsync.workout.dto.MonthlyMuscleView;
import com.fitsync.workout.dto.MonthlyTrainingInsightView;
import com.fitsync.workout.dto.MonthlyWeekView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.entity.ExerciseEntity;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence-level coverage for the monthly training insight. Like the weekly insight's tests it
 * uses real repositories on H2 and builds sessions through the public service layer, with a clock
 * the tests can move.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@ActiveProfiles("test")
@Import(MonthlyTrainingInsightServiceTests.MovableClockConfig.class)
class MonthlyTrainingInsightServiceTests {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final String SGT_ID = "Asia/Singapore";

    @Autowired
    private MonthlyTrainingInsightService monthlyTrainingInsightService;

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

    @Autowired
    private MovableClock clock;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final CurrentUserPrincipal alice = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com", "Alice", "USER");
    private final CurrentUserPrincipal bob = new CurrentUserPrincipal(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), "bob@example.com", "Bob", "USER");
    private final CurrentUserPrincipal dave = new CurrentUserPrincipal(
            UUID.fromString("44444444-4444-4444-4444-444444444444"), "dave@example.com", "Dave", "USER");

    private UUID benchId;
    private UUID squatId;
    private UUID runId;

    @BeforeEach
    void clearDataAndSeed() {
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
        clock.set(Instant.parse("2026-10-01T04:00:00Z"));
        benchId = createExercise("bench", "Bench Press", MuscleGroup.CHEST);
        squatId = createExercise("squat", "Back Squat", MuscleGroup.LEGS);
        runId = createExercise("run", "Treadmill Run", MuscleGroup.CARDIO);
    }

    @Test
    void describesAMonthWithEveryKindOfSession() {
        seedAlice();
        seedBob();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        assertEquals(SGT_ID, result.timezone());
        assertEquals("2026-09", result.month());
        assertEquals(LocalDate.parse("2026-09-01"), result.monthStart());
        assertEquals(LocalDate.parse("2026-09-30"), result.monthEnd());
        assertEquals(LocalDate.parse("2026-10-01"), result.referenceDate());
        assertEquals(LocalDate.parse("2026-08-03"), result.firstSessionDate());

        List<MonthlyDayView> days = result.days();
        assertEquals(List.of("2026-09-01", "2026-09-02", "2026-09-03", "2026-09-06", "2026-09-07", "2026-09-08",
                "2026-09-09"), days.stream().map(day -> day.date().toString()).toList());

        MonthlyDayView strengthDay = days.get(0);
        assertEquals(SessionKind.STRENGTH, strengthDay.kind());
        assertEquals("Strength", strengthDay.label());
        assertEquals(1, strengthDay.sessions());
        assertEquals(50, strengthDay.minutes());
        assertEquals(List.of(MuscleGroup.CHEST, MuscleGroup.LEGS), strengthDay.muscles());

        MonthlyDayView cardioDay = days.get(1);
        assertEquals(SessionKind.CARDIO, cardioDay.kind());
        assertEquals("Cardio", cardioDay.label());
        assertEquals(35, cardioDay.minutes());
        assertEquals(List.of(MuscleGroup.CARDIO), cardioDay.muscles());

        MonthlyDayView mixedDay = days.get(2);
        assertEquals(SessionKind.SPORT, mixedDay.kind(), "the longest session of the day names it");
        assertEquals("Badminton", mixedDay.label());
        assertEquals(2, mixedDay.sessions());
        assertEquals(75, mixedDay.minutes());
        assertEquals(List.of(), mixedDay.muscles());

        MonthlyDayView mobilityDay = days.get(6);
        assertEquals(SessionKind.MOBILITY, mobilityDay.kind());
        assertEquals("Mobility", mobilityDay.label());
        assertEquals(10, mobilityDay.minutes());
    }

    @Test
    void flagsOnlySetsThatBeatAnEarlierBestAndNotAFirstTimeExercise() {
        seedAlice();
        seedBob();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        List<String> recordDays = result.days().stream()
                .filter(MonthlyDayView::record).map(day -> day.date().toString()).toList();
        // Sep 1 repeats the August best and first-logs the squat, so neither is a record; Sep 6
        // lifts the bench, and Sep 7 lifts the squat while only repeating the bench.
        assertEquals(List.of("2026-09-06", "2026-09-07"), recordDays);
        assertEquals(2, result.totals().records());
        assertEquals(1, result.previousTotals().records());
    }

    @Test
    void countsOneRecordPerExerciseAndDate() {
        createSession(alice, WorkoutType.STRENGTH, "2026-08-10T18:30", 30, null, List.of(lift(benchId, "50")));
        createSession(alice, WorkoutType.STRENGTH, "2026-09-10T08:00", 30, null, List.of(lift(benchId, "60")));
        createSession(alice, WorkoutType.STRENGTH, "2026-09-10T18:00", 30, null, List.of(lift(benchId, "65")));
        createSession(alice, WorkoutType.STRENGTH, "2026-09-11T18:00", 30, null,
                List.of(lift(benchId, "70"), lift(squatId, "90")));

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        // Sep 10 beats 50 twice but is one pair, Sep 11 beats the bench and first-logs the squat.
        assertEquals(2, result.totals().records());
        assertTrue(result.days().get(0).record());
        assertTrue(result.days().get(1).record());
    }

    @Test
    void breaksTheStreakOnARestDayAndCarriesItAcrossAWeekBoundary() {
        seedAlice();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        // Sep 1 to 3 is three days, then two rest days, then Sep 6 (Sunday) to 9 (Wednesday).
        assertEquals(4, result.totals().longestStreakDays());
        assertEquals(3, result.previousTotals().longestStreakDays());
    }

    @Test
    void totalsTheMonthAndTheMonthBefore() {
        seedAlice();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        assertEquals(8, result.totals().sessions());
        assertEquals(285, result.totals().minutes());
        assertEquals(4, result.previousTotals().sessions());
        assertEquals(135, result.previousTotals().minutes());
    }

    @Test
    void countsOnlyTheDatesInsideTheMonthInEachWeekRow() {
        seedAlice();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        List<MonthlyWeekView> weeks = result.weeks();
        assertEquals(List.of("2026-08-31", "2026-09-07", "2026-09-14", "2026-09-21", "2026-09-28"),
                weeks.stream().map(week -> week.weekStart().toString()).toList());
        assertEquals(LocalDate.parse("2026-09-06"), weeks.get(0).weekEnd());
        assertEquals(LocalDate.parse("2026-10-04"), weeks.get(4).weekEnd());

        // The first week starts on Aug 31, where Alice has a mobility session that is not counted.
        MonthlyWeekView first = weeks.get(0);
        assertEquals(5, first.sessions());
        assertEquals(205, first.minutes());
        assertEquals(2, first.strengthSessions());
        assertEquals(1, first.cardioSessions());
        assertEquals(1, first.mobilitySessions());
        assertEquals(1, first.sportSessions());
        assertTrue(first.complete());

        MonthlyWeekView second = weeks.get(1);
        assertEquals(3, second.sessions());
        assertEquals(80, second.minutes());
        assertEquals(1, second.strengthSessions());
        assertEquals(1, second.cardioSessions());
        assertEquals(1, second.mobilitySessions());
        assertEquals(0, second.sportSessions());

        for (MonthlyWeekView week : weeks) {
            assertEquals(week.sessions(), week.strengthSessions() + week.cardioSessions()
                    + week.mobilitySessions() + week.sportSessions());
        }
        assertEquals(0, weeks.get(2).sessions());
        assertEquals(0, weeks.get(4).sessions());
        assertTrue(weeks.get(3).complete());
        assertFalse(weeks.get(4).complete(), "the last week ends after the reference date");
    }

    @Test
    void countsSessionsPerMuscleGroupInGroupOrder() {
        seedAlice();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        List<MonthlyMuscleView> muscles = result.muscles();
        assertEquals(List.of(MuscleGroup.CHEST, MuscleGroup.LEGS, MuscleGroup.CARDIO),
                muscles.stream().map(MonthlyMuscleView::group).toList());
        assertEquals(List.of("Chest", "Legs", "Cardio"), muscles.stream().map(MonthlyMuscleView::label).toList());
        assertEquals(List.of(3, 2, 2), muscles.stream().map(MonthlyMuscleView::sessions).toList());
    }

    @Test
    void leavesPreviousTotalsEmptyWhenTheFirstSessionIsInTheMonth() {
        seedAlice();
        seedBob();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                bob, SGT_ID, "2026-09");

        assertEquals(LocalDate.parse("2026-09-10"), result.firstSessionDate());
        assertNull(result.previousTotals());
        assertEquals(1, result.totals().sessions());
        assertEquals(0, result.totals().records(), "Bob's only session is his first of that exercise");
    }

    @Test
    void ignoresAnotherUsersSessionsWhenFindingRecords() {
        seedAlice();
        seedBob();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-09");

        // Bob benched 300 kg on Sep 10; if that counted, Alice's Sep 6 and Sep 7 would not be records.
        assertEquals(2, result.totals().records());
        assertEquals(8, result.totals().sessions());
    }

    @Test
    void describesAnEmptyMonth() {
        seedAlice();

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, "2026-06");

        assertEquals(LocalDate.parse("2026-08-03"), result.firstSessionDate());
        assertTrue(result.days().isEmpty());
        assertEquals(5, result.weeks().size());
        assertTrue(result.weeks().stream().allMatch(week -> week.sessions() == 0 && week.minutes() == 0));
        assertEquals(0, result.totals().sessions());
        assertEquals(0, result.totals().longestStreakDays());
        assertNull(result.previousTotals());
        assertTrue(result.muscles().isEmpty());
    }

    @Test
    void describesAUserWithNoSessionsAtAll() {
        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, null);

        assertEquals("2026-10", result.month());
        assertNull(result.firstSessionDate());
        assertNull(result.previousTotals());
        assertTrue(result.days().isEmpty());
    }

    @Test
    void usesTheCurrentMonthWhenTheReferenceDateIsMidMonth() {
        seedAlice();
        clock.set(Instant.parse("2026-09-15T04:00:00Z"));

        MonthlyTrainingInsightView result = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                alice, SGT_ID, null);

        assertEquals("2026-09", result.month());
        assertEquals(LocalDate.parse("2026-09-15"), result.referenceDate());
        assertEquals(8, result.totals().sessions());
        assertEquals(4, result.previousTotals().sessions());
        List<Boolean> complete = result.weeks().stream().map(MonthlyWeekView::complete).toList();
        assertEquals(List.of(true, true, false, false, false), complete);
    }

    @Test
    void placesASessionByItsLocalCompletionDate() {
        // 23:45 on Sep 30 in Singapore, finished at 00:15 on Oct 1 there.
        createSession(dave, WorkoutType.CARDIO, "2026-09-30T23:45", 30, null, List.of());

        MonthlyTrainingInsightView september = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                dave, SGT_ID, "2026-09");
        MonthlyTrainingInsightView october = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                dave, SGT_ID, "2026-10");
        MonthlyTrainingInsightView septemberInUtc = monthlyTrainingInsightService.getMonthlyTrainingInsight(
                dave, "UTC", "2026-09");

        assertEquals(0, september.totals().sessions());
        assertEquals(1, october.totals().sessions());
        assertEquals(LocalDate.parse("2026-10-01"), october.days().get(0).date());
        assertEquals(LocalDate.parse("2026-10-01"), october.firstSessionDate());
        assertEquals(1, septemberInUtc.totals().sessions());
        assertEquals(LocalDate.parse("2026-09-30"), septemberInUtc.days().get(0).date());
    }

    @Test
    void rejectsAFutureAndAMalformedMonth() {
        assertThrows(IllegalArgumentException.class,
                () -> monthlyTrainingInsightService.getMonthlyTrainingInsight(alice, SGT_ID, "2026-11"));
        for (String month : List.of("2026-9", "2026-13", "september", "2026-09-01")) {
            assertThrows(IllegalArgumentException.class,
                    () -> monthlyTrainingInsightService.getMonthlyTrainingInsight(alice, SGT_ID, month), month);
        }
        assertThrows(IllegalArgumentException.class,
                () -> monthlyTrainingInsightService.getMonthlyTrainingInsight(alice, "Not/AZone", "2026-09"));
    }

    @Test
    void usesAFixedNumberOfQueriesWhateverTheNumberOfSessions() {
        seedAlice();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

        statistics.clear();
        monthlyTrainingInsightService.getMonthlyTrainingInsight(alice, SGT_ID, "2026-09");
        long statements = statistics.getPrepareStatementCount();

        // First-session date, sessions with their exercises, heaviest set per session and exercise,
        // and the best weight before the range.
        assertEquals(4, statements);
    }

    private void seedAlice() {
        // August: the first session, a record on Aug 5, and a mobility session on Aug 31 that sits
        // in the first week row of September without belonging to the month.
        createSession(alice, WorkoutType.STRENGTH, "2026-08-03T18:30", 40, null, List.of(lift(benchId, "50")));
        createSession(alice, WorkoutType.CARDIO, "2026-08-04T18:30", 30, null, List.of(plain(runId)));
        createSession(alice, WorkoutType.STRENGTH, "2026-08-05T18:30", 45, null, List.of(lift(benchId, "55")));
        createSession(alice, WorkoutType.MOBILITY, "2026-08-31T18:30", 20, null, List.of());

        createSession(alice, WorkoutType.STRENGTH, "2026-09-01T18:30", 50, null,
                List.of(lift(benchId, "55"), lift(squatId, "80")));
        createSession(alice, WorkoutType.CARDIO, "2026-09-02T18:30", 35, null, List.of(plain(runId)));
        createSession(alice, WorkoutType.SPORTS, "2026-09-03T12:00", 60, SportActivity.BADMINTON, List.of());
        createSession(alice, WorkoutType.MOBILITY, "2026-09-03T19:00", 15, null, List.of());
        createSession(alice, WorkoutType.STRENGTH, "2026-09-06T18:30", 45, null, List.of(lift(benchId, "60")));
        createSession(alice, WorkoutType.STRENGTH, "2026-09-07T18:30", 40, null,
                List.of(lift(benchId, "60"), lift(squatId, "85")));
        createSession(alice, WorkoutType.CARDIO, "2026-09-08T18:30", 30, null, List.of(plain(runId)));
        createSession(alice, WorkoutType.MOBILITY, "2026-09-09T18:30", 10, null, List.of());
    }

    private void seedBob() {
        createSession(bob, WorkoutType.STRENGTH, "2026-09-10T18:30", 60, null, List.of(lift(benchId, "300")));
    }

    private WorkoutExerciseLogInput lift(UUID exerciseId, String weightKg) {
        List<WorkoutExerciseSetLogInput> sets = List.of(
                new WorkoutExerciseSetLogInput(1, 5, new BigDecimal(weightKg), null, null, null, null, null),
                new WorkoutExerciseSetLogInput(2, 5, new BigDecimal(weightKg), null, null, null, null, null));
        return new WorkoutExerciseLogInput(exerciseId, 1, 2, 10, null, null, null, null, null, null, sets);
    }

    private WorkoutExerciseLogInput plain(UUID exerciseId) {
        return new WorkoutExerciseLogInput(exerciseId, 1, null, null, null, null, null, null, null, null, List.of());
    }

    /** Creates a completed session that started at the given Singapore wall-clock time. */
    private void createSession(CurrentUserPrincipal user,
                               WorkoutType workoutType,
                               String localStart,
                               int durationMinutes,
                               SportActivity sportActivity,
                               List<WorkoutExerciseLogInput> exercises) {
        Instant startedAt = LocalDateTime.parse(localStart).atZone(SGT).toInstant();
        Instant completedAt = startedAt.plusSeconds(durationMinutes * 60L);
        List<WorkoutExerciseLogInput> numbered = new ArrayList<>();
        for (WorkoutExerciseLogInput log : exercises) {
            numbered.add(new WorkoutExerciseLogInput(log.exerciseId(), numbered.size() + 1, log.performedSets(),
                    log.performedReps(), log.totalVolumeKg(), log.distanceKm(), log.durationSeconds(),
                    log.averageHeartRate(), log.maxHeartRate(), log.notes(), log.sets()));
        }
        workoutService.createSession(user, new UpsertWorkoutSessionRequest(
                null, "Session", workoutType, WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL,
                startedAt, completedAt, null, null, null, null, numbered, sportActivity));
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

    /** A clock whose instant a test can set, so one context serves a reference date in any month. */
    static final class MovableClock extends Clock {
        private volatile Instant instant = Instant.parse("2026-10-01T04:00:00Z");

        void set(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock monthlyTrainingInsightTestClock() {
            return new MovableClock();
        }
    }
}
