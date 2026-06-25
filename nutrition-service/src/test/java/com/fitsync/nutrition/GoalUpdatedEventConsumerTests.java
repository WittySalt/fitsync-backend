package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
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
    private NutritionTargetRepository nutritionTargetRepository;

    @Test
    void activeCalorieAndProteinGoalsUpdateNutritionTargets() {
        UUID userId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("2100"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:05:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "PROTEIN",
                        new BigDecimal("140.0"),
                        "g",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        assertThat(nutritionTargetRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(target -> {
                    assertThat(target.getCalorieTarget()).isEqualTo(2100);
                    assertThat(target.getProteinTarget()).isNotNull();
                    assertThat(target.getProteinTarget().compareTo(new BigDecimal("140.0"))).isZero();
                });
    }

    @Test
    void nonActiveGoalClearsTrackedTarget() {
        UUID userId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("2200"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:10:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("2200"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "PAUSED")));

        assertThat(nutritionTargetRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(target -> target.getCalorieTarget())
                .isNull();
    }

    @Test
    void nonActiveProteinGoalClearsProteinTarget() {
        UUID userId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "PROTEIN",
                        new BigDecimal("155.0"),
                        "g",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        consumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T14:10:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        goalId,
                        "PROTEIN",
                        new BigDecimal("155.0"),
                        "g",
                        LocalDate.parse("2026-09-01"),
                        "CANCELLED")));

        assertThat(nutritionTargetRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(target -> target.getProteinTarget())
                .isNull();
    }
}
