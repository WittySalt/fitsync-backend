package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.entity.AnalyticsUserDashboardSnapshotEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkoutEventConsumerTests {

    @Autowired
    private DomainEventConsumer<WorkoutLoggedPayload> workoutLoggedEventConsumer;

    @Autowired
    private DomainEventConsumer<WorkoutUpdatedPayload> workoutUpdatedEventConsumer;

    @Autowired
    private DomainEventConsumer<WorkoutDeletedPayload> workoutDeletedEventConsumer;

    @Autowired
    private AnalyticsWorkoutContributionRepository workoutContributionRepository;

    @Autowired
    private AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;

    @Autowired
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;

    @Test
    void workoutLoggedEventBuildsContributionDailyAggregateAndDashboardWorkoutSummary() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startedAt = today.atStartOfDay().plusHours(6).toInstant(ZoneOffset.UTC);

        workoutLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                startedAt.plusSeconds(60),
                "workout-service",
                userId,
                new WorkoutLoggedPayload(
                        sessionId,
                        null,
                        "STRENGTH",
                        "COMPLETED",
                        startedAt,
                        startedAt.plusSeconds(2700),
                        45,
                        350,
                        6,
                        "PLAN")));

        assertThat(workoutContributionRepository.findById(sessionId))
                .isPresent()
                .get()
                .satisfies(contribution -> {
                    assertThat(contribution.getActivityDate()).isEqualTo(today);
                    assertThat(contribution.getDurationMinutes()).isEqualTo(45);
                });

        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getCompletedWorkoutCount()).isEqualTo(1);
                    assertThat(daily.getTotalWorkoutMinutes()).isEqualTo(45);
                    assertThat(daily.getTotalCaloriesBurned()).isEqualTo(350);
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getTodayWorkoutCount()).isEqualTo(1);
                    assertThat(snapshot.getWeekWorkoutCount()).isEqualTo(1);
                    assertThat(snapshot.getWeekWorkoutMinutes()).isEqualTo(45);
                    assertThat(snapshot.getActiveStreakDays()).isEqualTo(1);
                });
    }

    @Test
    void workoutLoggedEventUsesUserTimezoneForActivityDate() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-06-17T16:30:00Z");

        AnalyticsUserDashboardSnapshotEntity snapshot = new AnalyticsUserDashboardSnapshotEntity();
        snapshot.setUserId(userId);
        snapshot.setTimezone("Asia/Singapore");
        snapshot.setTodayWorkoutCount(0);
        snapshot.setWeekWorkoutCount(0);
        snapshot.setWeekWorkoutMinutes(0);
        snapshot.setActiveStreakDays(0);
        snapshot.setGeneratedAt(Instant.parse("2026-06-17T16:00:00Z"));
        dashboardSnapshotRepository.save(snapshot);

        workoutLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                startedAt.plusSeconds(60),
                "workout-service",
                userId,
                new WorkoutLoggedPayload(
                        sessionId,
                        null,
                        "STRENGTH",
                        "COMPLETED",
                        startedAt,
                        startedAt.plusSeconds(2700),
                        45,
                        350,
                        6,
                        "MANUAL")));

        assertThat(workoutContributionRepository.findById(sessionId))
                .isPresent()
                .get()
                .extracting(contribution -> contribution.getActivityDate())
                .isEqualTo(LocalDate.parse("2026-06-18"));
    }

    @Test
    void workoutUpdatedEventReplacesExistingContributionInsteadOfDoubleCounting() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startedAt = today.atStartOfDay().plusHours(18).toInstant(ZoneOffset.UTC);

        workoutLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                startedAt.plusSeconds(30),
                "workout-service",
                userId,
                new WorkoutLoggedPayload(
                        sessionId, null, "CARDIO", "COMPLETED", startedAt, startedAt.plusSeconds(1800),
                        30, 220, 1, "MANUAL")));

        workoutUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutUpdated",
                1,
                startedAt.plusSeconds(90),
                "workout-service",
                userId,
                new WorkoutUpdatedPayload(
                        sessionId, null, "CARDIO", "COMPLETED", startedAt, startedAt.plusSeconds(2700),
                        45, 320, 1, "MANUAL")));

        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getCompletedWorkoutCount()).isEqualTo(1);
                    assertThat(daily.getTotalWorkoutMinutes()).isEqualTo(45);
                    assertThat(daily.getTotalCaloriesBurned()).isEqualTo(320);
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(snapshot -> snapshot.getWeekWorkoutMinutes())
                .isEqualTo(45);
    }

    @Test
    void workoutDeletedEventRemovesContributionFromDailyAggregateAndDashboard() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant startedAt = today.atStartOfDay().plusHours(20).toInstant(ZoneOffset.UTC);

        workoutLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutLogged",
                1,
                startedAt.plusSeconds(30),
                "workout-service",
                userId,
                new WorkoutLoggedPayload(
                        sessionId, null, "HIIT", "COMPLETED", startedAt, startedAt.plusSeconds(1500),
                        25, 280, 4, "MANUAL")));

        workoutDeletedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "WorkoutDeleted",
                1,
                startedAt.plusSeconds(120),
                "workout-service",
                userId,
                new WorkoutDeletedPayload(sessionId, null, startedAt.plusSeconds(120))));

        assertThat(workoutContributionRepository.findById(sessionId)).isEmpty();
        assertThat(dailyWorkoutRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getCompletedWorkoutCount()).isZero();
                    assertThat(daily.getTotalWorkoutMinutes()).isZero();
                    assertThat(daily.getTotalCaloriesBurned()).isZero();
                });
        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getTodayWorkoutCount()).isZero();
                    assertThat(snapshot.getWeekWorkoutCount()).isZero();
                    assertThat(snapshot.getWeekWorkoutMinutes()).isZero();
                });
        assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(2L);
    }
}
