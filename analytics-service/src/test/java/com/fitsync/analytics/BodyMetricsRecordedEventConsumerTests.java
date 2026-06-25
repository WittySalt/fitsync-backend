package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BodyMetricsRecordedEventConsumerTests {

    @Autowired
    private DomainEventConsumer<BodyMetricsRecordedPayload> consumer;

    @Autowired
    private DomainEventConsumer<GoalUpdatedPayload> goalUpdatedEventConsumer;

    @Autowired
    private AnalyticsUserDailyBodyRepository dailyBodyRepository;

    @Autowired
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;

    @Autowired
    private AnalyticsUserGoalProgressRepository goalProgressRepository;

    @Test
    void bodyMetricsEventUpdatesDailyProjectionAndDashboardSnapshot() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                eventId,
                "BodyMetricsRecorded",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new BodyMetricsRecordedPayload(
                        UUID.randomUUID(),
                        new BigDecimal("78.40"),
                        new BigDecimal("19.80"),
                        new BigDecimal("85.50"),
                        Instant.parse("2026-06-14T03:50:00Z"))));

        assertThat(dailyBodyRepository.findTopByUserIdOrderByMetricDateDesc(userId))
                .isPresent()
                .get()
                .satisfies(body -> {
                    assertThat(body.getWeightKg()).isEqualByComparingTo("78.40");
                    assertThat(body.getBodyFatRate()).isEqualByComparingTo("19.80");
                    assertThat(body.getWaistCm()).isEqualByComparingTo("85.50");
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getLatestWeightKg()).isEqualByComparingTo("78.40");
                    assertThat(snapshot.getLatestBodyFatRate()).isEqualByComparingTo("19.80");
                });

        assertThat(processedEventRepository.findById(eventId)).isPresent();
    }

    @Test
    void duplicateBodyMetricsEventIsIgnoredByEventId() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        FitSyncDomainEvent<BodyMetricsRecordedPayload> event = new FitSyncDomainEvent<>(
                eventId,
                "BodyMetricsRecorded",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new BodyMetricsRecordedPayload(
                        UUID.randomUUID(),
                        new BigDecimal("79.00"),
                        null,
                        null,
                        Instant.parse("2026-06-14T03:50:00Z")));

        consumer.consume(event);
        consumer.consume(event);

        AnalyticsUserDashboardSnapshotEntity snapshot = dashboardSnapshotRepository.findById(userId).orElseThrow();
        assertThat(snapshot.getLatestWeightKg()).isEqualByComparingTo("79.00");
        assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void bodyMetricsEventRecomputesWeightGoalCurrentValue() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        goalUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T03:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "WEIGHT",
                        new BigDecimal("75.00"),
                        "kg",
                        LocalDate.parse("2026-08-01"),
                        "ACTIVE")));

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "BodyMetricsRecorded",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new BodyMetricsRecordedPayload(
                        UUID.randomUUID(),
                        new BigDecimal("78.40"),
                        null,
                        null,
                        Instant.parse("2026-06-14T03:50:00Z"))));

        assertThat(goalProgressRepository.findById(goalId))
                .isPresent()
                .get()
                .satisfies(goal -> assertThat(goal.getCurrentValue()).isEqualByComparingTo("78.40"));
    }
}
