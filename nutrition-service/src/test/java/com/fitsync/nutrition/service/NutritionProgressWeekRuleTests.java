package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.NutritionProgressFoodView;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Today is never part of the Progress Food summaries. The clock is fixed to Monday 5 Oct 2026 with four full
 * weeks logged before it and a breakfast logged today.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NutritionProgressWeekRuleTests.FixedClockConfig.class)
class NutritionProgressWeekRuleTests {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

    @Autowired
    private NutritionProgressService service;

    @Autowired
    private MealLogRepository mealLogRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private MealItemNutrientDetailRepository mealItemNutrientDetailRepository;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), "week-rule@example.com", "Week", "USER");

    @BeforeEach
    void fourFullWeeksAndABreakfastToday() {
        mealItemNutrientDetailRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealLogRepository.deleteAll();
        for (LocalDate date = LocalDate.of(2026, 9, 7); date.isBefore(MONDAY); date = date.plusDays(1)) {
            meal(MealType.LUNCH, date.atTime(12, 0).toInstant(ZoneOffset.UTC), "157.00", 2032);
        }
        meal(MealType.BREAKFAST, MONDAY.atTime(2, 0).toInstant(ZoneOffset.UTC), "29.00", 476);
    }

    @Test
    void theSummariesEndYesterdayWhenThePeriodReachesToday() {
        NutritionProgressFoodView view = service.getFood(user, LocalDate.of(2026, 9, 8), MONDAY, "UTC");

        assertEquals(LocalDate.of(2026, 10, 4), view.recentWindow().to());
        assertEquals(LocalDate.of(2026, 9, 8), view.recentWindow().from());
        assertEquals(27, view.habits().loggedDays());
        // Today's breakfast is still a meal time of the period.
        assertTrue(view.mealTimes().stream().anyMatch(time -> "BREAKFAST".equals(time.mealType())));
    }

    @Test
    void aPeriodOfOnlyTodayHasEmptySummaries() {
        NutritionProgressFoodView view = service.getFood(user, MONDAY, MONDAY, "UTC");

        assertEquals(MONDAY.minusDays(1), view.recentWindow().to());
        assertEquals(0, view.habits().loggedDays());
        assertTrue(view.lowestNutrients().isEmpty());
    }

    @Test
    void aPeriodThatEndedKeepsItsLastDay() {
        NutritionProgressFoodView view = service.getFood(user, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 10, 4),
                "UTC");

        assertEquals(LocalDate.of(2026, 10, 4), view.recentWindow().to());
        assertEquals(28, view.habits().loggedDays());
    }

    private void meal(MealType type, Instant consumedAt, String protein, int calories) {
        MealLogEntity meal = new MealLogEntity();
        meal.setMealId(UUID.randomUUID());
        meal.setUserId(user.userId());
        meal.setMealType(type);
        meal.setSource(MealSource.MANUAL);
        meal.setConsumedAt(consumedAt);
        mealLogRepository.save(meal);
        MealItemEntity item = new MealItemEntity();
        item.setMealItemId(UUID.randomUUID());
        item.setMealId(meal.getMealId());
        item.setFoodName("Test food");
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("serving");
        item.setCalories(calories);
        item.setProteinGrams(new BigDecimal(protein));
        mealItemRepository.save(item);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock nutritionProgressWeekRuleTestClock() {
            return Clock.fixed(Instant.parse("2026-10-05T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
