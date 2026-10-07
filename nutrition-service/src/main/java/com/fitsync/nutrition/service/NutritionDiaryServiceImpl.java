package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
import com.fitsync.nutrition.client.PersonFacts;
import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.DrinkWaterEntryView;
import com.fitsync.nutrition.dto.NutrientBalanceView;
import com.fitsync.nutrition.dto.NutrientCoverageView;
import com.fitsync.nutrition.dto.NutrientAmountRowView;
import com.fitsync.nutrition.dto.NutrientDayView;
import com.fitsync.nutrition.dto.NutrientPageView;
import com.fitsync.nutrition.dto.NutrientRichFoodView;
import com.fitsync.nutrition.dto.NutrientSourceView;
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
import com.fitsync.nutrition.repository.MealNutritionQueryRepository.MealCodeTotalRow;
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
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionDiaryServiceImpl implements NutritionDiaryService {

    private static final int MAX_RANGE_DAYS = 31;
    /** The nutrient table and one nutrient page also serve the 90 days of Progress. */
    private static final int MAX_NUTRIENT_RANGE_DAYS = 90;
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
    private static final String SCOPE_DIARY = "diary";
    private static final String SCOPE_ALL = "all";
    private static final int PAGE_SOURCES = 5;
    private static final int DEFAULT_PAGE_DAYS = 7;
    // Fibre, sugar, sodium and saturated fat are columns of the meal item, not snapshotted nutrient rows.
    private static final Set<String> COLUMN_CODES = Set.of(FIBER, SUGAR, SODIUM, SAT_FAT);
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
    private final NutrientPageSupport pageSupport;
    private final Clock clock;

    public NutritionDiaryServiceImpl(MealNutritionQueryRepository mealQueries,
                                     NutritionTargetRepository nutritionTargetRepository,
                                     WaterLogRepository waterLogRepository,
                                     FoodCategoryRepository foodCategoryRepository,
                                     NutrientRepository nutrientRepository,
                                     NutrientReferenceService referenceService,
                                     PersonLookup personLookup,
                                     NutrientPageSupport pageSupport,
                                     Clock clock) {
        this.mealQueries = mealQueries;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.waterLogRepository = waterLogRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.nutrientRepository = nutrientRepository;
        this.referenceService = referenceService;
        this.personLookup = personLookup;
        this.pageSupport = pageSupport;
        this.clock = clock;
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
        // These five stay null until an item carries a value, so that unknown is not reported as zero.
        BigDecimal fiber = null;
        BigDecimal sugar = null;
        BigDecimal sodium = null;
        BigDecimal saturatedFat = null;
        BigDecimal potassium = null;
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
            fiber = addKnown(fiber, row.getFiberGrams());
            sugar = addKnown(sugar, row.getSugarGrams());
            sodium = addKnown(sodium, row.getSodiumMg());
            saturatedFat = addKnown(saturatedFat, row.getSaturatedFatGrams());
            BigDecimal itemPotassium = potassiumByItem.get(row.getMealItemId());
            potassium = addKnown(potassium, itemPotassium);
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

        PersonFacts facts = personLookup.facts(userId);
        PersonReferences references = referenceService.forPerson(facts.basics());
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
                scaleTwo(orZero(fiber)),
                scaleTwo(orZero(sugar)),
                scaleTwo(orZero(sodium)),
                fiberTarget,
                sugarLimit,
                sodiumLimit,
                reaches(orZero(fiber), fiberTarget),
                staysWithin(orZero(sugar), sugarLimit),
                staysWithin(orZero(sodium), sodiumLimit),
                scaleTwoOrNull(saturatedFat),
                scaleTwoOrNull(potassium),
                plainWaterMl,
                waterFromDrinks,
                scaleTwo(sugarFromDrinks),
                balance,
                proteinByMeal(proteinByType, facts.weightKg()));
    }

    private NutrientBalanceView balanceLine(String code, String kind, BigDecimal amount, BigDecimal reference,
                                            Map<String, NutrientEntity> nutrients,
                                            Map<String, Map<String, SourceTotal>> sources) {
        NutrientEntity nutrient = nutrients.get(code);
        boolean known = amount != null;
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
                scaleTwoOrNull(amount),
                nutrient == null ? null : nutrient.getUnit(),
                reference,
                known ? shareOf(amount, reference) : null,
                known ? isTarget && reaches(amount, reference) : null,
                known ? !isTarget && reference != null && amount.compareTo(reference) > 0 : null,
                top);
    }

    private ProteinByMealView proteinByMeal(Map<MealType, BigDecimal> proteinByType, BigDecimal weight) {
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
                .computeIfAbsent(foodKey(row), ignored -> new SourceTotal(row.getFoodName(), row.getCategoryCode()))
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
            day.fiber = addKnown(day.fiber, meal.getFiberGrams());
            day.sugar = addKnown(day.sugar, meal.getSugarGrams());
            day.sodium = addKnown(day.sodium, meal.getSodiumMg());
            day.saturatedFat = addKnown(day.saturatedFat, meal.getSaturatedFatGrams());
        }
        for (MealNutrientTotalRow row : mealQueries.findMealNutrientTotals(userId, start, end, POTASSIUM)) {
            DayAccumulator day = byDay.get(localDate(row.getConsumedAt(), zone));
            if (day != null) {
                day.potassium = addKnown(day.potassium, row.getTotal());
            }
        }

        NutritionTargetEntity target = nutritionTargetRepository.findById(userId).orElse(null);
        Integer targetCalories = target == null ? null : target.getCalorieTarget();
        PersonReferences references = referenceService.forPerson(personLookup.facts(userId).basics());

        List<RangeDayView> result = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = from.plusDays(offset);
            DayAccumulator day = byDay.get(date);
            if (day == null) {
                result.add(new RangeDayView(date, false, 0, 0, targetCalories,
                        null, null, null, null, null, null, null, null, null, null));
                continue;
            }
            result.add(new RangeDayView(date, true, day.meals, day.calories, targetCalories,
                    scaleTwoOrNull(day.fiber), scaleTwoOrNull(day.potassium), scaleTwoOrNull(day.sugar),
                    scaleTwoOrNull(day.saturatedFat), scaleTwoOrNull(day.sodium),
                    day.fiber == null ? null : reaches(day.fiber, references.target(FIBER)),
                    day.potassium == null ? null : reaches(day.potassium, references.target(POTASSIUM)),
                    day.sugar == null ? null : staysWithin(day.sugar, references.limit(SUGAR)),
                    day.saturatedFat == null ? null : staysWithin(day.saturatedFat, references.limit(SAT_FAT)),
                    day.sodium == null ? null : staysWithin(day.sodium, references.limit(SODIUM))));
        }
        return new RangeSummaryView(from, to, result);
    }

    // ---------------------------------------------------------------------------------------------
    // Vitamins and minerals

    @Override
    @Transactional(readOnly = true)
    public NutrientSummaryView getNutrientSummary(CurrentUserPrincipal principal, LocalDate from, LocalDate to,
                                                  String timezone, String scope) {
        boolean all = parseScope(scope);
        ZoneId zone = resolveZone(timezone);
        int days = validateRange(from, to, MAX_NUTRIENT_RANGE_DAYS);
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        UUID userId = principal.userId();

        Set<LocalDate> loggedDates = loggedDates(userId, start, end, zone);
        int daysLogged = loggedDates.size();

        PersonFacts facts = personLookup.facts(userId);
        PersonBasics basics = facts.basics();
        PersonReferences references = referenceService.forPerson(basics);
        String diet = facts.dietaryPattern();
        List<String> focusCodes = !all && diet != null && FOCUS_DIETS.contains(diet) ? FOCUS_CODES : List.of();

        List<NutrientEntity> listed = new ArrayList<>();
        for (NutrientEntity nutrient : nutrientRepository.findAllByOrderBySortOrderAsc()) {
            boolean include = all
                    ? nutrient.getGroupCode() != null
                    : nutrient.isShownInDiary() || focusCodes.contains(nutrient.getCode());
            if (include) {
                listed.add(nutrient);
            }
        }
        if (all) {
            listed.sort(Comparator.comparingInt(NutrientEntity::getDisplayOrder));
        }
        List<String> codes = listed.stream().map(NutrientEntity::getCode).toList();

        NutrientAmounts amounts = loadAmounts(userId, start, end, zone, codes);
        Map<String, String> richest = richestUsualFoods(userId, to, zone,
                codes.stream().filter(code -> !COLUMN_CODES.contains(code)).toList());

        List<NutrientSummaryItemView> items = new ArrayList<>();
        for (NutrientEntity nutrient : listed) {
            String code = nutrient.getCode();
            items.add(summaryItem(nutrient, amounts.totals().get(code), daysLogged, references, richest.get(code),
                    from, to, loggedDates, amounts.daily().getOrDefault(code, Map.of())));
        }
        if (!all) {
            items.sort(Comparator
                    .comparingInt((NutrientSummaryItemView item) -> focusOrder(focusCodes, item.code()))
                    .thenComparing(NutrientSummaryItemView::share, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        NutrientCoverageView coverage = new NutrientCoverageView(
                mealQueries.countItems(userId, start, end),
                mealQueries.countItemsWithoutNutrients(userId, start, end));
        return new NutrientSummaryView(from, to, days, daysLogged, references.label(), references.range(), focusCodes, coverage, items);
    }

    // ---------------------------------------------------------------------------------------------
    // One nutrient page

    @Override
    @Transactional(readOnly = true)
    public NutrientPageView getNutrientPage(CurrentUserPrincipal principal, String code, LocalDate from, LocalDate to,
                                            String timezone) {
        NutrientEntity nutrient = nutrientRepository.findById(code)
                .orElseThrow(() -> new ResourceNotFoundException("Nutrient not found"));
        ZoneId zone = resolveZone(timezone);
        LocalDate last = to != null ? to : LocalDate.ofInstant(clock.instant(), zone);
        LocalDate first = from != null ? from : last.minusDays(DEFAULT_PAGE_DAYS - 1L);
        int days = validateRange(first, last, MAX_NUTRIENT_RANGE_DAYS);
        Instant start = first.atStartOfDay(zone).toInstant();
        Instant end = last.plusDays(1).atStartOfDay(zone).toInstant();
        UUID userId = principal.userId();

        Set<LocalDate> loggedDates = loggedDates(userId, start, end, zone);
        int daysLogged = loggedDates.size();
        PersonBasics basics = personLookup.facts(userId).basics();
        PersonReferences references = referenceService.forPerson(basics);

        NutrientAmounts amounts = loadAmounts(userId, start, end, zone, List.of(code));
        NutrientSummaryItemView item = summaryItem(nutrient, amounts.totals().get(code), daysLogged, references, null,
                first, last, loggedDates, amounts.daily().getOrDefault(code, Map.of()));
        List<NutrientAmountRowView> amountRows = pageSupport.amounts(code, basics);
        // Fibre, sugar, sodium and saturated fat are not snapshotted per food in the catalogue, so they have none.
        List<NutrientRichFoodView> richest = COLUMN_CODES.contains(code) ? List.of() : pageSupport.richest(code);

        return new NutrientPageView(
                code, nutrient.getDisplayName(), nutrient.getUnit(), nutrient.getGroupCode(), nutrient.getSymbol(),
                nutrient.getAtomicNumber(), first, last, days, daysLogged,
                item.averageAmount(), item.reference(), item.share(), item.met(), item.upperLimit(),
                item.aboveUpperLimit(), pageLabel(references.label()), item.days(),
                sourcesOf(userId, start, end, code, daysLogged), amountRows, richest, pageSupport.guide(code));
    }

    /** "for male ranges, aged 19 to 30" read as "Male ranges, aged 19 to 30". */
    private static String pageLabel(String label) {
        String text = label.startsWith("for ") ? label.substring("for ".length()) : label;
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    /**
     * The foods the person ate in the range that gave the nutrient, biggest first. The amount is per logged day,
     * so the amounts add up to the average, and the share is the food part of everything eaten of the nutrient.
     */
    private List<NutrientSourceView> sourcesOf(UUID userId, Instant start, Instant end, String code, int daysLogged) {
        boolean fromColumn = COLUMN_CODES.contains(code);
        Map<UUID, BigDecimal> detailByItem = new HashMap<>();
        if (!fromColumn) {
            for (NutrientDetailRow detail : mealQueries.findNutrientDetailRows(userId, start, end, List.of(code))) {
                detailByItem.merge(detail.getMealItemId(), orZero(detail.getAmount()), BigDecimal::add);
            }
        }
        Map<String, SourceTotal> byFood = new LinkedHashMap<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (DiaryItemRow row : mealQueries.findDiaryItemRows(userId, start, end)) {
            if (row.getMealItemId() == null) {
                continue;
            }
            BigDecimal amount = fromColumn ? columnAmount(code, row) : detailByItem.get(row.getMealItemId());
            if (amount == null || amount.signum() <= 0) {
                continue;
            }
            byFood.computeIfAbsent(foodKey(row), ignored -> new SourceTotal(row.getFoodName(), row.getCategoryCode()))
                    .add(amount);
            grandTotal = grandTotal.add(amount);
        }
        if (byFood.isEmpty() || daysLogged == 0) {
            return List.of();
        }
        Map<String, String> foodGroups = loadFoodGroups();
        BigDecimal total = grandTotal;
        return byFood.values().stream()
                .sorted(Comparator.comparing((SourceTotal source) -> source.amount).reversed()
                        .thenComparing(source -> source.foodName, Comparator.nullsLast(String::compareTo)))
                .limit(PAGE_SOURCES)
                .map(source -> new NutrientSourceView(
                        source.foodName,
                        source.categoryCode == null ? null : foodGroups.get(source.categoryCode),
                        source.categoryCode,
                        source.amount.divide(BigDecimal.valueOf(daysLogged), 2, RoundingMode.HALF_UP),
                        source.amount.divide(total, 3, RoundingMode.HALF_UP)))
                .toList();
    }

    private static boolean parseScope(String scope) {
        if (scope == null || SCOPE_DIARY.equals(scope)) {
            return false;
        }
        if (SCOPE_ALL.equals(scope)) {
            return true;
        }
        throw new BadRequestException("scope must be diary or all");
    }

    private Set<LocalDate> loggedDates(UUID userId, Instant start, Instant end, ZoneId zone) {
        Set<LocalDate> loggedDates = new HashSet<>();
        for (MealTotalsRow meal : mealQueries.findMealTotals(userId, start, end)) {
            loggedDates.add(localDate(meal.getConsumedAt(), zone));
        }
        return loggedDates;
    }

    /** The range total and the per-day totals of each code, the vitamins and minerals from their snapshot rows. */
    private NutrientAmounts loadAmounts(UUID userId, Instant start, Instant end, ZoneId zone, List<String> codes) {
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Map<LocalDate, BigDecimal>> dailyTotals = new HashMap<>();
        List<String> snapshotCodes = codes.stream().filter(code -> !COLUMN_CODES.contains(code)).toList();
        List<String> columnCodes = codes.stream().filter(COLUMN_CODES::contains).toList();
        if (!snapshotCodes.isEmpty()) {
            for (NutrientTotalRow row : mealQueries.findNutrientTotals(userId, start, end, snapshotCodes)) {
                if (row.getTotal() != null) {
                    totals.put(row.getNutrientCode(), row.getTotal());
                }
            }
            for (MealCodeTotalRow row : mealQueries.findNutrientTotalsByMeal(userId, start, end, snapshotCodes)) {
                if (row.getTotal() != null) {
                    dailyTotals.computeIfAbsent(row.getNutrientCode(), key -> new HashMap<>())
                            .merge(localDate(row.getConsumedAt(), zone), row.getTotal(), BigDecimal::add);
                }
            }
        }
        if (!columnCodes.isEmpty()) {
            for (DiaryItemRow row : mealQueries.findDiaryItemRows(userId, start, end)) {
                if (row.getMealItemId() == null) {
                    continue;
                }
                for (String code : columnCodes) {
                    BigDecimal amount = columnAmount(code, row);
                    if (amount != null) {
                        totals.merge(code, amount, BigDecimal::add);
                        dailyTotals.computeIfAbsent(code, key -> new HashMap<>())
                                .merge(localDate(row.getConsumedAt(), zone), amount, BigDecimal::add);
                    }
                }
            }
        }
        return new NutrientAmounts(totals, dailyTotals);
    }

    private static BigDecimal columnAmount(String code, DiaryItemRow row) {
        return switch (code) {
            case FIBER -> row.getFiberGrams();
            case SUGAR -> row.getSugarGrams();
            case SODIUM -> row.getSodiumMg();
            case SAT_FAT -> row.getSaturatedFatGrams();
            default -> null;
        };
    }

    private static NutrientSummaryItemView summaryItem(NutrientEntity nutrient, BigDecimal total, int daysLogged,
                                                       PersonReferences references, String richestUsualFood,
                                                       LocalDate from, LocalDate to, Set<LocalDate> loggedDates,
                                                       Map<LocalDate, BigDecimal> dailyAmounts) {
        String code = nutrient.getCode();
        boolean known = daysLogged > 0 && total != null;
        BigDecimal average = known
                ? total.divide(BigDecimal.valueOf(daysLogged), 2, RoundingMode.HALF_UP)
                : null;
        BigDecimal target = references.target(code);
        BigDecimal limit = target == null ? references.limit(code) : null;
        boolean isLimit = limit != null;
        BigDecimal reference = isLimit ? limit : target;
        BigDecimal upper = references.upper(code);
        Boolean met = null;
        Boolean above = null;
        if (known) {
            met = isLimit ? average.compareTo(limit) <= 0 : reaches(average, reference);
            above = isLimit ? average.compareTo(limit) > 0 : upper != null && average.compareTo(upper) > 0;
        }
        return new NutrientSummaryItemView(
                code,
                nutrient.getDisplayName(),
                nutrient.getKind(),
                nutrient.getGroupCode(),
                nutrient.getSymbol(),
                nutrient.getAtomicNumber(),
                nutrient.getUnit(),
                average,
                reference,
                known ? shareOf(average, reference) : null,
                met,
                upper,
                above,
                richestUsualFood,
                dayEntries(from, to, loggedDates, dailyAmounts, reference));
    }

    private static List<NutrientDayView> dayEntries(LocalDate from, LocalDate to, Set<LocalDate> loggedDates,
                                                    Map<LocalDate, BigDecimal> amounts, BigDecimal reference) {
        List<NutrientDayView> entries = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            BigDecimal amount = loggedDates.contains(date) ? amounts.get(date) : null;
            BigDecimal rounded = amount == null ? null : scaleTwo(amount);
            entries.add(new NutrientDayView(date, loggedDates.contains(date), rounded,
                    rounded == null ? null : shareOf(rounded, reference)));
        }
        return entries;
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
        return validateRange(from, to, MAX_RANGE_DAYS);
    }

    private int validateRange(LocalDate from, LocalDate to, int maxDays) {
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > maxDays) {
            throw new BadRequestException("The range must not exceed " + maxDays + " days");
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

    private static BigDecimal scaleTwoOrNull(BigDecimal value) {
        return value == null ? null : scaleTwo(value);
    }

    /** Adds a value that may be unknown to a total that may be unknown; the result is unknown only if both are. */
    private static BigDecimal addKnown(BigDecimal total, BigDecimal value) {
        if (value == null) {
            return total;
        }
        return total == null ? value : total.add(value);
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
        private final String categoryCode;
        private BigDecimal amount = BigDecimal.ZERO;

        private SourceTotal(String foodName, String categoryCode) {
            this.foodName = foodName;
            this.categoryCode = categoryCode;
        }

        private void add(BigDecimal value) {
            amount = amount.add(value);
        }
    }

    /** The range totals and the per-day totals of some nutrients, by code. */
    private record NutrientAmounts(Map<String, BigDecimal> totals, Map<String, Map<LocalDate, BigDecimal>> daily) {
    }

    /** A day of a range, summed from its meals. An amount stays null while no meal of the day carries it. */
    private static final class DayAccumulator {
        private int meals;
        private int calories;
        private BigDecimal fiber;
        private BigDecimal potassium;
        private BigDecimal sugar;
        private BigDecimal saturatedFat;
        private BigDecimal sodium;
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
