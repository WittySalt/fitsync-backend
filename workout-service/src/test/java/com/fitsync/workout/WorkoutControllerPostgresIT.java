package com.fitsync.workout;

import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
}
