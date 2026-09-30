package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.InsightPeriod;
import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.MealProteinShareView;
import com.fitsync.nutrition.dto.NutritionPageInsightView;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real repositories on H2, driven through the service. The clock is fixed to Wednesday 30 September
 * 2026, so the described week is Monday 21 to Sunday 27 September and the week before it starts on
 * Monday 14 September.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(NutritionPageInsightServiceTests.FixedClockConfig.class)
class NutritionPageInsightServiceTests {

    private static final String SGT = "Asia/Singapore";
    private static final ZoneId SGT_ZONE = ZoneId.of(SGT);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 9, 21);
    private static final LocalDate EARLIER_START = LocalDate.of(2026, 9, 14);

    @Autowired
    private NutritionPageInsightService service;

    @Autowired
    private MealLogRepository mealLogRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private MealItemNutrientDetailRepository mealItemNutrientDetailRepository;

    @Autowired
    private NutritionTargetRepository nutritionTargetRepository;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("22222222-2222-2222-2222-222222222222"), "insight@example.com", "Insight", "USER");

    @BeforeEach
    void clearData() {
        mealItemNutrientDetailRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealLogRepository.deleteAll();
        nutritionTargetRepository.deleteAll();
    }

    @Test
    void describesAFullWeekAgainstTheWeekBeforeIt() {
        for (int day = 0; day < 7; day++) {
            meal(user.userId(), MealType.BREAKFAST, noonOn(PERIOD_START.plusDays(day)), "18.20", 500);
            meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START.plusDays(day)), "40.05", 700);
            meal(user.userId(), MealType.DINNER, noonOn(PERIOD_START.plusDays(day)), "60.15", 900);
            meal(user.userId(), MealType.DINNER, noonOn(EARLIER_START.plusDays(day)), "121.00", 2310);
        }
        target(user.userId(), 2100, "140.00");

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(SGT, view.timezone());
        assertEquals(InsightPeriod.PREVIOUS_WEEK, view.period());
        assertEquals(PERIOD_START, view.periodStart());
        assertEquals(LocalDate.of(2026, 9, 27), view.periodEnd());
        assertEquals(7, view.daysLogged());
        assertEquals(new BigDecimal("118.4"), view.proteinPerDayGrams());
        assertEquals(2100, view.caloriesPerDay());
        assertEquals(new BigDecimal("121.0"), view.earlierProteinPerDayGrams());
        assertEquals(2310, view.earlierCaloriesPerDay());
        assertEquals(0, new BigDecimal("140").compareTo(view.proteinTargetGrams()));
        assertEquals(2100, view.calorieTargetKcal());
        assertEquals(
                List.of(new BigDecimal("18.2"), new BigDecimal("40.1"), new BigDecimal("60.2")),
                view.proteinByMeal().stream().map(MealProteinShareView::gramsPerDay).toList());
    }

    @Test
    void dividesByTheDaysThatHaveMealsAndLeavesEarlierEmptyWithoutAWeekBefore() {
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START), "100.00", 2000);
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START.plusDays(2)), "50.00", 2000);
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START.plusDays(5)), "20.00", 2000);

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(3, view.daysLogged());
        assertEquals(new BigDecimal("56.7"), view.proteinPerDayGrams());
        assertEquals(2000, view.caloriesPerDay());
        assertNull(view.earlierProteinPerDayGrams());
        assertNull(view.earlierCaloriesPerDay());
        assertNull(view.proteinTargetGrams());
        assertNull(view.calorieTargetKcal());
    }

    @Test
    void roundsCaloriesHalfUpToAWholeNumber() {
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START), "10.00", 2000);
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START.plusDays(1)), "10.00", 2001);

        assertEquals(2001, service.getNutritionPageInsight(user, SGT).caloriesPerDay());
    }

    @Test
    void reportsNoneWithEveryNumberEmptyWhenThePeriodHasNoMeals() {
        meal(user.userId(), MealType.LUNCH, noonOn(EARLIER_START), "50.00", 1500);
        target(user.userId(), 2100, "140.00");

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(InsightPeriod.NONE, view.period());
        assertEquals(PERIOD_START, view.periodStart());
        assertEquals(LocalDate.of(2026, 9, 27), view.periodEnd());
        assertEquals(0, view.daysLogged());
        assertNull(view.proteinPerDayGrams());
        assertNull(view.earlierProteinPerDayGrams());
        assertNull(view.proteinTargetGrams());
        assertNull(view.caloriesPerDay());
        assertNull(view.earlierCaloriesPerDay());
        assertNull(view.calorieTargetKcal());
        assertTrue(view.proteinByMeal().isEmpty());
    }

    @Test
    void placesMealsNearMidnightOnTheLocalDate() {
        // 23:59:59 on Sunday 20 September in Singapore belongs to the week before the period.
        meal(user.userId(), MealType.DINNER, Instant.parse("2026-09-20T15:59:59Z"), "30.00", 600);
        // Midnight on Monday 21 September in Singapore is the first moment of the period.
        meal(user.userId(), MealType.BREAKFAST, Instant.parse("2026-09-20T16:00:00Z"), "100.00", 500);
        // 23:59:59 on Sunday 27 September is the last moment of the period.
        meal(user.userId(), MealType.DINNER, Instant.parse("2026-09-27T15:59:59Z"), "50.00", 700);
        // Midnight on Monday 28 September is in the current week, which is not described.
        meal(user.userId(), MealType.DINNER, Instant.parse("2026-09-27T16:00:00Z"), "999.00", 9999);

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(2, view.daysLogged());
        assertEquals(new BigDecimal("75.0"), view.proteinPerDayGrams());
        assertEquals(new BigDecimal("30.0"), view.earlierProteinPerDayGrams());
        assertEquals(600, view.earlierCaloriesPerDay());
    }

    @Test
    void listsProteinByMealTypeInTheOrderOfADayWithSentenceCaseLabels() {
        Instant day = noonOn(PERIOD_START);
        meal(user.userId(), MealType.OTHER, day, "1.00", 10);
        meal(user.userId(), MealType.SUPPER, day, "2.00", 10);
        meal(user.userId(), MealType.SNACK, day, "3.00", 10);
        meal(user.userId(), MealType.DINNER, day, "4.00", 10);
        meal(user.userId(), MealType.BREAKFAST, day, "5.00", 10);

        List<MealProteinShareView> byMeal = service.getNutritionPageInsight(user, SGT).proteinByMeal();

        assertEquals(List.of(MealType.BREAKFAST, MealType.DINNER, MealType.SNACK, MealType.SUPPER, MealType.OTHER),
                byMeal.stream().map(MealProteinShareView::mealType).toList());
        assertEquals(List.of("Breakfast", "Dinner", "Snack", "Supper", "Other"),
                byMeal.stream().map(MealProteinShareView::label).toList());
        assertEquals(new BigDecimal("5.0"), byMeal.get(0).gramsPerDay());
    }

    @Test
    void countsAMealWithoutItemsAsALoggedDay() {
        meal(user.userId(), MealType.LUNCH, noonOn(PERIOD_START), "60.00", 1000);
        emptyMeal(user.userId(), MealType.SNACK, noonOn(PERIOD_START.plusDays(1)));

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(2, view.daysLogged());
        assertEquals(new BigDecimal("30.0"), view.proteinPerDayGrams());
        assertEquals(500, view.caloriesPerDay());
    }

    @Test
    void ignoresMealsOfOtherUsers() {
        UUID other = UUID.randomUUID();
        meal(other, MealType.LUNCH, noonOn(PERIOD_START), "80.00", 1800);
        target(other, 1900, "120.00");

        NutritionPageInsightView view = service.getNutritionPageInsight(user, SGT);

        assertEquals(InsightPeriod.NONE, view.period());
        assertNull(view.proteinTargetGrams());
    }

    @Test
    void usesUtcWhenNoTimezoneIsGivenAndRejectsAnUnknownZone() {
        assertEquals("UTC", service.getNutritionPageInsight(user, null).timezone());
        assertEquals("UTC", service.getNutritionPageInsight(user, " ").timezone());
        assertThrows(IllegalArgumentException.class, () -> service.getNutritionPageInsight(user, "Mars/Olympus"));
    }

    private static Instant noonOn(LocalDate date) {
        return date.atTime(12, 0).atZone(SGT_ZONE).toInstant();
    }

    private void meal(UUID userId, MealType type, Instant consumedAt, String protein, int calories) {
        UUID mealId = emptyMeal(userId, type, consumedAt);
        MealItemEntity item = new MealItemEntity();
        item.setMealItemId(UUID.randomUUID());
        item.setMealId(mealId);
        item.setFoodName("Test food");
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("serving");
        item.setCalories(calories);
        item.setProteinGrams(new BigDecimal(protein));
        mealItemRepository.save(item);
    }

    private UUID emptyMeal(UUID userId, MealType type, Instant consumedAt) {
        MealLogEntity meal = new MealLogEntity();
        meal.setMealId(UUID.randomUUID());
        meal.setUserId(userId);
        meal.setMealType(type);
        meal.setSource(MealSource.MANUAL);
        meal.setConsumedAt(consumedAt);
        mealLogRepository.save(meal);
        return meal.getMealId();
    }

    private void target(UUID userId, int calories, String protein) {
        NutritionTargetEntity target = new NutritionTargetEntity();
        target.setUserId(userId);
        target.setCalorieTarget(calories);
        target.setProteinTarget(new BigDecimal(protein));
        target.setProjectionUpdatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        nutritionTargetRepository.save(target);
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock nutritionPageInsightTestClock() {
            return Clock.fixed(Instant.parse("2026-09-30T04:00:00Z"), ZoneOffset.UTC);
        }
    }
}
