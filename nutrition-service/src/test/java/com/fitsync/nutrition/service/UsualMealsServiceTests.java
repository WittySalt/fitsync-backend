package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.UsualMealItemView;
import com.fitsync.nutrition.dto.UsualMealView;
import com.fitsync.nutrition.dto.UsualMealsView;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
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
 * 2026 at noon in Singapore, so the window is the fourteen local days from Wednesday 16 to Tuesday 29
 * September.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(UsualMealsServiceTests.FixedClockConfig.class)
class UsualMealsServiceTests {

    private static final String SGT = "Asia/Singapore";
    private static final ZoneId SGT_ZONE = ZoneId.of(SGT);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);

    private static final UUID OATS = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID MILK = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID EGGS = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final UUID TOAST = UUID.fromString("00000000-0000-0000-0000-0000000000a4");
    private static final UUID CUSTOM_SHAKE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Autowired
    private UsualMealsService service;

    @Autowired
    private MealLogRepository mealLogRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private MealItemNutrientDetailRepository mealItemNutrientDetailRepository;

    private final CurrentUserPrincipal user = new CurrentUserPrincipal(
            UUID.fromString("33333333-3333-3333-3333-333333333333"), "usual@example.com", "Usual", "USER");

    @BeforeEach
    void clearData() {
        mealItemNutrientDetailRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealLogRepository.deleteAll();
    }

    @Test
    void picksTheMostFrequentCombinationAndCopiesFromItsLatestMeal() {
        for (int day = 1; day <= 4; day++) {
            meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(day), 7, 30),
                    oats(), milk());
        }
        // A different breakfast, eaten twice, does not compete.
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(5), 7, 30), oats(), food(EGGS, "Eggs", 140));
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(6), 7, 30), oats(), food(EGGS, "Eggs", 140));
        // The latest meal of the usual combination is the one eaten yesterday.
        UUID expectedSource = mealIdOf(TODAY.minusDays(1), MealType.BREAKFAST);

        UsualMealView breakfast = single(service.getUsualMeals(user, SGT));

        assertEquals(MealType.BREAKFAST, breakfast.mealType());
        assertEquals("Breakfast", breakfast.label());
        assertEquals("07:30", breakfast.usualTime());
        assertEquals(6, breakfast.mealsInWindow());
        assertEquals(4, breakfast.occurrences());
        assertEquals(expectedSource, breakfast.sourceMealId());
        assertEquals(310, breakfast.totalCalories());
        assertEquals(new BigDecimal("14.6"), breakfast.totalProteinGrams());
        assertEquals(new BigDecimal("44.3"), breakfast.totalCarbsGrams());
        assertEquals(new BigDecimal("7.5"), breakfast.totalFatGrams());
        // Two items saved in the same instant have no order between them, so the names are compared
        // as a set and the item is found by its name.
        assertEquals(List.of("Milk", "Rolled Oats"),
                breakfast.items().stream().map(UsualMealItemView::name).sorted().toList());
        UsualMealItemView first = breakfast.items().stream()
                .filter(item -> "Rolled Oats".equals(item.name())).findFirst().orElseThrow();
        assertEquals(190, first.calories());
        assertEquals(new BigDecimal("6.5"), first.proteinGrams());
        assertEquals("GRAINS", first.categoryCode());
        assertNull(breakfast.items().stream()
                .filter(item -> "Milk".equals(item.name())).findFirst().orElseThrow().categoryCode());
    }

    @Test
    void treatsMealsWithDifferentQuantitiesAsTheSameCombination() {
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(1), 12, 0), oats(), milk());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(2), 12, 0),
                item(OATS, null, "Rolled Oats", 380, "13.00", "66.50", "6.90", "GRAINS", "2.00"), milk());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(3), 12, 0),
                milk(), item(OATS, null, "Rolled Oats", 95, "3.25", "16.60", "1.70", "GRAINS", "0.50"));

        UsualMealView lunch = single(service.getUsualMeals(user, SGT));

        assertEquals(3, lunch.occurrences());
        assertEquals(310, lunch.totalCalories());
    }

    @Test
    void doesNotCallACombinationUsualWhenItWasSeenOnlyTwice() {
        meal(user.userId(), MealType.DINNER, at(TODAY.minusDays(1), 19, 0), oats(), milk());
        meal(user.userId(), MealType.DINNER, at(TODAY.minusDays(2), 19, 0), oats(), milk());
        meal(user.userId(), MealType.DINNER, at(TODAY.minusDays(3), 19, 0), oats(), food(EGGS, "Eggs", 140));

        UsualMealView dinner = single(service.getUsualMeals(user, SGT));

        assertEquals("19:00", dinner.usualTime());
        assertEquals(3, dinner.mealsInWindow());
        assertEquals(0, dinner.occurrences());
        assertNull(dinner.sourceMealId());
        assertNull(dinner.totalCalories());
        assertNull(dinner.totalProteinGrams());
        assertNull(dinner.totalCarbsGrams());
        assertNull(dinner.totalFatGrams());
        assertTrue(dinner.items().isEmpty());
    }

    @Test
    void breaksATieInFavourOfTheCombinationEatenMostRecently() {
        // Combination A is eaten on days 1, 3 and 5 and B on days 2, 4 and 6, so A has the latest meal.
        for (int day = 1; day <= 6; day++) {
            if (day % 2 == 1) {
                meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(day), 8, 0), oats(), milk());
            } else {
                meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(day), 8, 0), food(TOAST, "Toast", 120),
                        food(EGGS, "Eggs", 140));
            }
        }

        UsualMealView breakfast = single(service.getUsualMeals(user, SGT));

        assertEquals(3, breakfast.occurrences());
        assertEquals(mealIdOf(TODAY.minusDays(1), MealType.BREAKFAST), breakfast.sourceMealId());
        assertEquals(310, breakfast.totalCalories());
    }

    @Test
    void prefersTheMoreFrequentCombinationOverTheMoreRecentOne() {
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(1), 8, 0), food(TOAST, "Toast", 120));
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(2), 8, 0), food(TOAST, "Toast", 120));
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(3), 8, 0), oats());
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(4), 8, 0), oats());
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(5), 8, 0), oats());

        UsualMealView breakfast = single(service.getUsualMeals(user, SGT));

        assertEquals(3, breakfast.occurrences());
        assertEquals(mealIdOf(TODAY.minusDays(3), MealType.BREAKFAST), breakfast.sourceMealId());
    }

    @Test
    void matchesFoodsWithoutAnIdentifierByTheirLowerCasedTrimmedName() {
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(1), 15, 0),
                item(null, null, "Rolled Oats", 190, "6.50", "33.25", "3.45", null, "1.00"));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(2), 15, 0),
                item(null, null, "  rolled oats ", 190, "6.50", "33.25", "3.45", null, "1.00"));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(3), 15, 0),
                item(null, null, "ROLLED OATS", 190, "6.50", "33.25", "3.45", null, "1.00"));

        UsualMealView snack = single(service.getUsualMeals(user, SGT));

        assertEquals(3, snack.occurrences());
        assertEquals(mealIdOf(TODAY.minusDays(1), MealType.SNACK), snack.sourceMealId());
    }

    @Test
    void identifiesACustomFoodByItsIdentifierRatherThanItsName() {
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(1), 15, 0),
                item(null, CUSTOM_SHAKE, "Shake", 200, "20.00", "10.00", "2.00", null, "1.00"));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(2), 15, 0),
                item(null, CUSTOM_SHAKE, "Protein shake", 200, "20.00", "10.00", "2.00", null, "1.00"));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(3), 15, 0),
                item(null, CUSTOM_SHAKE, "My shake", 200, "20.00", "10.00", "2.00", null, "1.00"));

        assertEquals(3, single(service.getUsualMeals(user, SGT)).occurrences());
    }

    @Test
    void usesTheMiddleMealForAnOddCountAndRoundsToFiveMinutes() {
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(1), 11, 28), oats());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(2), 12, 43), oats());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(3), 14, 10), oats());

        assertEquals("12:45", single(service.getUsualMeals(user, SGT)).usualTime());
    }

    @Test
    void averagesTheTwoMiddleMealsForAnEvenCount() {
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(1), 6, 0), oats());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(2), 7, 30), oats());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(3), 7, 41), oats());
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(4), 20, 0), oats());

        // The middle pair is 07:30 and 07:41, whose average 07:35:30 rounds down to 07:35.
        assertEquals("07:35", single(service.getUsualMeals(user, SGT)).usualTime());
    }

    @Test
    void leavesOutTodayAndAnythingBeforeTheFourteenDayWindow() {
        meal(user.userId(), MealType.BREAKFAST, at(TODAY, 7, 0), oats());
        // The first local day of the window is included from its first minute.
        meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(14), 0, 0), oats());
        // The day before the window is not, even one minute before it opens.
        meal(user.userId(), MealType.DINNER, at(TODAY.minusDays(15), 23, 59), oats());
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(15), 12, 0), oats());
        // The last minute of yesterday is still inside.
        meal(user.userId(), MealType.SUPPER, at(TODAY.minusDays(1), 23, 59), oats());

        UsualMealsView view = service.getUsualMeals(user, SGT);

        assertEquals(List.of(MealType.LUNCH, MealType.SUPPER),
                view.meals().stream().map(UsualMealView::mealType).toList());
        assertEquals(14, view.windowDays());
    }

    @Test
    void ignoresMealsOfOtherUsers() {
        UUID other = UUID.randomUUID();
        for (int day = 1; day <= 4; day++) {
            meal(other, MealType.BREAKFAST, at(TODAY.minusDays(day), 7, 30), oats(), milk());
        }
        meal(user.userId(), MealType.DINNER, at(TODAY.minusDays(1), 19, 0), oats());

        UsualMealsView view = service.getUsualMeals(user, SGT);

        assertEquals(1, view.meals().size());
        assertEquals(MealType.DINNER, view.meals().get(0).mealType());
    }

    @Test
    void listsTheTypesInTheOrderOfADay() {
        Instant day = at(TODAY.minusDays(1), 12, 0);
        meal(user.userId(), MealType.OTHER, day, oats());
        meal(user.userId(), MealType.SUPPER, day, oats());
        meal(user.userId(), MealType.SNACK, day, oats());
        meal(user.userId(), MealType.DINNER, day, oats());
        meal(user.userId(), MealType.BREAKFAST, day, oats());

        UsualMealsView view = service.getUsualMeals(user, SGT);

        assertEquals(List.of(MealType.BREAKFAST, MealType.DINNER, MealType.SNACK, MealType.SUPPER, MealType.OTHER),
                view.meals().stream().map(UsualMealView::mealType).toList());
        assertEquals(List.of("Breakfast", "Dinner", "Snack", "Supper", "Other"),
                view.meals().stream().map(UsualMealView::label).toList());
    }

    @Test
    void returnsNoMealsWhenTheWindowIsEmpty() {
        meal(user.userId(), MealType.BREAKFAST, at(TODAY.minusDays(20), 7, 0), oats());

        UsualMealsView view = service.getUsualMeals(user, SGT);

        assertEquals(SGT, view.timezone());
        assertEquals(14, view.windowDays());
        assertTrue(view.meals().isEmpty());
    }

    @Test
    void readsTheTimeOfDayInTheCallersZoneAndWrapsPastMidnight() {
        // 23:58 in Singapore is 15:58 UTC on the same day.
        Instant lateDinner = at(TODAY.minusDays(1), 23, 58);

        meal(user.userId(), MealType.DINNER, lateDinner, oats());

        assertEquals("00:00", single(service.getUsualMeals(user, SGT)).usualTime());
        assertEquals("16:00", single(service.getUsualMeals(user, "UTC")).usualTime());
    }

    @Test
    void usesUtcWhenNoTimezoneIsGivenAndRejectsAnUnknownZone() {
        assertEquals("UTC", service.getUsualMeals(user, null).timezone());
        assertEquals("UTC", service.getUsualMeals(user, " ").timezone());
        assertThrows(BadRequestException.class, () -> service.getUsualMeals(user, "Mars/Olympus"));
    }

    @Test
    void listsAtMostTwelveItemsButTotalsEveryItemOfTheMeal() {
        for (int day = 1; day <= 3; day++) {
            List<FoodSpec> foods = new ArrayList<>();
            for (int n = 0; n < 14; n++) {
                foods.add(item(UUID.nameUUIDFromBytes(("food-" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        null, "Food " + n, 10, "1.00", "1.00", "1.00", null, "1.00"));
            }
            meal(user.userId(), MealType.LUNCH, at(TODAY.minusDays(day), 12, 0), foods.toArray(new FoodSpec[0]));
        }

        UsualMealView lunch = single(service.getUsualMeals(user, SGT));

        assertEquals(12, lunch.items().size());
        assertEquals(140, lunch.totalCalories());
        assertEquals(new BigDecimal("14.0"), lunch.totalProteinGrams());
    }

    @Test
    void ignoresAMealWithoutItemsWhenLookingForACombinationButStillCountsItsTime() {
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(1), 16, 0));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(2), 16, 0));
        meal(user.userId(), MealType.SNACK, at(TODAY.minusDays(3), 16, 0));

        UsualMealView snack = single(service.getUsualMeals(user, SGT));

        assertEquals(3, snack.mealsInWindow());
        assertEquals(0, snack.occurrences());
        assertEquals("16:00", snack.usualTime());
    }

    private static UsualMealView single(UsualMealsView view) {
        assertEquals(1, view.meals().size());
        return view.meals().get(0);
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).atZone(SGT_ZONE).toInstant();
    }

    private UUID mealIdOf(LocalDate date, MealType type) {
        return mealLogRepository.findAll().stream()
                .filter(meal -> meal.getUserId().equals(user.userId()) && meal.getMealType() == type)
                .filter(meal -> LocalDate.ofInstant(meal.getConsumedAt(), SGT_ZONE).equals(date))
                .map(MealLogEntity::getMealId)
                .findFirst()
                .orElseThrow();
    }

    private static FoodSpec oats() {
        return item(OATS, null, "Rolled Oats", 190, "6.50", "33.25", "3.45", "GRAINS", "1.00");
    }

    private static FoodSpec milk() {
        return item(MILK, null, "Milk", 120, "8.05", "11.05", "4.05", null, "1.00");
    }

    private static FoodSpec food(UUID catalogId, String name, int calories) {
        return item(catalogId, null, name, calories, "5.00", "10.00", "2.00", null, "1.00");
    }

    private static FoodSpec item(UUID catalogId, UUID customId, String name, int calories, String protein,
                                 String carbs, String fat, String category, String quantity) {
        return new FoodSpec(catalogId, customId, name, calories, new BigDecimal(protein), new BigDecimal(carbs),
                new BigDecimal(fat), category, new BigDecimal(quantity));
    }

    private UUID meal(UUID userId, MealType type, Instant consumedAt, FoodSpec... foods) {
        MealLogEntity meal = new MealLogEntity();
        meal.setMealId(UUID.randomUUID());
        meal.setUserId(userId);
        meal.setMealType(type);
        meal.setSource(MealSource.MANUAL);
        meal.setConsumedAt(consumedAt);
        mealLogRepository.save(meal);
        for (FoodSpec food : foods) {
            MealItemEntity item = new MealItemEntity();
            item.setMealItemId(UUID.randomUUID());
            item.setMealId(meal.getMealId());
            item.setFoodCatalogItemId(food.catalogId());
            item.setCustomFoodId(food.customId());
            item.setFoodName(food.name());
            item.setQuantity(food.quantity());
            item.setUnit("serving");
            item.setCategoryCode(food.category());
            item.setCalories(food.calories());
            item.setProteinGrams(food.protein());
            item.setCarbsGrams(food.carbs());
            item.setFatGrams(food.fat());
            mealItemRepository.save(item);
        }
        return meal.getMealId();
    }

    private record FoodSpec(UUID catalogId, UUID customId, String name, int calories, BigDecimal protein,
                            BigDecimal carbs, BigDecimal fat, String category, BigDecimal quantity) {
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock usualMealsTestClock() {
            return Clock.fixed(Instant.parse("2026-09-30T04:00:00Z"), ZoneOffset.UTC);
        }
    }
}
