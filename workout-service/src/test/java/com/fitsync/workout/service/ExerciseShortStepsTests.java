package com.fitsync.workout.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.ExerciseShortStepRepository;
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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExerciseShortStepsTests {

    private static final List<String> CODES_WITH_SHORT_STEPS = List.of(
            "dumbbell_bench_press",
            "barbell_bench_press_-_medium_grip",
            "bent_over_two-dumbbell_row",
            "goblet_squat",
            "bodyweight_squat",
            "barbell_squat",
            "dumbbell_lunges",
            "romanian_deadlift",
            "barbell_deadlift",
            "dumbbell_shoulder_press",
            "wide-grip_lat_pulldown",
            "dumbbell_bicep_curl",
            "pushups",
            "plank",
            "crunches",
            "mountain_climbers",
            "jogging_treadmill",
            "DEMO_TREADMILL");

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExerciseShortStepRepository shortStepRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final CurrentUserPrincipal currentUser = new CurrentUserPrincipal(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            "alice@example.com",
            "Alice",
            "USER");

    @BeforeEach
    void clearExercises() {
        workoutSessionRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    void migrationStoresTwoToFourOrderedStepsForEveryListedCode() {
        assertThat(shortStepRepository.count()).isEqualTo(72);
        for (String code : CODES_WITH_SHORT_STEPS) {
            List<String> steps = shortStepRepository.findTextsByExerciseCode(code);
            assertThat(steps).as(code).hasSizeBetween(2, 4).doesNotContainNull();
            assertThat(steps).as(code).allSatisfy(step -> assertThat(step).isNotBlank().hasSizeLessThanOrEqualTo(200));
        }
        assertThat(shortStepRepository.findTextsByExerciseCode("dumbbell_bench_press")).containsExactly(
                "Lie back with the dumbbells at chest height, palms facing your feet.",
                "Breathe out and press both up until the arms are straight.",
                "Lower for two counts until the elbows are level with the bench.",
                "Keep the feet flat and the shoulder blades pressed into the bench.");
    }

    @Test
    void codeIsMatchedCaseSensitively() {
        assertThat(shortStepRepository.findTextsByExerciseCode("DEMO_TREADMILL")).isNotEmpty();
        assertThat(shortStepRepository.findTextsByExerciseCode("demo_treadmill")).isEmpty();
        assertThat(shortStepRepository.findTextsByExerciseCode("DUMBBELL_BENCH_PRESS")).isEmpty();
    }

    @Test
    void detailCarriesShortStepsForACatalogueExercise() {
        UUID exerciseId = saveExercise("dumbbell_bench_press", false, null);

        ExerciseView view = exerciseService.getExercise(currentUser, exerciseId);

        assertThat(view.shortSteps()).isEqualTo(shortStepRepository.findTextsByExerciseCode("dumbbell_bench_press"));
        assertThat(view.shortSteps()).hasSize(4);
    }

    @Test
    void detailHasAnEmptyListWhenTheExerciseHasNoShortSteps() {
        UUID exerciseId = saveExercise("exercise-without-short-steps", false, null);

        ExerciseView view = exerciseService.getExercise(currentUser, exerciseId);

        assertThat(view.shortSteps()).isEmpty();
    }

    @Test
    void customExerciseWithTheTreadmillCodeGetsTheTreadmillSteps() {
        UUID exerciseId = saveExercise("DEMO_TREADMILL", true, currentUser.userId());

        ExerciseView view = exerciseService.getExercise(currentUser, exerciseId);

        assertThat(view.shortSteps()).isEqualTo(shortStepRepository.findTextsByExerciseCode("DEMO_TREADMILL"));
        assertThat(view.shortSteps()).isNotEmpty();
    }

    @Test
    void detailCostsAtMostOneQueryMoreThanBefore() {
        UUID withSteps = saveExercise("goblet_squat", false, null);
        UUID without = saveExercise("exercise-without-short-steps", false, null);
        Statistics statistics = hibernateStatistics();

        statistics.clear();
        exerciseService.getExercise(currentUser, without);
        long baselineWithLookup = statistics.getPrepareStatementCount();

        statistics.clear();
        exerciseService.getExercise(currentUser, withSteps);
        long withStepsCount = statistics.getPrepareStatementCount();

        // The lookup is the same single query whether or not rows exist, so the two counts agree.
        // The detail read was four queries before short steps (the exercise, its instructions, its
        // images and its secondary muscles); the lookup adds exactly one.
        assertThat(withStepsCount).isEqualTo(baselineWithLookup);
        assertThat(withStepsCount).isLessThanOrEqualTo(QUERIES_BEFORE_SHORT_STEPS + 1);
    }

    private static final long QUERIES_BEFORE_SHORT_STEPS = 4;

    private Statistics hibernateStatistics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        sessionFactory.getStatistics().setStatisticsEnabled(true);
        return sessionFactory.getStatistics();
    }

    private UUID saveExercise(String code, boolean custom, UUID ownerUserId) {
        ExerciseEntity entity = new ExerciseEntity();
        entity.setExerciseId(UUID.randomUUID());
        entity.setOwnerUserId(ownerUserId);
        entity.setCode(code);
        entity.setName("Exercise " + code);
        entity.setExerciseType(ExerciseType.STRENGTH);
        entity.setPrimaryMuscleGroup(MuscleGroup.CHEST);
        entity.setEquipment(ExerciseEquipment.DUMBBELL);
        entity.setDifficulty(WorkoutDifficulty.BEGINNER);
        entity.setStatus(ExerciseStatus.ACTIVE);
        entity.setCustom(custom);
        return exerciseRepository.save(entity).getExerciseId();
    }
}
