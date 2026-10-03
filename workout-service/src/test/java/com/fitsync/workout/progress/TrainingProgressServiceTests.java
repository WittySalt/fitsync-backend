package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WeeklyTrainingInsightView;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseSecondaryMuscleEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.service.WeeklyTrainingInsightService;
import com.fitsync.workout.service.WorkoutService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The progress reads against a real H2 database: the queries run, sessions are placed by the caller's
 * zone, records count the history before the window, and another user's data never shows.
 */
@SpringBootTest(properties = {
        "fitsync.workout.catalog.free-exercise-db.resource=classpath:fixtures/free-exercise-db-exercises.json",
        "fitsync.workout.catalog.free-exercise-db.image-source-directory=src/test/resources/fixtures/free-exercise-db-images"
})
@ActiveProfiles("test")
@Import(TrainingProgressServiceTests.FixedClockConfig.class)
class TrainingProgressServiceTests {

    private static final ZoneId SGT = ZoneId.of("Asia/Singapore");
    private static final LocalDate FROM = LocalDate.parse("2026-09-07");
    private static final LocalDate TO = LocalDate.parse("2026-10-04");
    private static final ProgressPeriod PERIOD = new ProgressPeriod(FROM, TO);

    @Autowired
    private TrainingProgressService trainingProgressService;

    @Autowired
    private WeeklyTrainingInsightService weeklyTrainingInsightService;

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

    private final CurrentUserPrincipal alice = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"), "alice@example.com", "Alice", "USER");
    private final CurrentUserPrincipal bob = new CurrentUserPrincipal(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), "bob@example.com", "Bob", "USER");

    private UUID deadliftId;

    @BeforeEach
    void seed() {
        outboxEventRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();

        deadliftId = createDeadlift();
        strength(alice, "2026-07-01T18:30:00", "80");
        strength(alice, "2026-09-08T18:30:00", "85");
        strength(alice, "2026-09-15T18:30:00", "90");
        strength(alice, "2026-09-22T18:30:00", "92.5");
        // 01:00 on Monday 7 Sept in Singapore, still Sunday 6 Sept in UTC.
        cardio(alice, Instant.parse("2026-09-06T17:00:00Z"), 45);
        sport(alice, "2026-09-27T15:00:00", 60, SportActivity.BADMINTON);
        // Bob's heavier deadlift must never reach Alice's figures.
        strength(bob, "2026-09-10T18:30:00", "200");
    }

    @Test
    void readsTheTrainingPageInTheCallersZoneWithRecordsFromEarlierHistory() {
        TrainingProgressView view = trainingProgressService.getTraining(alice, SGT, PERIOD, new BigDecimal("80"));

        assertEquals(5, view.hero().sessions().value());
        assertEquals(0, view.hero().sessions().previous());
        assertEquals(3, view.hero().records().value());
        assertEquals(List.of("Deadlift"), view.hero().records().exercises());
        assertEquals(1, view.lifts().size());
        assertEquals(List.of(true, true, true), view.lifts().get(0).e1rm().stream().map(p -> p.isRecord()).toList());
        assertEquals(new BigDecimal("5.9"), view.records().get(2).delta());
        assertEquals(TrainingProgressView.Level.NOVICE, view.lifts().get(0).relative().level());
        // Nine deadlift sets over four weeks: 2.25 a week for the back, half of that for the legs.
        assertEquals(new BigDecimal("2.3"), setsPerWeek(view, MuscleGroup.BACK));
        assertEquals(new BigDecimal("1.1"), setsPerWeek(view, MuscleGroup.LEGS));
        assertTrue(view.activities().stream().anyMatch(a -> a.key().equals("SPORT:BADMINTON")));
        assertEquals(LocalDate.parse("2026-09-07"), view.calendar().get(0).date());
    }

    @Test
    void theSameSessionsReadInUtcPutTheLateSessionInThePeriodBefore() {
        TrainingProgressView view = trainingProgressService.getTraining(alice, ZoneOffset.UTC, PERIOD, null);

        assertEquals(4, view.hero().sessions().value());
        assertEquals(1, view.hero().sessions().previous());
        assertEquals(45, view.hero().minutes().previous());
        assertTrue(view.lifts().get(0).relative() == null);
    }

    @Test
    void readsOneActivityAndOneExercise() {
        ActivityProgressView badminton = trainingProgressService.getActivity(alice, SGT, ActivityKey.parse("SPORT:BADMINTON"), PERIOD);
        assertEquals(1, badminton.tiles().sessions());
        assertEquals(60, badminton.tiles().minutes());
        assertEquals("Badminton", badminton.name());

        ExerciseProgressView deadlift = trainingProgressService.getExercise(alice, SGT, deadliftId, PERIOD);
        assertEquals(4, deadlift.sessions());
        assertEquals(LocalDate.parse("2026-07-01"), deadlift.firstSeen());
        assertEquals(3, deadlift.tiles().records());
        assertEquals(List.of(MuscleGroup.LEGS), deadlift.secondaryGroups());
        assertEquals(3, deadlift.history().size());
    }

    @Test
    void anotherUsersCustomExerciseIsNotFound() {
        ExerciseEntity custom = exercise("bob-only", "Bob's lift", MuscleGroup.ARMS);
        custom.setOwnerUserId(bob.userId());
        custom.setCustom(true);
        UUID customId = exerciseRepository.save(custom).getExerciseId();

        assertThrows(ResourceNotFoundException.class,
                () -> trainingProgressService.getExercise(alice, SGT, customId, PERIOD));
        assertFalse(trainingProgressService.getExercise(bob, SGT, deadliftId, PERIOD).history().isEmpty());
    }

    @Test
    void theWeeklyInsightCarriesTheCardLiftsAndActivityLines() {
        WeeklyTrainingInsightView weekly = weeklyTrainingInsightService.getWeeklyTrainingInsight(alice,
                "Asia/Singapore", LocalDate.parse("2026-10-04"));

        assertEquals(1, weekly.lifts().size());
        assertEquals("Deadlift", weekly.lifts().get(0).name());
        assertEquals(new BigDecimal("99.2"), weekly.lifts().get(0).e1rmStart());
        assertEquals(new BigDecimal("107.9"), weekly.lifts().get(0).e1rmNow());
        assertEquals(9, weekly.lifts().get(0).changePct());
        assertTrue(weekly.activitySummaries().stream().anyMatch(a -> a.key().equals("STRENGTH")
                && a.summary().size() == 3));
    }

    private static BigDecimal setsPerWeek(TrainingProgressView view, MuscleGroup group) {
        return view.muscles().groups().stream().filter(g -> g.group() == group).findFirst().orElseThrow()
                .setsPerWeek();
    }

    private UUID createDeadlift() {
        ExerciseEntity entity = exercise("progress-deadlift", "Deadlift", MuscleGroup.BACK);
        ExerciseSecondaryMuscleEntity legs = new ExerciseSecondaryMuscleEntity();
        legs.setSecondaryMuscleId(UUID.randomUUID());
        legs.setExercise(entity);
        legs.setMuscleGroup(MuscleGroup.LEGS);
        entity.getSecondaryMuscles().add(legs);
        return exerciseRepository.save(entity).getExerciseId();
    }

    private ExerciseEntity exercise(String code, String name, MuscleGroup group) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName(name);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(group);
        entity.setEquipment(ExerciseEquipment.BARBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(false);
        return entity;
    }

    private void strength(CurrentUserPrincipal user, String localStart, String weight) {
        BigDecimal kg = new BigDecimal(weight);
        List<WorkoutExerciseSetLogInput> sets = List.of(
                new WorkoutExerciseSetLogInput(1, 5, kg, null, null, null, null, null),
                new WorkoutExerciseSetLogInput(2, 5, kg, null, null, null, null, null),
                new WorkoutExerciseSetLogInput(3, 5, kg, null, null, null, null, null));
        Instant startedAt = LocalDateTime.parse(localStart).atZone(SGT).toInstant();
        workoutService.createSession(user, new UpsertWorkoutSessionRequest(null, "Pull day", WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL, startedAt, startedAt.plusSeconds(50 * 60L),
                null, null, null, null,
                List.of(new WorkoutExerciseLogInput(deadliftId, 1, 3, 15, null, null, null, null, null, null, sets))));
    }

    private void cardio(CurrentUserPrincipal user, Instant startedAt, int minutes) {
        workoutService.createSession(user, new UpsertWorkoutSessionRequest(null, "Late run", WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL, startedAt, startedAt.plusSeconds(minutes * 60L),
                null, null, null, null, List.of()));
    }

    private void sport(CurrentUserPrincipal user, String localStart, int minutes, SportActivity activity) {
        Instant startedAt = LocalDateTime.parse(localStart).atZone(SGT).toInstant();
        workoutService.createSession(user, new UpsertWorkoutSessionRequest(null, "Badminton", WorkoutType.SPORTS,
                WorkoutSessionStatus.COMPLETED, WorkoutSource.MANUAL, startedAt, startedAt.plusSeconds(minutes * 60L),
                null, null, null, null, List.of(), activity));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock trainingProgressTestClock() {
            return Clock.fixed(Instant.parse("2026-10-05T04:00:00Z"), ZoneOffset.UTC);
        }
    }
}
