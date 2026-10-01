package com.fitsync.user.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.domain.MetricSource;
import com.fitsync.user.entity.BodyMetricEntryEntity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
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

    private UUID userId;
    private UUID latestMetricId;
    private UUID oldestMetricId;

    @BeforeEach
    void seedTwoCheckIns() {
        userId = UUID.randomUUID();
        oldestMetricId = saveCheckIn(Instant.parse("2026-06-03T23:00:00Z"), "63.80");
        latestMetricId = saveCheckIn(Instant.parse("2026-06-10T23:00:00Z"), "62.90");
    }

    private UUID saveCheckIn(Instant measuredAt, String weightKg) {
        BodyMetricEntryEntity entry = new BodyMetricEntryEntity();
        entry.setUserId(userId);
        entry.setWeightKg(new BigDecimal(weightKg));
        entry.setMeasuredAt(measuredAt);
        entry.setSource(MetricSource.MANUAL);
        return bodyMetricEntryRepository.save(entry).getMetricId();
    }

    @Test
    void shouldReturnHistoryWhenDateRangeIsNullableOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        List<BodyMetricEntryEntity> allEntries = bodyMetricEntryRepository.findHistory(
                userId,
                null,
                null,
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> fromOnly = bodyMetricEntryRepository.findHistory(
                userId,
                Instant.parse("2026-06-06T00:00:00Z"),
                null,
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> toOnly = bodyMetricEntryRepository.findHistory(
                userId,
                null,
                Instant.parse("2026-06-06T00:00:00Z"),
                PageRequest.of(0, 10));
        List<BodyMetricEntryEntity> bounded = bodyMetricEntryRepository.findHistory(
                userId,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"),
                PageRequest.of(0, 1));

        assertEquals(List.of(latestMetricId, oldestMetricId),
                allEntries.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(latestMetricId),
                fromOnly.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(oldestMetricId),
                toOnly.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertEquals(List.of(latestMetricId),
                bounded.stream().map(BodyMetricEntryEntity::getMetricId).toList());
    }

    @Test
    void shouldReturnTrendSourceWhenDateRangeIsNullableOnPostgres() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available");

        List<BodyMetricEntryEntity> allEntries = bodyMetricEntryRepository.findTrendSource(userId, null, null);
        List<BodyMetricEntryEntity> bounded = bodyMetricEntryRepository.findTrendSource(
                userId,
                Instant.parse("2026-06-06T00:00:00Z"),
                Instant.parse("2026-06-11T00:00:00Z"));

        assertIterableEquals(
                List.of(oldestMetricId, latestMetricId),
                allEntries.stream().map(BodyMetricEntryEntity::getMetricId).toList());
        assertIterableEquals(
                List.of(latestMetricId),
                bounded.stream().map(BodyMetricEntryEntity::getMetricId).toList());
    }
}
