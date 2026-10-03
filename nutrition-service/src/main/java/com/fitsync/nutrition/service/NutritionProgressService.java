package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.NutrientSummaryItemView;
import com.fitsync.nutrition.dto.NutrientSummaryView;
import com.fitsync.nutrition.dto.NutritionProgressFoodView;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.Cooks;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.Habits;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.LastCook;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.LowNutrient;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.MealTime;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.TopFood;
import com.fitsync.nutrition.dto.NutritionProgressFoodView.Window;
import com.fitsync.nutrition.dto.RangeDayView;
import com.fitsync.nutrition.dto.RangeSummaryView;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.RecipeCookEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealItemRepository.LoggedFoodCountRow;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.RecipeCookRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The nutrition half of the Progress Food page (progress-redesign contract section 3.1): meal times, top foods,
 * cooks, the lowest nutrients and the fibre habit. Every query is scoped to the caller from the token.
 */
@Service
public class NutritionProgressService {

    static final int MAX_DAYS = 370;
    static final int TOP_FOODS = 5;
    static final int LOWEST_NUTRIENTS = 3;
    /** The longest range the nutrient and range summaries accept. */
    static final int RECENT_DAYS = 31;
    private static final String KIND_VITAMIN = "VITAMIN";
    private static final String KIND_MINERAL = "MINERAL";
    private static final String FOOD_GROUP_OTHER = "OTHER";

    private final MealLogRepository mealLogRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final RecipeCookRepository recipeCookRepository;
    private final RecipeRepository recipeRepository;
    private final NutritionDiaryService diaryService;

    public NutritionProgressService(MealLogRepository mealLogRepository,
                                    MealItemRepository mealItemRepository,
                                    FoodCategoryRepository foodCategoryRepository,
                                    RecipeCookRepository recipeCookRepository,
                                    RecipeRepository recipeRepository,
                                    NutritionDiaryService diaryService) {
        this.mealLogRepository = mealLogRepository;
        this.mealItemRepository = mealItemRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.recipeCookRepository = recipeCookRepository;
        this.recipeRepository = recipeRepository;
        this.diaryService = diaryService;
    }

    /** {@code from} and {@code to} are both included, at most 370 days apart; the zone defaults to UTC. */
    @Transactional(readOnly = true)
    public NutritionProgressFoodView getFood(CurrentUserPrincipal principal, LocalDate from, LocalDate to,
                                             String timezone) {
        validate(from, to);
        ZoneId zone = NutritionPageInsightServiceImpl.resolveZone(timezone);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        List<MealLogEntity> meals = mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                        principal.userId(), start, end.minusNanos(1));
        List<MealTime> mealTimes = mealTimes(meals, zone);
        List<TopFood> topFoods = topFoods(mealItemRepository.countLoggedFoods(principal.userId(), start, end));

        long cookCount = recipeCookRepository
                .countByUserIdAndDeletedAtIsNullAndFinishedAtGreaterThanEqualAndFinishedAtLessThan(principal.userId(),
                        start, end);
        LastCook last = recipeCookRepository
                .findFirstByUserIdAndDeletedAtIsNullAndFinishedAtGreaterThanEqualAndFinishedAtLessThanOrderByFinishedAtDesc(
                        principal.userId(), start, end)
                .map(this::lastCook)
                .orElse(null);

        LocalDate recentFrom = to.minusDays(RECENT_DAYS - 1L).isAfter(from) ? to.minusDays(RECENT_DAYS - 1L) : from;
        NutrientSummaryView nutrients = diaryService.getNutrientSummary(principal, recentFrom, to, zone.getId());
        RangeSummaryView range = diaryService.getRangeSummary(principal, recentFrom, to, zone.getId());
        int loggedDays = 0;
        int fibreMet = 0;
        for (RangeDayView day : range.days()) {
            if (day.logged()) {
                loggedDays++;
                if (Boolean.TRUE.equals(day.fiberMet())) {
                    fibreMet++;
                }
            }
        }

        return new NutritionProgressFoodView(from, to, mealTimes, topFoods, new Cooks(cookCount, last),
                new Window(recentFrom, to), lowestNutrients(nutrients), new Habits(loggedDays, fibreMet));
    }

    static void validate(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from must not be after to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new BadRequestException("The period must not exceed " + MAX_DAYS + " days");
        }
    }

    /** Median and quartiles of the minute of the day per meal type, in the meal types' own order. */
    static List<MealTime> mealTimes(List<MealLogEntity> meals, ZoneId zone) {
        Map<MealType, List<Integer>> minutes = new EnumMap<>(MealType.class);
        for (MealLogEntity meal : meals) {
            if (meal.getMealType() == null || meal.getConsumedAt() == null) {
                continue;
            }
            LocalTime time = meal.getConsumedAt().atZone(zone).toLocalTime();
            minutes.computeIfAbsent(meal.getMealType(), key -> new ArrayList<>()).add(time.getHour() * 60 + time.getMinute());
        }
        List<MealTime> result = new ArrayList<>();
        minutes.forEach((type, values) -> {
            Collections.sort(values);
            result.add(new MealTime(type.name(), quantile(values, 0.5), quantile(values, 0.25), quantile(values, 0.75),
                    values.size()));
        });
        return result;
    }

    /** The q-quantile of sorted values, interpolating linearly between neighbours, rounded half up. */
    static int quantile(List<Integer> sorted, double q) {
        double position = q * (sorted.size() - 1);
        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);
        double value = sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * Merges the grouped rows into one entry per food (the catalogue id, else the custom food id, else the name
     * ignoring case), keeps the five logged most often and names each by its most frequent spelling.
     */
    private List<TopFood> topFoods(List<LoggedFoodCountRow> rows) {
        Map<String, FoodTally> byFood = new LinkedHashMap<>();
        for (LoggedFoodCountRow row : rows) {
            String key = row.getFoodCatalogItemId() != null ? "C:" + row.getFoodCatalogItemId()
                    : row.getCustomFoodId() != null ? "U:" + row.getCustomFoodId()
                    : "N:" + Objects.requireNonNullElse(row.getFoodName(), "").trim().toLowerCase(Locale.ROOT);
            byFood.computeIfAbsent(key, k -> new FoodTally()).add(row);
        }
        List<FoodTally> top = byFood.values().stream()
                .sorted(Comparator.comparingLong((FoodTally tally) -> tally.times).reversed()
                        .thenComparing(tally -> tally.lastConsumedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_FOODS)
                .toList();
        List<String> codes = top.stream().map(tally -> tally.categoryCode).filter(Objects::nonNull).distinct().toList();
        Map<String, String> groups = new HashMap<>();
        if (!codes.isEmpty()) {
            for (FoodCategoryEntity category : foodCategoryRepository.findByCodeInAndActiveTrue(codes)) {
                groups.put(category.getCode(), category.getFoodGroup());
            }
        }
        return top.stream()
                .map(tally -> new TopFood(tally.foodCatalogItemId, tally.name,
                        tally.categoryCode == null ? FOOD_GROUP_OTHER : groups.getOrDefault(tally.categoryCode, FOOD_GROUP_OTHER),
                        tally.categoryCode, tally.times))
                .toList();
    }

    private LastCook lastCook(RecipeCookEntity cook) {
        String name = recipeRepository.findById(cook.getRecipeId()).map(RecipeEntity::getName).orElse(null);
        return new LastCook(cook.getRecipeId(), name, cook.getFinishedAt());
    }

    /** The same rule as the recipe fit: vitamins and minerals with a share and a positive reference, lowest first. */
    static List<LowNutrient> lowestNutrients(NutrientSummaryView summary) {
        if (summary.daysLogged() <= 0) {
            return List.of();
        }
        return summary.nutrients().stream()
                .filter(item -> KIND_VITAMIN.equals(item.kind()) || KIND_MINERAL.equals(item.kind()))
                .filter(item -> item.share() != null && item.reference() != null && item.reference().signum() > 0)
                .sorted(Comparator.comparing(NutrientSummaryItemView::share).thenComparing(NutrientSummaryItemView::code))
                .limit(LOWEST_NUTRIENTS)
                .map(item -> new LowNutrient(item.code(), item.displayName(), item.share()))
                .toList();
    }

    /** One food's rows added together. */
    private static final class FoodTally {
        private java.util.UUID foodCatalogItemId;
        private String name;
        private long nameTimes;
        private String categoryCode;
        private long times;
        private Instant lastConsumedAt;

        void add(LoggedFoodCountRow row) {
            foodCatalogItemId = row.getFoodCatalogItemId();
            times += row.getTimes();
            if (row.getTimes() > nameTimes) {
                nameTimes = row.getTimes();
                name = row.getFoodName();
            }
            if (categoryCode == null) {
                categoryCode = row.getCategoryCode();
            }
            if (lastConsumedAt == null || (row.getLastConsumedAt() != null && row.getLastConsumedAt().isAfter(lastConsumedAt))) {
                lastConsumedAt = row.getLastConsumedAt();
            }
        }
    }
}
