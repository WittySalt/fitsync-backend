package com.fitsync.workout;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSource;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.UpsertWorkoutSessionRequest;
import com.fitsync.workout.security.CurrentUserPrincipal;
import com.fitsync.workout.service.WorkoutService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WorkoutControllerPostgresIT {

    private static final UUID GOBLET_SQUAT_ID = UUID.fromString("51111111-1111-1111-1111-111111111112");
    private static final UUID ALICE_BAND_ROW_ID = UUID.fromString("51111111-1111-1111-1111-111111111114");

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
    private MockMvc mockMvc;

    @Autowired
    private WorkoutService workoutService;

    @Test
    void exerciseHistoryAcceptsPageAndSizeWithoutDateRangeOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}/history", GOBLET_SQUAT_ID)
                        .with(authentication(TestJwt.authentication("USER")))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].exerciseId").value(GOBLET_SQUAT_ID.toString()))
                .andExpect(jsonPath("$.content[0].exerciseNameSnapshot").value("Goblet Squat"))
                .andExpect(jsonPath("$.content[0].sessionName").value("Full Body Starter - Session 1"))
                .andExpect(jsonPath("$.content[0].startedAt").value("2026-06-10T09:00:00Z"))
                .andExpect(jsonPath("$.content[0].completedAt").value("2026-06-10T09:46:00Z"))
                .andExpect(jsonPath("$.content[0].setNumber").value(1))
                .andExpect(jsonPath("$.content[0].reps").value(10))
                .andExpect(jsonPath("$.content[0].weightKg").value(12.00));
    }

    @Test
    void exerciseHistoryReturnsEmptyPageForAccessibleExerciseWithoutHistoryOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        mockMvc.perform(get("/api/v1/workouts/exercises/{exerciseId}/history", ALICE_BAND_ROW_ID)
                        .with(authentication(TestJwt.authentication("USER")))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void sessionListReturnsFullPageWithoutNullableFiltersOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal principal = seedSessionFilterFixtures(
                UUID.fromString("11111111-2222-1111-1111-111111111111"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authenticationFor(principal)))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Planned Strength"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Cardio"))
                .andExpect(jsonPath("$.content[2].name").value("Session Filters Strength"));
    }

    @Test
    void sessionListAppliesEachNullableFilterAloneOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal principal = seedSessionFilterFixtures(
                UUID.fromString("11111111-2222-2222-1111-111111111111"));
        var authToken = authenticationFor(principal);

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Cardio"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Strength"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("workoutType", "STRENGTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Planned Strength"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Strength"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("minDurationMinutes", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Cardio"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("maxDurationMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Planned Strength"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Strength"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("from", "2026-06-04T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Planned Strength"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Cardio"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("to", "2026-06-06T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Cardio"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Strength"));
    }

    @Test
    void sessionListCombinesFiltersOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal principal = seedSessionFilterFixtures(
                UUID.fromString("11111111-2222-3333-1111-111111111111"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authenticationFor(principal)))
                        .param("status", "COMPLETED")
                        .param("workoutType", "CARDIO")
                        .param("from", "2026-06-04T00:00:00Z")
                        .param("to", "2026-06-06T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Cardio"));
    }

    @Test
    void sessionListReportsAccuratePagingMetadataOnPostgres() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");
        CurrentUserPrincipal principal = seedSessionFilterFixtures(
                UUID.fromString("11111111-2222-4444-1111-111111111111"));
        var authToken = authenticationFor(principal);

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Planned Strength"))
                .andExpect(jsonPath("$.content[1].name").value("Session Filters Cardio"));

        mockMvc.perform(get("/api/v1/workouts/sessions")
                        .with(authentication(authToken))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Session Filters Strength"));
    }

    private CurrentUserPrincipal seedSessionFilterFixtures(UUID userId) {
        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                userId,
                "session-filters-" + userId + "@example.com",
                "Session Filters",
                "USER");
        workoutService.createSession(principal, session(
                "Session Filters Strength",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.COMPLETED,
                Instant.parse("2026-06-01T10:00:00Z"),
                Instant.parse("2026-06-01T10:30:00Z"),
                30));
        workoutService.createSession(principal, session(
                "Session Filters Cardio",
                WorkoutType.CARDIO,
                WorkoutSessionStatus.COMPLETED,
                Instant.parse("2026-06-05T10:00:00Z"),
                Instant.parse("2026-06-05T10:45:00Z"),
                45));
        workoutService.createSession(principal, session(
                "Session Filters Planned Strength",
                WorkoutType.STRENGTH,
                WorkoutSessionStatus.PLANNED,
                Instant.parse("2026-06-10T10:00:00Z"),
                null,
                20));
        return principal;
    }

    private UpsertWorkoutSessionRequest session(String name,
                                                WorkoutType workoutType,
                                                WorkoutSessionStatus status,
                                                Instant startedAt,
                                                Instant completedAt,
                                                Integer durationMinutes) {
        return new UpsertWorkoutSessionRequest(
                null,
                name,
                workoutType,
                status,
                WorkoutSource.MANUAL,
                startedAt,
                completedAt,
                durationMinutes,
                null,
                null,
                null,
                List.of());
    }

    private UsernamePasswordAuthenticationToken authenticationFor(CurrentUserPrincipal principal) {
        return new UsernamePasswordAuthenticationToken(principal, null, List.of(() -> "ROLE_USER"));
    }
}
