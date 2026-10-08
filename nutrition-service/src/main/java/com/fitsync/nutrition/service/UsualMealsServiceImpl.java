package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.UsualMealItemView;
import com.fitsync.nutrition.dto.UsualMealView;
import com.fitsync.nutrition.dto.UsualMealsView;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealItemRow;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the person's habitual meals over the last two weeks: for each meal type, the usual time of day and the
 * combination of foods eaten together most often, shown with the nutrition of the latest meal that had it so the
 * card can offer to log it again.
 */
@Service
public class UsualMealsServiceImpl implements UsualMealsService {

    private static final int WINDOW_DAYS = 14;
    /** A combination has to recur this often in the window to count as a habit. */
    private static final int MIN_OCCURRENCES = 3;
    /** The card lists at most this many foods, though the totals cover the whole meal. */
    private static final int MAX_ITEMS = 12;
    private static final int TIME_STEP_MINUTES = 5;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final Clock clock;
    private final MealNutritionQueryRepository mealNutritionQueryRepository;

    public UsualMealsServiceImpl(Clock clock, MealNutritionQueryRepository mealNutritionQueryRepository) {
        this.clock = clock;
        this.mealNutritionQueryRepository = mealNutritionQueryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UsualMealsView getUsualMeals(CurrentUserPrincipal principal, String timezone) {
        ZoneId zoneId = NutritionPageInsightServiceImpl.resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);

        // Today is left out so that a meal just logged does not count toward its own habit.
        List<MealItemRow> rows = mealNutritionQueryRepository.findMealItemRows(
                principal.userId(),
                today.minusDays(WINDOW_DAYS).atStartOfDay(zoneId).toInstant(),
                today.atStartOfDay(zoneId).toInstant());

        Map<MealType, List<Meal>> byType = new EnumMap<>(MealType.class);
        for (Meal meal : groupByMeal(rows, zoneId)) {
            byType.computeIfAbsent(meal.type, type -> new ArrayList<>()).add(meal);
        }

        // The enum runs breakfast, lunch, dinner and then the remaining types, which is the order of a day.
        List<UsualMealView> meals = new ArrayList<>();
        for (MealType type : MealType.values()) {
            List<Meal> ofType = byType.get(type);
            if (ofType != null && type != MealType.FAST) {
                meals.add(describe(type, ofType));
            }
        }
        return new UsualMealsView(zoneId.getId(), WINDOW_DAYS, meals);
    }

    /** Folds the item rows back into meals. The rows arrive newest meal first and keep that order. */
    private static List<Meal> groupByMeal(List<MealItemRow> rows, ZoneId zoneId) {
        Map<UUID, Meal> meals = new LinkedHashMap<>();
        for (MealItemRow row : rows) {
            Meal meal = meals.computeIfAbsent(row.getMealId(), id -> new Meal(
                    id, row.getMealType(), row.getConsumedAt(),
                    LocalTime.ofInstant(row.getConsumedAt(), zoneId).toSecondOfDay()));
            if (row.getFoodName() != null) {
                meal.items.add(row);
            }
        }
        return new ArrayList<>(meals.values());
    }

    private static UsualMealView describe(MealType type, List<Meal> meals) {
        String label = NutritionPageInsightServiceImpl.labelOf(type);
        String usualTime = usualTime(meals);
        Combination usual = usualCombination(meals);
        if (usual == null) {
            return new UsualMealView(type, label, usualTime, meals.size(), 0, null, null, null, null, null, List.of());
        }

        Meal source = usual.latest;
        int calories = 0;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        List<UsualMealItemView> items = new ArrayList<>();
        for (MealItemRow item : source.items) {
            int itemCalories = item.getCalories() == null ? 0 : item.getCalories();
            calories += itemCalories;
            protein = protein.add(orZero(item.getProteinGrams()));
            carbs = carbs.add(orZero(item.getCarbsGrams()));
            fat = fat.add(orZero(item.getFatGrams()));
            if (items.size() < MAX_ITEMS) {
                items.add(new UsualMealItemView(item.getFoodName(), itemCalories,
                        item.getProteinGrams() == null ? null : oneDecimal(item.getProteinGrams()),
                        item.getCategoryCode()));
            }
        }
        return new UsualMealView(type, label, usualTime, meals.size(), usual.count, source.mealId, calories,
                oneDecimal(protein), oneDecimal(carbs), oneDecimal(fat), items);
    }

    /** The median local time of day, rounded to the nearest five minutes and written as HH:mm. */
    private static String usualTime(List<Meal> meals) {
        int[] seconds = meals.stream().mapToInt(meal -> meal.secondOfDay).sorted().toArray();
        int middle = seconds.length / 2;
        double median = seconds.length % 2 == 1
                ? seconds[middle]
                : (seconds[middle - 1] + seconds[middle]) / 2.0;
        long steps = Math.round(median / 60.0 / TIME_STEP_MINUTES);
        int minutes = (int) (steps * TIME_STEP_MINUTES) % MINUTES_PER_DAY;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /**
     * The combination of foods that occurs in the most meals, provided it occurs at least three times.
     * A tie goes to the combination whose latest meal is the most recent.
     */
    private static Combination usualCombination(List<Meal> meals) {
        Map<Set<String>, Combination> combinations = new HashMap<>();
        for (Meal meal : meals) {
            Set<String> foods = meal.foodKeys();
            if (!foods.isEmpty()) {
                combinations.computeIfAbsent(foods, key -> new Combination()).add(meal);
            }
        }
        return combinations.values().stream()
                .filter(combination -> combination.count >= MIN_OCCURRENCES)
                .max(Comparator.<Combination>comparingInt(combination -> combination.count)
                        .thenComparing(combination -> combination.latest.consumedAt))
                .orElse(null);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal oneDecimal(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    /** The key that makes two logged foods the same food: its catalogue or custom id, else its name. */
    private static String foodKey(MealItemRow item) {
        if (item.getFoodCatalogItemId() != null) {
            return "catalog:" + item.getFoodCatalogItemId();
        }
        if (item.getCustomFoodId() != null) {
            return "custom:" + item.getCustomFoodId();
        }
        return "name:" + item.getFoodName().trim().toLowerCase(Locale.ROOT);
    }

    /** One meal with its items, as read from the window. */
    private static final class Meal {
        private final UUID mealId;
        private final MealType type;
        private final Instant consumedAt;
        private final int secondOfDay;
        private final List<MealItemRow> items = new ArrayList<>();

        private Meal(UUID mealId, MealType type, Instant consumedAt, int secondOfDay) {
            this.mealId = mealId;
            this.type = type;
            this.consumedAt = consumedAt;
            this.secondOfDay = secondOfDay;
        }

        private Set<String> foodKeys() {
            Set<String> keys = new HashSet<>();
            for (MealItemRow item : items) {
                keys.add(foodKey(item));
            }
            return keys;
        }
    }

    /** How often one set of foods was eaten, and the most recent meal that had it. */
    private static final class Combination {
        private int count;
        private Meal latest;

        private void add(Meal meal) {
            count++;
            if (latest == null || meal.consumedAt.isAfter(latest.consumedAt)) {
                latest = meal;
            }
        }
    }
}
