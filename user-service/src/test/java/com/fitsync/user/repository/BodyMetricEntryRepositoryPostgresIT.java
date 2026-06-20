package com.fitsync.user.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class BodyMetricEntryRepositoryPostgresIT {

    private static final UUID ALICE_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LATEST_METRIC_ID = UUID.fromString("71111111-1111-1111-1111-111111111112");
    private static final UUID OLDEST_METRIC_ID = UUID.fromString("71111111-1111-1111-1111-111111111111");

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
    private BodyMetricEntryRepository bodyMetricEntryRepository;

    @Test
    void shouldReturnHistoryWhenDateRangeIsNullableOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        List<BodyMetricEntryEntity> allEntries = bodyMetricEntryRepository.findHistory(
                ALICE_USER_ID,
                null,
                null,
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> fromOnly = bodyMetricEntryRepository.findHistory(
                ALICE_USER_ID,
                Instant.parse("2026-06-06T00:00:00Z"),
                null,
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> toOnly = bodyMetricEntryRepository.findHistory(
                ALICE_USER_ID,
                null,
                Instant.parse("2026-06-06T00:00:00Z"),
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> bounded = bodyMetricEntryRepository.findHistory(
                ALICE_USER_ID,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
                PageRequest.of(0, 1));

        assertEquals(List.of(LATEST_METRIC_ID, OLDEST_METRIC_ID),
                allEntries.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(LATEST_METRIC_ID),
                fromOnly.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(OLDEST_METRIC_ID),
                toOnly.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(LATEST_METRIC_ID),
                bounded.stream().map(BodyMetricEntryEntity::getMetricId).toList());
    }

    @Test
    void shouldReturnTrendSourceWhenDateRangeIsNullableOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        List<BodyMetricEntryEntity> allEntries = bodyMetricEntryRepository.findTrendSource(ALICE_USER_ID, null, null);
        List<BodyMetricEntryEntity> bounded = bodyMetricEntryRepository.findTrendSource(
                ALICE_USER_ID,
                Instant.parse("2026-06-06T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"));

        assertIterableEquals(
                List.of(OLDEST_METRIC_ID, LATEST_METRIC_ID),
                allEntries.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertIterableEquals(
                List.of(LATEST_METRIC_ID),
                bounded.stream().map(BodyMetricEntryEntity::getMetricId).toList());
    }
}
