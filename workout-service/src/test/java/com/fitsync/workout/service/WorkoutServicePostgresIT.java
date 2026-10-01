package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.CreateExerciseRequest;
import com.fitsync.workout.dto.CreateWorkoutPlanRequest;
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.dto.WorkoutExerciseLogInput;
import com.fitsync.workout.dto.WorkoutExerciseSetLogInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.dto.WorkoutSessionView;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.web.response.PageResponse;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WorkoutServicePostgresIT {

    private static final UUID GOBLET_SQUAT_ID = UUID.fromString("51111111-1111-1111-1111-111111111112");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fitsync")
            .withUsername("fitsync")
            .withPassword("fitsync");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private WorkoutService workoutService;

    @Test
    void shouldListExercisesWhenQueryIsBlankOrFilteredOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        CurrentUserPrincipal currentUser = newUser();
        exerciseService.createCustomExercise(currentUser, new CreateExerciseRequest(
                "RESISTANCE_BAND_ROW",
                "Resistance Band Row",
                "Custom pulling movement used at home.",
                ExerciseType.STRENGTH,
                MuscleGroup.BACK,
                ExerciseEquipment.BAND,
                WorkoutDifficulty.BEGINNER));

        PageResponse<ExerciseView> blankQuery = exerciseService.listExercises(
                currentUser,
                "",
                null,
                null,
                null,
                null,
                0,
                30);

        PageResponse<ExerciseView> filtered = exerciseService.listExercises(
                currentUser,
                "squat",
                ExerciseType.STRENGTH,
                MuscleGroup.LEGS,
                ExerciseEquipment.DUMBBELL,
                WorkoutDifficulty.BEGINNER,
                0,
                30);

        assertEquals(4, blankQuery.totalElements());
        assertEquals(List.of("Goblet Squat"), filtered.content().stream().map(ExerciseView::name).toList());
    }

    @Test
    void shouldListPlansAndSessionsWithNullableFiltersOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        TrainingHistory history = seedTrainingHistory();
        CurrentUserPrincipal currentUser = history.user();
        UUID activePlanId = history.planId();

        PageResponse<WorkoutPlanView> allPlans = workoutService.listPlans(currentUser, null, 0, 20);
        PageResponse<WorkoutPlanView> activePlans = workoutService.listPlans(currentUser, WorkoutPlanStatus.ACTIVE, 0, 20);

        PageResponse<WorkoutSessionView> allSessions = workoutService.listSessions(
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
                20);
        PageResponse<WorkoutSessionView> filteredSessions = workoutService.listSessions(
                currentUser,
                Instant.parse("2026-06-10T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
                WorkoutSessionStatus.COMPLETED,
                null,
                null,
                null,
                activePlanId,
                null,
                0,
                20);

        assertEquals(1, allPlans.totalElements());
        assertEquals(1, activePlans.totalElements());
        assertEquals(1, allSessions.totalElements());
        assertEquals(1, filteredSessions.totalElements());
        assertEquals(activePlanId, filteredSessions.content().get(0).planId());
    }

    @Test
    void shouldLoadExerciseRecordsAndHistoryOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        CurrentUserPrincipal currentUser = seedTrainingHistory().user();

        var records = workoutService.getExerciseRecords(currentUser, GOBLET_SQUAT_ID);
        var history = workoutService.getExerciseHistory(
                currentUser,
                GOBLET_SQUAT_ID,
                Instant.parse("2026-06-09T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
                0,
                20);

        assertEquals(new BigDecimal("12.00"), records.maxWeightKg().value());
        assertEquals(3, history.content().size());
        assertTrue(history.content().stream().allMatch(entry -> GOBLET_SQUAT_ID.equals(entry.exerciseId())));
    }

    @Test
    void shouldReturnLatestCompletedSessionOnlyOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal user = new CurrentUserPrincipal(
                UUID.randomUUID(),
                "latest-completed@example.com",
                "Latest Completed",
                "USER");

        workoutService.createSession(user, sessionRequest(
                "Cancelled Later",
                WorkoutSessionStatus.CANCELLED,
                Instant.parse("2026-06-12T10:00:00Z"),
                null));
        workoutService.createSession(user, sessionRequest(
                "In Progress Later",
                WorkoutSessionStatus.IN_PROGRESS,
                Instant.parse("2026-06-13T10:00:00Z"),
                null));

        assertThrows(ResourceNotFoundException.class, () -> workoutService.getLatestSession(user));

        WorkoutSessionView completed = workoutService.createSession(user, sessionRequest(
                "Completed Earlier",
                WorkoutSessionStatus.COMPLETED,
                Instant.parse("2026-06-11T10:00:00Z"),
                Instant.parse("2026-06-11T10:45:00Z")));

        WorkoutSessionView latest = workoutService.getLatestSession(user);

        assertEquals(completed.sessionId(), latest.sessionId());
        assertEquals(WorkoutSessionStatus.COMPLETED, latest.status());
    }

    @Test
    void shouldCreateAndReadBackAPlanWithAWeekOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal user = new CurrentUserPrincipal(
                UUID.randomUUID(),
                "plan-week-postgres@example.com",
                "Plan Week Postgres",
                "USER");

        WorkoutPlanView plan = workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                "Postgres Week Plan",
                null,
                GoalFocus.STRENGTH,
                WorkoutType.CUSTOM,
                WorkoutDifficulty.BEGINNER,
                null,
                List.of(),
                List.of(
                        new WorkoutPlanDayInput(null, 1, SessionKind.STRENGTH, "Push", null, 45,
                                List.of(new WorkoutPlanExerciseInput(GOBLET_SQUAT_ID, 1, 3, 8, null, null, null, 90, null, null))),
                        new WorkoutPlanDayInput(null, 6, SessionKind.SPORT, "Badminton Night", SportActivity.BADMINTON, 60, List.of()))));

        WorkoutPlanView fetched = workoutService.getPlan(user, plan.planId());

        assertEquals(2, fetched.days().size());
        assertEquals(2, fetched.sessionsPerWeek());
        assertTrue(fetched.exercises().isEmpty());
        assertEquals(1, fetched.days().get(0).exercises().size());
        assertEquals(SportActivity.BADMINTON, fetched.days().get(1).sportActivity());
        assertEquals("Badminton", fetched.days().get(1).sportLabel());
    }

    private CurrentUserPrincipal newUser() {
        UUID userId = UUID.randomUUID();
        return new CurrentUserPrincipal(userId, "training-" + userId + "@example.com", "Training User", "USER");
    }

    /** A person with one active plan and one completed session of three goblet squat sets. */
    private TrainingHistory seedTrainingHistory() {
        CurrentUserPrincipal user = newUser();
        WorkoutPlanView plan = workoutService.createPlan(user, new CreateWorkoutPlanRequest(
                "Full Body Starter",
                null,
                GoalFocus.GENERAL_FITNESS,
                WorkoutType.STRENGTH,
                WorkoutDifficulty.BEGINNER,
                45,
                List.of(new WorkoutPlanExerciseInput(GOBLET_SQUAT_ID, 1, 3, 10, null, null, null, 75, null, null))));
        workoutService.activatePlan(user, plan.planId());
        workoutService.createSession(user, new UpsertWorkoutSessionRequest(
                plan.planId(),
                "Full Body Starter - Session 1",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                WorkoutSource.PLAN,
                Instant.parse("2026-06-10T09:00:00Z"),
                Instant.parse("2026-06-10T09:46:00Z"),
                46,
                null,
                null,
                null,
                List.of(new WorkoutExerciseLogInput(GOBLET_SQUAT_ID, 1, 3, 30, new BigDecimal("360.00"),
                        null, null, null, null, null,
                        List.of(squatSet(1), squatSet(2), squatSet(3))))));
        return new TrainingHistory(user, plan.planId());
    }

    private WorkoutExerciseSetLogInput squatSet(int setNumber) {
        return new WorkoutExerciseSetLogInput(setNumber, 10, new BigDecimal("12.00"), null, null, 75, 7, null);
    }

    private record TrainingHistory(CurrentUserPrincipal user, UUID planId) {
    }

    private UpsertWorkoutSessionRequest sessionRequest(String name,
                                                       WorkoutSessionStatus status,
                                                       Instant startedAt,
                                                       Instant completedAt) {
        return new UpsertWorkoutSessionRequest(
                null,
                name,
                WorkoutType.STRENGTH,
                status,
                WorkoutSource.MANUAL,
                startedAt,
                completedAt,
                completedAt == null ? null : 45,
                null,
                null,
                null,
                List.of());
    }
}
