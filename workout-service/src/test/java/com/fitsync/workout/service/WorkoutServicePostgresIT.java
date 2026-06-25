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
import com.fitsync.workout.dto.ExerciseView;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
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

    private static final UUID ALICE_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ACTIVE_PLAN_ID = UUID.fromString("52111111-1111-1111-1111-111111111111");
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

    private final CurrentUserPrincipal currentUser = new CurrentUserPrincipal(
            ALICE_USER_ID,
            "alice@example.com",
            "Alice",
            "USER");

    @Test
    void shouldListExercisesWhenQueryIsBlankOrFilteredOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

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
                ACTIVE_PLAN_ID,
                0,
                20);

        assertEquals(1, allPlans.totalElements());
        assertEquals(1, activePlans.totalElements());
        assertEquals(1, allSessions.totalElements());
        assertEquals(1, filteredSessions.totalElements());
        assertEquals(ACTIVE_PLAN_ID, filteredSessions.content().get(0).planId());
    }

    @Test
    void shouldLoadExerciseRecordsAndHistoryOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

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
