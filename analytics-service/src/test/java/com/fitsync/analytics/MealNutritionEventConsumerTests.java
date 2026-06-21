package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MealNutritionEventConsumerTests {

    @Autowired
    private DomainEventConsumer<MealLoggedPayload> mealLoggedEventConsumer;

    @Autowired
    private DomainEventConsumer<MealUpdatedPayload> mealUpdatedEventConsumer;

    @Autowired
    private DomainEventConsumer<MealDeletedPayload> mealDeletedEventConsumer;

    @Autowired
    private AnalyticsMealContributionRepository mealContributionRepository;

    @Autowired
    private AnalyticsUserDailyNutritionRepository dailyNutritionRepository;

    @Autowired
    private AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;

    @Autowired
    private AnalyticsProcessedEventRepository processedEventRepository;

    @Test
    void mealLoggedEventBuildsContributionDailyAggregateAndDashboardTodayNutrition() {
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant consumedAt = today.atStartOfDay().plusHours(8).toInstant(ZoneOffset.UTC);

        mealLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                consumedAt.plusSeconds(60),
                "nutrition-service",
                userId,
                new MealLoggedPayload(
                        mealId,
                        "BREAKFAST",
                        consumedAt,
                        600,
                        new BigDecimal("40.00"),
                        new BigDecimal("52.00"),
                        new BigDecimal("14.00"),
                        2,
                        "MANUAL")));

        assertThat(mealContributionRepository.findById(mealId))
                .isPresent()
                .get()
                .satisfies(contribution -> {
                    assertThat(contribution.getActivityDate()).isEqualTo(today);
                    assertThat(contribution.getTotalCalories()).isEqualTo(600);
                });

        assertThat(dailyNutritionRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getMealCount()).isEqualTo(1);
                    assertThat(daily.getTotalCaloriesConsumed()).isEqualTo(600);
                    assertThat(daily.getTotalProteinGrams()).isEqualByComparingTo("40.00");
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .satisfies(snapshot -> {
                    assertThat(snapshot.getTodayCaloriesConsumed()).isEqualTo(600);
                    assertThat(snapshot.getTodayProteinGrams()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void mealUpdatedEventReplacesExistingContributionInsteadOfDoubleCounting() {
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant consumedAt = today.atStartOfDay().plusHours(12).toInstant(ZoneOffset.UTC);

        mealLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                consumedAt.plusSeconds(30),
                "nutrition-service",
                userId,
                new MealLoggedPayload(
                        mealId,
                        "LUNCH",
                        consumedAt,
                        500,
                        new BigDecimal("30.00"),
                        new BigDecimal("45.00"),
                        new BigDecimal("12.00"),
                        1,
                        "MANUAL")));

        mealUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealUpdated",
                1,
                consumedAt.plusSeconds(90),
                "nutrition-service",
                userId,
                new MealUpdatedPayload(
                        mealId,
                        "LUNCH",
                        consumedAt,
                        720,
                        new BigDecimal("55.00"),
                        new BigDecimal("60.00"),
                        new BigDecimal("20.00"),
                        2,
                        "MANUAL")));

        assertThat(dailyNutritionRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getMealCount()).isEqualTo(1);
                    assertThat(daily.getTotalCaloriesConsumed()).isEqualTo(720);
                    assertThat(daily.getTotalProteinGrams()).isEqualByComparingTo("55.00");
                });

        assertThat(dashboardSnapshotRepository.findById(userId))
                .isPresent()
                .get()
                .extracting(snapshot -> snapshot.getTodayCaloriesConsumed())
                .isEqualTo(720);
    }

    @Test
    void mealDeletedEventRemovesExistingContributionFromDailyAggregate() {
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant consumedAt = today.atStartOfDay().plusHours(18).toInstant(ZoneOffset.UTC);

        mealLoggedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                consumedAt.plusSeconds(30),
                "nutrition-service",
                userId,
                new MealLoggedPayload(
                        mealId,
                        "DINNER",
                        consumedAt,
                        900,
                        new BigDecimal("70.00"),
                        new BigDecimal("80.00"),
                        new BigDecimal("28.00"),
                        3,
                        "MANUAL")));

        mealDeletedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealDeleted",
                1,
                consumedAt.plusSeconds(120),
                "nutrition-service",
                userId,
                new MealDeletedPayload(mealId, consumedAt.plusSeconds(120))));

        assertThat(mealContributionRepository.findById(mealId)).isEmpty();
        assertThat(dailyNutritionRepository.findByUserIdAndActivityDate(userId, today))
                .isPresent()
                .get()
                .satisfies(daily -> {
                    assertThat(daily.getMealCount()).isZero();
                    assertThat(daily.getTotalCaloriesConsumed()).isZero();
                    assertThat(daily.getTotalProteinGrams()).isEqualByComparingTo("0");
                });
        assertThat(processedEventRepository.count()).isGreaterThanOrEqualTo(2L);
    }
}
