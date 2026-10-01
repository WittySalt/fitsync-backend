package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

import com.fitsync.nutrition.client.PersonBasics;
import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.DrinkWaterEntryView;
import com.fitsync.nutrition.dto.NutrientBalanceView;
import com.fitsync.nutrition.dto.NutrientCoverageView;
import com.fitsync.nutrition.dto.NutrientSummaryItemView;
import com.fitsync.nutrition.dto.NutrientSummaryView;
import com.fitsync.nutrition.dto.ProteinByMealView;
import com.fitsync.nutrition.dto.ProteinMealView;
import com.fitsync.nutrition.dto.RangeDayView;
import com.fitsync.nutrition.dto.RangeSummaryView;
import com.fitsync.nutrition.dto.TopSourceView;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.NutrientEntity;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.entity.WaterLogEntity;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.DiaryItemRow;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealNutrientTotalRow;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealTotalsRow;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.NutrientDetailRow;
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.NutrientTotalRow;
import com.fitsync.nutrition.repository.NutrientRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.repository.WaterLogRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutrientReferenceService.PersonReferences;
import com.fitsync.web.error.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionDiaryServiceImpl implements NutritionDiaryService {

    private static final int MAX_RANGE_DAYS = 31;
    private static final int USUAL_FOODS = 20;
    private static final int USUAL_FOODS_WINDOW_DAYS = 30;
    private static final int TOP_SOURCES = 3;
    private static final String FOOD_GROUP_DRINKS = "DRINKS";
    private static final String FIBER = "FIBER";
    private static final String POTASSIUM = "POTASSIUM";
    private static final String SUGAR = "SUGAR";
    private static final String SAT_FAT = "SAT_FAT";
    private static final String SODIUM = "SODIUM";
    private static final String KIND_TARGET = "TARGET";
    private static final String KIND_LIMIT = "LIMIT";
    private static final List<String> FOCUS_CODES = List.of("VIT_B12", "IRON", "ZINC");
    private static final Set<String> FOCUS_DIETS = Set.of("VEGETARIAN", "VEGAN");
    private static final List<MealType> PROTEIN_MEAL_ORDER = List.of(
            MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER, MealType.SNACK);
    // Protein per meal is worth 0.3 to 0.4 grams per kilogram, or 20 to 40 grams when the weight is not known.
    private static final BigDecimal PROTEIN_LOW_PER_KG = new BigDecimal("0.3");
    private static final BigDecimal PROTEIN_HIGH_PER_KG = new BigDecimal("0.4");
    private static final int PROTEIN_LOW_DEFAULT_GRAMS = 20;
    private static final int PROTEIN_HIGH_DEFAULT_GRAMS = 40;

    private final MealNutritionQueryRepository mealQueries;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final WaterLogRepository waterLogRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final NutrientRepository nutrientRepository;
    private final NutrientReferenceService referenceService;
    private final PersonLookup personLookup;

    public NutritionDiaryServiceImpl(MealNutritionQueryRepository mealQueries,
                                     NutritionTargetRepository nutritionTargetRepository,
                                     WaterLogRepository waterLogRepository,
                                     FoodCategoryRepository foodCategoryRepository,
                                     NutrientRepository nutrientRepository,
                                     NutrientReferenceService referenceService,
                                     PersonLookup personLookup) {
        this.mealQueries = mealQueries;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.waterLogRepository = waterLogRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.nutrientRepository = nutrientRepository;
        this.referenceService = referenceService;
        this.personLookup = personLookup;
    }

    // ---------------------------------------------------------------------------------------------
    // Day summary

    @Override
    @Transactional(readOnly = true)
    public DailyNutritionSummaryView getDailySummary(CurrentUserPrincipal principal, LocalDate date, String timezone) {
        ZoneId zone = resolveZone(timezone);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        UUID userId = principal.userId();

        List<DiaryItemRow> rows = mealQueries.findDiaryItemRows(userId, from, to);
        Map<UUID, BigDecimal> potassiumByItem = new HashMap<>();
        for (NutrientDetailRow detail : mealQueries.findNutrientDetailRows(userId, from, to, List.of(POTASSIUM))) {
            potassiumByItem.merge(detail.getMealItemId(), detail.getAmount(), BigDecimal::add);
        }
        Map<String, String> foodGroups = loadFoodGroups();

        Set<UUID> mealIds = new HashSet<>();
        int totalCalories = 0;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal fiber = BigDecimal.ZERO;
        BigDecimal sugar = BigDecimal.ZERO;
        BigDecimal sodium = BigDecimal.ZERO;
        BigDecimal saturatedFat = BigDecimal.ZERO;
        BigDecimal potassium = BigDecimal.ZERO;
        BigDecimal sugarFromDrinks = BigDecimal.ZERO;
        int waterFromDrinks = 0;
        Map<MealType, BigDecimal> proteinByType = new EnumMap<>(MealType.class);
        Map<String, Map<String, SourceTotal>> sources = new HashMap<>();
        for (DiaryItemRow row : rows) {
            mealIds.add(row.getMealId());
            if (row.getMealItemId() == null) {
                continue;
            }
            totalCalories += row.getCalories() == null ? 0 : row.getCalories();
            protein = protein.add(orZero(row.getProteinGrams()));
            carbs = carbs.add(orZero(row.getCarbsGrams()));
            fat = fat.add(orZero(row.getFatGrams()));
            fiber = fiber.add(orZero(row.getFiberGrams()));
            sugar = sugar.add(orZero(row.getSugarGrams()));
            sodium = sodium.add(orZero(row.getSodiumMg()));
            saturatedFat = saturatedFat.add(orZero(row.getSaturatedFatGrams()));
            BigDecimal itemPotassium = potassiumByItem.getOrDefault(row.getMealItemId(), BigDecimal.ZERO);
            potassium = potassium.add(itemPotassium);
            waterFromDrinks += row.getWaterMl() == null ? 0 : row.getWaterMl();
            if (row.getCategoryCode() != null && FOOD_GROUP_DRINKS.equals(foodGroups.get(row.getCategoryCode()))) {
                sugarFromDrinks = sugarFromDrinks.add(orZero(row.getSugarGrams()));
            }
            proteinByType.merge(row.getMealType(), orZero(row.getProteinGrams()), BigDecimal::add);
            addSource(sources, FIBER, row, row.getFiberGrams());
            addSource(sources, POTASSIUM, row, itemPotassium);
            addSource(sources, SUGAR, row, row.getSugarGrams());
            addSource(sources, SAT_FAT, row, row.getSaturatedFatGrams());
            addSource(sources, SODIUM, row, row.getSodiumMg());
        }

        int plainWaterMl = waterLogRepository
                .findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThanEqualOrderByLoggedAtAsc(
                        userId, from, to.minusNanos(1))
                .stream()
                .mapToInt(WaterLogEntity::getAmountMl)
                .sum();

        NutritionTargetEntity target = nutritionTargetRepository.findById(userId).orElse(null);
        Integer targetCalories = target == null ? null : target.getCalorieTarget();
        BigDecimal targetProtein = target == null ? null : target.getProteinTarget();
        BigDecimal targetCarbs = target == null ? null : target.getCarbTargetGrams();
        BigDecimal targetFat = target == null ? null : target.getFatTargetGrams();

        PersonReferences references = referenceService.forPerson(personLookup.basics());
        Map<String, NutrientEntity> nutrients = loadNutrients();
        BigDecimal fiberTarget = references.target(FIBER);
        BigDecimal sugarLimit = references.limit(SUGAR);
        BigDecimal sodiumLimit = references.limit(SODIUM);

        List<NutrientBalanceView> balance = List.of(
                balanceLine(FIBER, KIND_TARGET, fiber, references.target(FIBER), nutrients, sources),
                balanceLine(POTASSIUM, KIND_TARGET, potassium, references.target(POTASSIUM), nutrients, sources),
                balanceLine(SUGAR, KIND_LIMIT, sugar, references.limit(SUGAR), nutrients, sources),
                balanceLine(SAT_FAT, KIND_LIMIT, saturatedFat, references.limit(SAT_FAT), nutrients, sources),
                balanceLine(SODIUM, KIND_LIMIT, sodium, references.limit(SODIUM), nutrients, sources));

        return new DailyNutritionSummaryView(
                date,
                zone.getId(),
                totalCalories,
                protein,
                carbs,
                fat,
                targetCalories,
                targetProtein,
                targetCarbs,
                targetFat,
                targetCalories == null ? null : targetCalories - totalCalories,
                targetProtein == null ? null : targetProtein.subtract(protein),
                targetCarbs == null ? null : targetCarbs.subtract(carbs),
                targetFat == null ? null : targetFat.subtract(fat),
                mealIds.size(),
                scaleTwo(fiber),
                scaleTwo(sugar),
                scaleTwo(sodium),
                fiberTarget,
                sugarLimit,
                sodiumLimit,
                reaches(fiber, fiberTarget),
                staysWithin(sugar, sugarLimit),
                staysWithin(sodium, sodiumLimit),
                scaleTwo(saturatedFat),
                scaleTwo(potassium),
                plainWaterMl,
                waterFromDrinks,
                scaleTwo(sugarFromDrinks),
                balance,
                proteinByMeal(proteinByType));
    }

    private NutrientBalanceView balanceLine(String code, String kind, BigDecimal amount, BigDecimal reference,
                                            Map<String, NutrientEntity> nutrients,
                                            Map<String, Map<String, SourceTotal>> sources) {
        NutrientEntity nutrient = nutrients.get(code);
        boolean isTarget = KIND_TARGET.equals(kind);
        List<TopSourceView> top = sources.getOrDefault(code, Map.of()).values().stream()
                .sorted(Comparator.comparing((SourceTotal source) -> source.amount).reversed()
                        .thenComparing(source -> source.foodName, Comparator.nullsLast(String::compareTo)))
                .limit(TOP_SOURCES)
                .map(source -> new TopSourceView(source.foodName, scaleTwo(source.amount)))
                .toList();
        return new NutrientBalanceView(
                code,
                nutrient == null ? code : nutrient.getDisplayName(),
                kind,
                scaleTwo(amount),
                nutrient == null ? null : nutrient.getUnit(),
                reference,
                shareOf(amount, reference),
                isTarget && reaches(amount, reference),
                !isTarget && reference != null && amount.compareTo(reference) > 0,
                top);
    }

    private ProteinByMealView proteinByMeal(Map<MealType, BigDecimal> proteinByType) {
        BigDecimal weight = personLookup.latestWeightKg();
        int low = PROTEIN_LOW_DEFAULT_GRAMS;
        int high = PROTEIN_HIGH_DEFAULT_GRAMS;
        BigDecimal reportedWeight = null;
        if (weight != null && weight.signum() > 0) {
            reportedWeight = weight.setScale(1, RoundingMode.HALF_UP);
            low = weight.multiply(PROTEIN_LOW_PER_KG).setScale(0, RoundingMode.HALF_UP).intValue();
            high = weight.multiply(PROTEIN_HIGH_PER_KG).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        List<ProteinMealView> meals = new ArrayList<>();
        for (MealType type : PROTEIN_MEAL_ORDER) {
            meals.add(new ProteinMealView(type, scaleTwo(proteinByType.getOrDefault(type, BigDecimal.ZERO))));
        }
        return new ProteinByMealView(reportedWeight, low, high, meals);
    }

    private void addSource(Map<String, Map<String, SourceTotal>> sources, String nutrientCode, DiaryItemRow row,
                           BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        sources.computeIfAbsent(nutrientCode, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(foodKey(row), ignored -> new SourceTotal(row.getFoodName()))
                .add(amount);
    }

    // ---------------------------------------------------------------------------------------------
    // Drinks that count toward water

    @Override
    @Transactional(readOnly = true)
    public List<DrinkWaterEntryView> listDrinkWater(UUID userId, Instant from, Instant to) {
        return mealQueries.findDiaryItemRows(userId, from, to).stream()
                .filter(row -> row.getMealItemId() != null && row.getWaterMl() != null && row.getWaterMl() > 0)
                .map(row -> new DrinkWaterEntryView(
                        row.getMealItemId(), row.getMealId(), row.getFoodName(), row.getWaterMl(), row.getConsumedAt()))
                .toList();
    }

    // ---------------------------------------------------------------------------------------------
    // Range of days

    @Override
    @Transactional(readOnly = true)
    public RangeSummaryView getRangeSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to,
                                            String timezone) {
        ZoneId zone = resolveZone(timezone);
        int days = validateRange(from, to);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        UUID userId = principal.userId();

        Map<LocalDate, DayAccumulator> byDay = new HashMap<>();
        for (MealTotalsRow meal : mealQueries.findMealTotals(userId, start, end)) {
            DayAccumulator day = byDay.computeIfAbsent(localDate(meal.getConsumedAt(), zone), d -> new DayAccumulator());
            day.meals++;
            day.calories += meal.getCalories() == null ? 0 : meal.getCalories().intValue();
            day.fiber = day.fiber.add(orZero(meal.getFiberGrams()));
            day.sugar = day.sugar.add(orZero(meal.getSugarGrams()));
            day.sodium = day.sodium.add(orZero(meal.getSodiumMg()));
            day.saturatedFat = day.saturatedFat.add(orZero(meal.getSaturatedFatGrams()));
        }
        for (MealNutrientTotalRow row : mealQueries.findMealNutrientTotals(userId, start, end, POTASSIUM)) {
            DayAccumulator day = byDay.get(localDate(row.getConsumedAt(), zone));
            if (day != null) {
                day.potassium = day.potassium.add(orZero(row.getTotal()));
            }
        }

        NutritionTargetEntity target = nutritionTargetRepository.findById(userId).orElse(null);
        Integer targetCalories = target == null ? null : target.getCalorieTarget();
        PersonReferences references = referenceService.forPerson(personLookup.basics());

        List<RangeDayView> result = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = from.plusDays(offset);
            DayAccumulator day = byDay.get(date);
            if (day == null) {
                result.add(new RangeDayView(date, false, 0, 0, targetCalories,
                        scaleTwo(BigDecimal.ZERO), scaleTwo(BigDecimal.ZERO), scaleTwo(BigDecimal.ZERO),
                        scaleTwo(BigDecimal.ZERO), scaleTwo(BigDecimal.ZERO), null, null, null, null, null));
                continue;
            }
            result.add(new RangeDayView(date, true, day.meals, day.calories, targetCalories,
                    scaleTwo(day.fiber), scaleTwo(day.potassium), scaleTwo(day.sugar),
                    scaleTwo(day.saturatedFat), scaleTwo(day.sodium),
                    reaches(day.fiber, references.target(FIBER)),
                    reaches(day.potassium, references.target(POTASSIUM)),
                    staysWithin(day.sugar, references.limit(SUGAR)),
                    staysWithin(day.saturatedFat, references.limit(SAT_FAT)),
                    staysWithin(day.sodium, references.limit(SODIUM))));
        }
        return new RangeSummaryView(from, to, result);
    }

    // ---------------------------------------------------------------------------------------------
    // Vitamins and minerals

    @Override
    @Transactional(readOnly = true)
    public NutrientSummaryView getNutrientSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to,
                                                  String timezone) {
        ZoneId zone = resolveZone(timezone);
        int days = validateRange(from, to);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        UUID userId = principal.userId();

        Set<LocalDate> loggedDates = new HashSet<>();
        for (MealTotalsRow meal : mealQueries.findMealTotals(userId, start, end)) {
            loggedDates.add(localDate(meal.getConsumedAt(), zone));
        }
        int daysLogged = loggedDates.size();

        PersonBasics basics = personLookup.basics();
        PersonReferences references = referenceService.forPerson(basics);
        String diet = personLookup.dietaryPattern();
        List<String> focusCodes = diet != null && FOCUS_DIETS.contains(diet) ? FOCUS_CODES : List.of();

        List<NutrientEntity> listed = new ArrayList<>();
        for (NutrientEntity nutrient : nutrientRepository.findAllByOrderBySortOrderAsc()) {
            if (nutrient.isShownInDiary() || focusCodes.contains(nutrient.getCode())) {
                listed.add(nutrient);
            }
        }
        List<String> codes = listed.stream().map(NutrientEntity::getCode).toList();

        Map<String, BigDecimal> totals = new HashMap<>();
        for (NutrientTotalRow row : mealQueries.findNutrientTotals(userId, start, end, codes)) {
            totals.put(row.getNutrientCode(), orZero(row.getTotal()));
        }
        Map<String, String> richest = richestUsualFoods(userId, to, zone, codes);

        List<NutrientSummaryItemView> items = new ArrayList<>();
        for (NutrientEntity nutrient : listed) {
            String code = nutrient.getCode();
            BigDecimal average = daysLogged == 0
                    ? BigDecimal.ZERO
                    : totals.getOrDefault(code, BigDecimal.ZERO)
                            .divide(BigDecimal.valueOf(daysLogged), 2, RoundingMode.HALF_UP);
            BigDecimal reference = references.target(code);
            BigDecimal upper = references.upper(code);
            items.add(new NutrientSummaryItemView(
                    code,
                    nutrient.getDisplayName(),
                    nutrient.getKind(),
                    nutrient.getUnit(),
                    average,
                    reference,
                    shareOf(average, reference),
                    daysLogged > 0 && reaches(average, reference),
                    upper,
                    upper != null && average.compareTo(upper) > 0,
                    richest.get(code)));
        }
        items.sort(Comparator
                .comparingInt((NutrientSummaryItemView item) -> focusOrder(focusCodes, item.code()))
                .thenComparing(NutrientSummaryItemView::share, Comparator.nullsLast(Comparator.naturalOrder())));

        NutrientCoverageView coverage = new NutrientCoverageView(
                mealQueries.countItems(userId, start, end),
                mealQueries.countItemsWithoutNutrients(userId, start, end));
        return new NutrientSummaryView(from, to, days, daysLogged, references.label(), focusCodes, coverage, items);
    }

    /**
     * For each nutrient, the food among the person's most frequent ones of the last 30 days that gives the most
     * of it per usual portion. A usual portion is what the food's logged items held on average, and an item with
     * no value for the nutrient counts as zero. A nutrient no usual food gives any of is left out of the map.
     */
    private Map<String, String> richestUsualFoods(UUID userId, LocalDate to, ZoneId zone, List<String> codes) {
        Instant windowStart = to.minusDays(USUAL_FOODS_WINDOW_DAYS - 1L).atStartOfDay(zone).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        Map<String, UsualFood> foods = new LinkedHashMap<>();
        Map<UUID, String> keyByItem = new HashMap<>();
        for (DiaryItemRow row : mealQueries.findDiaryItemRows(userId, windowStart, windowEnd)) {
            if (row.getMealItemId() == null) {
                continue;
            }
            String key = foodKey(row);
            keyByItem.put(row.getMealItemId(), key);
            foods.computeIfAbsent(key, ignored -> new UsualFood(row.getFoodName())).items++;
        }
        List<UsualFood> usual = foods.values().stream()
                .sorted(Comparator.comparingInt((UsualFood food) -> food.items).reversed()
                        .thenComparing(food -> food.name, Comparator.nullsLast(String::compareTo)))
                .limit(USUAL_FOODS)
                .toList();
        if (usual.isEmpty() || codes.isEmpty()) {
            return Map.of();
        }
        for (NutrientDetailRow detail : mealQueries.findNutrientDetailRows(userId, windowStart, windowEnd, codes)) {
            UsualFood food = foods.get(keyByItem.get(detail.getMealItemId()));
            if (food != null) {
                food.amounts.merge(detail.getNutrientCode(), orZero(detail.getAmount()), BigDecimal::add);
            }
        }
        Map<String, String> result = new HashMap<>();
        for (String code : codes) {
            UsualFood best = null;
            BigDecimal bestPerPortion = BigDecimal.ZERO;
            for (UsualFood food : usual) {
                BigDecimal perPortion = food.amounts.getOrDefault(code, BigDecimal.ZERO)
                        .divide(BigDecimal.valueOf(food.items), 4, RoundingMode.HALF_UP);
                if (perPortion.compareTo(bestPerPortion) > 0) {
                    best = food;
                    bestPerPortion = perPortion;
                }
            }
            if (best != null) {
                result.put(code, best.name);
            }
        }
        return result;
    }

    private int focusOrder(List<String> focusCodes, String code) {
        int index = focusCodes.indexOf(code);
        return index < 0 ? focusCodes.size() : index;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers

    private int validateRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BadRequestException("The range must not exceed " + MAX_RANGE_DAYS + " days");
        }
        return (int) days;
    }

    private Map<String, NutrientEntity> loadNutrients() {
        Map<String, NutrientEntity> byCode = new HashMap<>();
        for (NutrientEntity nutrient : nutrientRepository.findAll()) {
            byCode.put(nutrient.getCode(), nutrient);
        }
        return byCode;
    }

    private Map<String, String> loadFoodGroups() {
        Map<String, String> groups = new HashMap<>();
        for (FoodCategoryEntity category : foodCategoryRepository.findAll()) {
            groups.put(category.getCode(), category.getFoodGroup());
        }
        return groups;
    }

    private static LocalDate localDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }

    private static boolean reaches(BigDecimal amount, BigDecimal target) {
        return target != null && amount.compareTo(target) >= 0;
    }

    private static boolean staysWithin(BigDecimal amount, BigDecimal limit) {
        return limit == null || amount.compareTo(limit) <= 0;
    }

    private static BigDecimal shareOf(BigDecimal amount, BigDecimal reference) {
        if (reference == null || reference.signum() <= 0) {
            return null;
        }
        return amount.divide(reference, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaleTwo(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String foodKey(DiaryItemRow row) {
        if (row.getFoodCatalogItemId() != null) {
            return "C:" + row.getFoodCatalogItemId();
        }
        if (row.getCustomFoodId() != null) {
            return "U:" + row.getCustomFoodId();
        }
        String name = row.getFoodName() == null ? "" : row.getFoodName().trim().toLowerCase(Locale.ROOT);
        return "N:" + name;
    }

    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid timezone");
        }
    }

    /** A food with the amount it gave, merged over all the items of that food. */
    private static final class SourceTotal {
        private final String foodName;
        private BigDecimal amount = BigDecimal.ZERO;

        private SourceTotal(String foodName) {
            this.foodName = foodName;
        }

        private void add(BigDecimal value) {
            amount = amount.add(value);
        }
    }

    /** A day of a range, summed from its meals. */
    private static final class DayAccumulator {
        private int meals;
        private int calories;
        private BigDecimal fiber = BigDecimal.ZERO;
        private BigDecimal potassium = BigDecimal.ZERO;
        private BigDecimal sugar = BigDecimal.ZERO;
        private BigDecimal saturatedFat = BigDecimal.ZERO;
        private BigDecimal sodium = BigDecimal.ZERO;
    }

    /** A food of the usual-foods window: its name, how many items it has and the nutrient amounts on them. */
    private static final class UsualFood {
        private final String name;
        private int items;
        private final Map<String, BigDecimal> amounts = new HashMap<>();

        private UsualFood(String name) {
            this.name = name;
        }
    }
}
