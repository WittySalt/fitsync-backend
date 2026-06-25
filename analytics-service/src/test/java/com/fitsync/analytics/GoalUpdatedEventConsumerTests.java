package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
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
class GoalUpdatedEventConsumerTests {

    @Autowired
    private DomainEventConsumer<GoalUpdatedPayload> consumer;

    @Autowired
    private DomainEventConsumer<BodyMetricsRecordedPayload> bodyMetricsConsumer;

    @Autowired
    private AnalyticsUserGoalProgressRepository goalProgressRepository;

    @Autowired
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;

    @Test
    void activeCalorieGoalUpdatesGoalProgressAndDashboardTarget() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                eventId,
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "CALORIES",
                        new BigDecimal("2100"),
                        "kcal",
                        LocalDate.parse("2026-08-01"),
                        "ACTIVE")));

        assertThat(goalProgressRepository.findById(goalId))
                .isPresent()
                .get()
                .satisfies(goal -> {
                    assertThat(goal.getGoalType()).isEqualTo("CALORIES");
                    assertThat(goal.getGoalStatus()).isEqualTo("ACTIVE");
                    assertThat(goal.isOnTrack()).isTrue();
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(snapshot -> snapshot.getTodayCaloriesTarget())
                .isEqualTo(2100);

        assertThat(processedEventRepository.findById(eventId)).isPresent();
    }

    @Test
    void pausedProteinGoalClearsDashboardProteinTarget() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "PROTEIN",
                        new BigDecimal("150.0"),
                        "g",
                        LocalDate.parse("2026-08-01"),
                        "ACTIVE")));

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T05:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "PROTEIN",
                        new BigDecimal("150.0"),
                        "g",
                        LocalDate.parse("2026-08-01"),
                        "PAUSED")));

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(snapshot -> snapshot.getTodayProteinTargetGrams())
                .isNull();
    }

    @Test
    void pastActiveGoalIsStoredAsExpired() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-14T04:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "CALORIES",
                        new BigDecimal("2100"),
                        "kcal",
                        LocalDate.parse("2026-06-13"),
                        "ACTIVE")));

        assertThat(goalProgressRepository.findById(goalId))
                .isPresent()
                .get()
                .satisfies(goal -> {
                    assertThat(goal.getGoalStatus()).isEqualTo("EXPIRED");
                    assertThat(goal.getDaysRemaining()).isNull();
                    assertThat(goal.isOnTrack()).isFalse();
                });
    }

    @Test
    void weightGoalCapturesLatestKnownWeightAsBaselineWhenProjected() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        bodyMetricsConsumer.consume(new FitSyncDomainEvent<>(
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

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-15T04:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "WEIGHT",
                        new BigDecimal("75.00"),
                        "kg",
                        LocalDate.parse("2026-08-01"),
                        "ACTIVE")));

        assertThat(goalProgressRepository.findById(goalId))
                .isPresent()
                .get()
                .satisfies(goal -> {
                    assertThat(goal.getBaselineValue()).isEqualByComparingTo("78.40");
                    assertThat(goal.getCurrentValue()).isEqualByComparingTo("78.40");
                });
    }
}
