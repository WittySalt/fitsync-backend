package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.dto.CopyDayRequest;
import com.fitsync.nutrition.dto.CopyMealRequest;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CreateMealTemplateRequest;
import com.fitsync.nutrition.dto.MaterializeTemplateRequest;
import com.fitsync.nutrition.dto.MealTemplateItemView;
import com.fitsync.nutrition.dto.MealTemplateView;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.DefaultServingView;
import com.fitsync.nutrition.dto.DrinkWaterEntryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.MealItemInput;
import com.fitsync.nutrition.dto.MealItemView;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.dto.LogWaterRequest;
import com.fitsync.nutrition.dto.QuickAddMealRequest;
import com.fitsync.nutrition.dto.WaterDaySummaryView;
import com.fitsync.nutrition.dto.WaterLogView;
import com.fitsync.nutrition.dto.CreateRecipeRequest;
import com.fitsync.nutrition.dto.NutrientDetailInput;
import com.fitsync.nutrition.dto.NutrientDetailView;
import com.fitsync.nutrition.dto.RecipeAddItemsRequest;
import com.fitsync.nutrition.dto.RecipeIngredientInput;
import com.fitsync.nutrition.dto.RecipeIngredientView;
import com.fitsync.nutrition.dto.RecipeMealRequest;
import com.fitsync.nutrition.dto.RecipeStepInput;
import com.fitsync.nutrition.dto.RecipeView;
import com.fitsync.nutrition.dto.TemplateRecipeInput;
import com.fitsync.nutrition.entity.CustomFoodEntity;
import com.fitsync.nutrition.entity.FoodBarcodeEntity;
import com.fitsync.nutrition.entity.FoodBrandEntity;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.FoodFavoriteEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealItemNutrientDetailEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.MealTemplateEntity;
import com.fitsync.nutrition.entity.MealTemplateItemEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.entity.WaterLogEntity;
import com.fitsync.nutrition.external.ExternalFoodSource;
import com.fitsync.contracts.events.DomainEventPublisher;
import com.fitsync.nutrition.repository.CustomFoodRepository;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodBrandRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodCatalogSearchSpecification;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.FoodFavoriteRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.MealTemplateItemRepository;
import com.fitsync.nutrition.repository.MealTemplateRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.WaterLogRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ConflictException;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The core of the food diary: meals and their items, custom foods, the food catalogue (search with its fuzzy
 * and external-source fallbacks, barcode lookup and import, cloning), user recipes, meal templates, favourites,
 * recent and frequent foods, water and the CSV export. The daily summary is delegated to
 * {@link NutritionDiaryService}.
 *
 * <p>A meal item is a snapshot: when it is logged, the food's nutrition is scaled to the portion and copied
 * onto the item together with its classification, so later catalogue edits never change a logged day. Every
 * meal change publishes MealLogged, MealUpdated or MealDeleted through the outbox.
 */
@Service
public class NutritionServiceImpl implements NutritionService {

    private static final String SOURCE = "nutrition-service";
    private static final String FAST_HAS_NO_FOODS = "A fasted day has no foods";
    private static final String NUTRITION_EVENTS_TOPIC = "nutrition-events";
    private static final int FOOD_SEARCH_MAX_SIZE = 50;
    /** A search that finds fewer local foods than this tops the first page up from the external source. */
    private static final int EXTERNAL_SEARCH_FALLBACK_THRESHOLD = 5;
    /** A search that finds fewer foods than this by name adds the ones with a similar name. */
    static final int FUZZY_SEARCH_THRESHOLD = 5;
    private static final int LOGGED_FOOD_MAX_LIMIT = 50;
    /** Recent and frequent foods are worked out from the caller's latest this-many meal items only. */
    private static final int LOGGED_FOOD_SCAN_LIMIT = 500;
    /** Caps an export at about a year of diary, so one request cannot read a user's whole history. */
    private static final long EXPORT_MAX_DAYS = 370;
    private static final String FOOD_GROUP_OTHER = "OTHER";
    private static final Set<String> FOOD_GROUPS = Set.of(
            "VEG", "FRUIT", "GRAINS", "PROTEIN", "DAIRY", "NUTS", "LEGUMES", "DRINKS", "SWEETS", FOOD_GROUP_OTHER);
    /** The nutrient code whose detail row gives a food's alcohol grams. */
    private static final String ALCOHOL_CODE = "ALCOHOL";

    private final DomainEventPublisher domainEventPublisher;
    private final MealLogRepository mealLogRepository;
    private final MealItemRepository mealItemRepository;
    private final MealItemNutrientDetailRepository mealItemNutrientDetailRepository;
    private final CustomFoodRepository customFoodRepository;
    private final FoodCatalogItemRepository foodCatalogItemRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodBrandRepository foodBrandRepository;
    private final FoodServingRepository foodServingRepository;
    private final FoodBarcodeRepository foodBarcodeRepository;
    private final FoodNutrientProfileRepository foodNutrientProfileRepository;
    private final FoodNutrientDetailRepository foodNutrientDetailRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final FoodFavoriteRepository foodFavoriteRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final MealTemplateItemRepository mealTemplateItemRepository;
    private final WaterLogRepository waterLogRepository;
    private final ExternalFoodSource externalFoodSource;
    private final TransactionTemplate transactionTemplate;
    private final NutritionDiaryService nutritionDiaryService;
    private final RecipeLibraryService recipeLibraryService;
    private final RecipeInsightService recipeInsightService;
    private final FuzzyFoodSearch fuzzyFoodSearch;

    public NutritionServiceImpl(DomainEventPublisher domainEventPublisher,
                                MealLogRepository mealLogRepository,
                                MealItemRepository mealItemRepository,
                                MealItemNutrientDetailRepository mealItemNutrientDetailRepository,
                                CustomFoodRepository customFoodRepository,
                                FoodCatalogItemRepository foodCatalogItemRepository,
                                FoodCategoryRepository foodCategoryRepository,
                                FoodBrandRepository foodBrandRepository,
                                FoodServingRepository foodServingRepository,
                                FoodBarcodeRepository foodBarcodeRepository,
                                FoodNutrientProfileRepository foodNutrientProfileRepository,
                                FoodNutrientDetailRepository foodNutrientDetailRepository,
                                RecipeRepository recipeRepository,
                                RecipeIngredientRepository recipeIngredientRepository,
                                FoodFavoriteRepository foodFavoriteRepository,
                                MealTemplateRepository mealTemplateRepository,
                                MealTemplateItemRepository mealTemplateItemRepository,
                                WaterLogRepository waterLogRepository,
                                ExternalFoodSource externalFoodSource,
                                PlatformTransactionManager transactionManager,
                                NutritionDiaryService nutritionDiaryService,
                                RecipeLibraryService recipeLibraryService,
                                RecipeInsightService recipeInsightService,
                                FuzzyFoodSearch fuzzyFoodSearch) {
        this.fuzzyFoodSearch = fuzzyFoodSearch;
        this.domainEventPublisher = domainEventPublisher;
        this.mealLogRepository = mealLogRepository;
        this.mealItemRepository = mealItemRepository;
        this.mealItemNutrientDetailRepository = mealItemNutrientDetailRepository;
        this.customFoodRepository = customFoodRepository;
        this.foodCatalogItemRepository = foodCatalogItemRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.foodBrandRepository = foodBrandRepository;
        this.foodServingRepository = foodServingRepository;
        this.foodBarcodeRepository = foodBarcodeRepository;
        this.foodNutrientProfileRepository = foodNutrientProfileRepository;
        this.foodNutrientDetailRepository = foodNutrientDetailRepository;
        this.recipeRepository = recipeRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.foodFavoriteRepository = foodFavoriteRepository;
        this.mealTemplateRepository = mealTemplateRepository;
        this.mealTemplateItemRepository = mealTemplateItemRepository;
        this.waterLogRepository = waterLogRepository;
        this.externalFoodSource = externalFoodSource;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.nutritionDiaryService = nutritionDiaryService;
        this.recipeLibraryService = recipeLibraryService;
        this.recipeInsightService = recipeInsightService;
    }

    @Override
    @Transactional
    public MealView createMeal(CurrentUserPrincipal principal, CreateMealRequest request, String timezone) {
        return createMealFromPlanned(principal.userId(), request, timezone,
                plannedFromInputs(principal.userId(), request.items()));
    }

    /** Saves a new meal from the header of a request and items that are already planned, then publishes MealLogged. */
    private MealView createMealFromPlanned(UUID userId, CreateMealRequest header, String timezone,
                                          List<PlannedItem> planned) {
        if (header.mealType() == MealType.FAST && !planned.isEmpty()) {
            throw new BadRequestException(FAST_HAS_NO_FOODS);
        }
        settleFastDay(userId, header.mealType(), header.consumedAt(), timezone, null);
        MealLogEntity entity = new MealLogEntity();
        entity.setMealId(UUID.randomUUID());
        entity.setUserId(userId);
        applyMeal(entity, header, timezone);
        MealLogEntity savedMeal = mealLogRepository.save(entity);
        List<MealItemEntity> savedItems = persistMealItems(savedMeal.getMealId(), planned);
        publishMealLogged(savedMeal, savedItems);
        return toMealView(savedMeal, savedItems);
    }

    @Override
    @Transactional
    public MealView quickAddMeal(CurrentUserPrincipal principal, QuickAddMealRequest request, String timezone) {
        String itemName = request.name() == null || request.name().isBlank() ? "Quick add" : request.name().trim();
        MealItemInput item = new MealItemInput(
                null, null, null, null, null,
                itemName, null,
                BigDecimal.ONE, "serving",
                null, null, null,
                request.calories(),
                request.proteinGrams(),
                request.carbsGrams(),
                request.fatGrams(),
                request.fiberGrams(),
                request.sugarGrams(),
                request.sodiumMg(),
                null);
        CreateMealRequest mealRequest = new CreateMealRequest(
                request.mealType() == null ? MealType.SNACK : request.mealType(),
                blankToNull(request.name()),
                MealSource.MANUAL,
                request.consumedAt() == null ? Instant.now(Clock.systemUTC()) : request.consumedAt(),
                null,
                List.of(item));
        return createMeal(principal, mealRequest, timezone);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealView> listMeals(CurrentUserPrincipal principal, Instant from, Instant to, MealType mealType) {
        validateDateRange(from, to);
        List<MealLogEntity> meals = findMeals(principal.userId(), from, to, mealType);
        Map<String, String> foodGroups = loadFoodGroups();
        return meals.stream()
                .map(meal -> toMealView(meal, mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId()),
                        foodGroups))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MealView getMeal(CurrentUserPrincipal principal, UUID mealId) {
        MealLogEntity meal = getOwnedMeal(principal.userId(), mealId);
        return toMealView(meal, mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId()));
    }

    @Override
    @Transactional
    public MealView updateMeal(CurrentUserPrincipal principal, UUID mealId, CreateMealRequest request,
                              String timezone) {
        MealLogEntity meal = getOwnedMeal(principal.userId(), mealId);
        if (request.mealType() == MealType.FAST && !request.items().isEmpty()) {
            throw new BadRequestException(FAST_HAS_NO_FOODS);
        }
        settleFastDay(principal.userId(), request.mealType(), request.consumedAt(), timezone, mealId);
        applyMeal(meal, request, timezone);
        MealLogEntity savedMeal = mealLogRepository.save(meal);
        mealItemRepository.deleteByMealId(savedMeal.getMealId());
        List<MealItemEntity> savedItems = replaceMealItems(principal.userId(), savedMeal.getMealId(), request.items());
        publishMealUpdated(savedMeal, savedItems);
        return toMealView(savedMeal, savedItems);
    }

    @Override
    @Transactional
    public void deleteMeal(CurrentUserPrincipal principal, UUID mealId) {
        softDeleteMeal(getOwnedMeal(principal.userId(), mealId));
    }

    /**
     * A day is either fasted or has meals. Logging a fast on a day that already has meals is refused, and logging
     * any other meal on a fasted day marks the fast as deleted in the same transaction, so the day stops being
     * a fasted one. The meal that is being edited is left out of the check.
     */
    private void settleFastDay(UUID userId, MealType mealType, Instant consumedAt, String timezone, UUID ownMealId) {
        ZoneId zone = resolveZone(timezone);
        LocalDate day = consumedAt.atZone(zone).toLocalDate();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant();
        List<MealLogEntity> others = mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                        userId, from, to)
                .stream()
                .filter(meal -> !meal.getMealId().equals(ownMealId))
                .toList();
        if (mealType == MealType.FAST) {
            if (!others.isEmpty()) {
                throw new ConflictException("Meals are already logged on this day");
            }
            return;
        }
        for (MealLogEntity meal : others) {
            if (meal.getMealType() == MealType.FAST) {
                softDeleteMeal(meal);
            }
        }
    }

    private void softDeleteMeal(MealLogEntity meal) {
        // The meal and its items stay in the table, marked as deleted, and every read leaves them out.
        meal.setDeletedAt(Instant.now(Clock.systemUTC()));
        mealLogRepository.save(meal);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealDeleted",
                1,
                Instant.now(Clock.systemUTC()),
                SOURCE,
                meal.getUserId(),
                new MealDeletedPayload(meal.getMealId(), Instant.now(Clock.systemUTC()))));
    }

    @Override
    @Transactional(readOnly = true)
    public MealView getLatestMeal(CurrentUserPrincipal principal) {
        return mealLogRepository.findTopByUserIdOrderByConsumedAtDesc(principal.userId())
                .map(meal -> toMealView(meal, mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId())))
                .orElse(null);
    }

    @Override
    @Transactional
    public MealView copyMeal(CurrentUserPrincipal principal, UUID mealId, CopyMealRequest request) {
        MealLogEntity source = getOwnedMeal(principal.userId(), mealId);
        return copyMealTo(principal.userId(), source, request.consumedAt(), request.mealType(), loadFoodGroups());
    }

    @Override
    @Transactional
    public List<MealView> copyDay(CurrentUserPrincipal principal, LocalDate sourceDate, CopyDayRequest request,
                                 String timezone) {
        ZoneId zone = resolveZone(timezone);
        Instant from = sourceDate.atStartOfDay(zone).toInstant();
        Instant to = sourceDate.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant();
        List<MealLogEntity> meals = mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                        principal.userId(), from, to);
        List<MealView> copied = new ArrayList<>();
        // Read the category table once for the whole day instead of once per copied meal.
        Map<String, String> foodGroups = loadFoodGroups();
        for (MealLogEntity meal : meals) {
            if (meal.getMealType() == MealType.FAST) {
                continue;
            }
            LocalTime localTime = meal.getConsumedAt().atZone(zone).toLocalTime();
            Instant target = request.targetDate().atTime(localTime).atZone(zone).toInstant();
            copied.add(copyMealTo(principal.userId(), meal, target, null, foodGroups));
        }
        return copied;
    }

    /** Clones a meal and its item snapshots (+ nutrient details) to a new time, emitting MealLogged. */
    private MealView copyMealTo(UUID userId, MealLogEntity source, Instant targetConsumedAt, MealType mealTypeOverride,
                                Map<String, String> foodGroups) {
        MealType copyType = mealTypeOverride != null ? mealTypeOverride : source.getMealType();
        if (copyType == MealType.FAST) {
            throw new BadRequestException("A fasted day cannot be copied");
        }
        settleFastDay(userId, copyType, targetConsumedAt, source.getTimezone(), null);
        MealLogEntity newMeal = new MealLogEntity();
        newMeal.setMealId(UUID.randomUUID());
        newMeal.setUserId(userId);
        newMeal.setMealType(copyType);
        newMeal.setName(source.getName());
        newMeal.setSource(source.getSource());
        newMeal.setConsumedAt(targetConsumedAt);
        newMeal.setTimezone(source.getTimezone());
        newMeal.setNote(source.getNote());
        MealLogEntity savedMeal = mealLogRepository.save(newMeal);

        List<MealItemEntity> sourceItems = mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(source.getMealId());
        Map<UUID, List<NutrientDetailView>> detailsBySource = loadMealItemNutrientDetails(sourceItems);
        List<MealItemEntity> newItems = new ArrayList<>();
        for (MealItemEntity src : sourceItems) {
            MealItemEntity savedItem = mealItemRepository.save(copyMealItem(savedMeal.getMealId(), src));
            replaceMealItemNutrientDetails(savedItem.getMealItemId(),
                    detailsBySource.getOrDefault(src.getMealItemId(), List.of()).stream()
                            .map(d -> new NutrientDetailInput(d.nutrientCode(), d.displayName(), d.amount(),
                                    d.unit(), d.sortOrder()))
                            .toList());
            newItems.add(savedItem);
        }
        publishMealLogged(savedMeal, newItems);
        return toMealView(savedMeal, newItems, foodGroups);
    }

    private MealItemEntity copyMealItem(UUID newMealId, MealItemEntity src) {
        MealItemEntity copy = new MealItemEntity();
        copy.setMealItemId(UUID.randomUUID());
        copy.setMealId(newMealId);
        copy.setCustomFoodId(src.getCustomFoodId());
        copy.setFoodCatalogItemId(src.getFoodCatalogItemId());
        copy.setFoodServingId(src.getFoodServingId());
        copy.setExternalFoodRef(src.getExternalFoodRef());
        copy.setBarcode(src.getBarcode());
        copy.setFoodName(src.getFoodName());
        copy.setBrandName(src.getBrandName());
        copy.setQuantity(src.getQuantity());
        copy.setUnit(src.getUnit());
        copy.setServingSize(src.getServingSize());
        copy.setServingUnit(src.getServingUnit());
        copy.setSnapshotServingLabel(src.getSnapshotServingLabel());
        copy.setCalories(src.getCalories());
        copy.setProteinGrams(src.getProteinGrams());
        copy.setCarbsGrams(src.getCarbsGrams());
        copy.setFatGrams(src.getFatGrams());
        copy.setFiberGrams(src.getFiberGrams());
        copy.setSugarGrams(src.getSugarGrams());
        copy.setSodiumMg(src.getSodiumMg());
        copy.setCategoryCode(src.getCategoryCode());
        copy.setNovaGroup(src.getNovaGroup());
        copy.setNutriScore(src.getNutriScore());
        copy.setGramsConsumed(src.getGramsConsumed());
        copy.setSaturatedFatGrams(src.getSaturatedFatGrams());
        copy.setWaterMl(src.getWaterMl());
        copy.setSourceDataset(src.getSourceDataset());
        copy.setRecipeId(src.getRecipeId());
        copy.setRecipeServings(src.getRecipeServings());
        return copy;
    }

    @Override
    @Transactional
    public CustomFoodView createCustomFood(CurrentUserPrincipal principal, CreateCustomFoodRequest request) {
        CustomFoodEntity entity = new CustomFoodEntity();
        entity.setCustomFoodId(UUID.randomUUID());
        entity.setUserId(principal.userId());
        applyCustomFood(entity, request);
        entity.setVerified(false);
        return toCustomFoodView(customFoodRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomFoodView> listCustomFoods(CurrentUserPrincipal principal) {
        return customFoodRepository.findByUserIdOrderByNameAsc(principal.userId()).stream()
                .map(this::toCustomFoodView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomFoodView getCustomFood(CurrentUserPrincipal principal, UUID customFoodId) {
        return toCustomFoodView(getOwnedCustomFood(principal.userId(), customFoodId));
    }

    @Override
    @Transactional
    public CustomFoodView updateCustomFood(CurrentUserPrincipal principal, UUID customFoodId, CreateCustomFoodRequest request) {
        CustomFoodEntity entity = getOwnedCustomFood(principal.userId(), customFoodId);
        applyCustomFood(entity, request);
        return toCustomFoodView(customFoodRepository.save(entity));
    }

    @Override
    @Transactional
    public void deleteCustomFood(CurrentUserPrincipal principal, UUID customFoodId) {
        customFoodRepository.delete(getOwnedCustomFood(principal.userId(), customFoodId));
    }

    @Override
    @Transactional(readOnly = true)
    public DailyNutritionSummaryView getDailySummary(CurrentUserPrincipal principal, LocalDate date, String timezone) {
        return nutritionDiaryService.getDailySummary(principal, date, timezone);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FoodSearchResultView> searchFoods(CurrentUserPrincipal principal, FoodSearchCriteria criteria) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        String normalizedQuery = normalizeQuery(criteria.query());

        String categoryCode = null;
        String pathPrefix = null;
        if (criteria.categoryId() != null) {
            FoodCategoryEntity category = foodCategoryRepository.findByCategoryIdAndActiveTrue(criteria.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Food category not found"));
            if (criteria.includeSubcategories()) {
                pathPrefix = category.getPath();
            } else {
                categoryCode = category.getCode();
            }
        }

        String foodGroup = normalizeFoodGroup(criteria.foodGroup());
        int size = Math.min(Math.max(criteria.size(), 1), FOOD_SEARCH_MAX_SIZE);
        int page = Math.max(criteria.page(), 0);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<FoodCatalogItemEntity> foodPage = foodCatalogItemRepository.findAll(
                FoodCatalogSearchSpecification.accessibleSearch(
                        principal.userId(), normalizedQuery, today, categoryCode, pathPrefix,
                        criteria.brandId(), criteria.verified(), criteria.scope(), foodGroup),
                pageRequest);
        List<FoodCatalogItemEntity> exactFoods = foodPage.getContent();
        List<FoodCatalogItemEntity> extraFoods = shouldSearchSimilarFoods(criteria, normalizedQuery, page,
                exactFoods.size())
                ? similarFoods(principal.userId(), normalizedQuery, today, exactFoods, size - exactFoods.size())
                : List.of();
        List<FoodCatalogItemEntity> foods = extraFoods.isEmpty() ? exactFoods
                : mergeSimilar(exactFoods, extraFoods, size);
        long totalFound = foodPage.getTotalElements() + extraFoods.size();

        Map<UUID, String> brandNames = loadBrandNames(foods.stream()
                .map(FoodCatalogItemEntity::getBrandId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, String> brandLogos = loadBrandLogos(foods.stream()
                .map(FoodCatalogItemEntity::getBrandId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<String, String> categoryNames = loadCategoryNames(foods.stream()
                .map(FoodCatalogItemEntity::getCategoryCode)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, FoodNutrientProfileEntity> profiles = loadPrimaryProfiles(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .toList(), today);
        Map<UUID, String> primaryBarcodes = loadPrimaryBarcodes(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .toList(), today);
        Map<String, String> foodGroups = loadFoodGroups();
        Map<UUID, FoodServingEntity> defaultServings = loadDefaultServings(foods, today);
        List<FoodSearchResultView> localResults = foods.stream()
                .map(food -> toFoodSearchResultView(food, brandNames, brandLogos, categoryNames, foodGroups,
                        profiles.get(food.getFoodId()), primaryBarcodes.get(food.getFoodId()),
                        defaultServings.get(food.getFoodId())))
                .toList();
        if (!shouldSearchExternalFoods(criteria, normalizedQuery, page, localResults.size())) {
            return new PageImpl<>(localResults, pageRequest, totalFound);
        }
        // External results only fill the page up to the fallback threshold, never past the page size, and
        // skip any product whose barcode is already in the local results.
        int externalLimit = Math.min(size - localResults.size(), EXTERNAL_SEARCH_FALLBACK_THRESHOLD - localResults.size());
        if (externalLimit <= 0) {
            return new PageImpl<>(localResults, pageRequest, totalFound);
        }
        List<String> localBarcodes = primaryBarcodes.values().stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        List<FoodSearchResultView> externalResults;
        try {
            externalResults = externalFoodSource.search(normalizedQuery, externalLimit).stream()
                    .filter(result -> result.primaryBarcode() == null || !localBarcodes.contains(result.primaryBarcode()))
                    .limit(externalLimit)
                    .toList();
        } catch (RuntimeException ex) {
            // The external source is best effort: when it is down the local results are still returned.
            return new PageImpl<>(localResults, pageRequest, totalFound);
        }
        if (externalResults.isEmpty()) {
            return new PageImpl<>(localResults, pageRequest, totalFound);
        }
        List<FoodSearchResultView> combined = new ArrayList<>(localResults);
        combined.addAll(externalResults);
        return new PageImpl<>(combined, pageRequest, totalFound + externalResults.size());
    }

    /**
     * The fuzzy fallback runs for a plain text search on the first page that found fewer than five foods by name:
     * with a category, brand, verified, scope or food-group filter the exact search stays as it is.
     */
    private boolean shouldSearchSimilarFoods(FoodSearchCriteria criteria, String normalizedQuery, int page,
                                             int foundCount) {
        return normalizedQuery != null
                && page == 0
                && foundCount < FUZZY_SEARCH_THRESHOLD
                && criteria.categoryId() == null
                && criteria.brandId() == null
                && criteria.verified() == null
                && criteria.scope() == null
                && blankToNull(criteria.foodGroup()) == null;
    }

    private List<FoodCatalogItemEntity> similarFoods(UUID userId, String query, LocalDate today,
                                                     List<FoodCatalogItemEntity> found, int limit) {
        Set<UUID> foundIds = found.stream().map(FoodCatalogItemEntity::getFoodId).collect(java.util.stream.Collectors.toSet());
        List<UUID> ids = fuzzyFoodSearch.similarFoodIds(userId, query, today, foundIds, limit);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, FoodCatalogItemEntity> byId = new HashMap<>();
        foodCatalogItemRepository.findAllById(ids).forEach(food -> byId.put(food.getFoodId(), food));
        return ids.stream().filter(id -> !foundIds.contains(id)).map(byId::get).filter(java.util.Objects::nonNull)
                .toList();
    }

    /** The exact matches first, then the similar ones in their order, no food twice, at most one page. */
    static List<FoodCatalogItemEntity> mergeSimilar(List<FoodCatalogItemEntity> exact,
                                                    List<FoodCatalogItemEntity> similar, int pageSize) {
        Map<UUID, FoodCatalogItemEntity> merged = new LinkedHashMap<>();
        exact.forEach(food -> merged.putIfAbsent(food.getFoodId(), food));
        similar.forEach(food -> merged.putIfAbsent(food.getFoodId(), food));
        return merged.values().stream().limit(pageSize).toList();
    }

    /** Same rule as the fuzzy fallback: a plain text search on the first page that found few foods. */
    private boolean shouldSearchExternalFoods(FoodSearchCriteria criteria,
                                              String normalizedQuery,
                                              int page,
                                              int localResultCount) {
        return normalizedQuery != null
                && page == 0
                && localResultCount < EXTERNAL_SEARCH_FALLBACK_THRESHOLD
                && criteria.categoryId() == null
                && criteria.brandId() == null
                && criteria.verified() == null
                && criteria.scope() == null
                && blankToNull(criteria.foodGroup()) == null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodCategoryView> listCategories(CurrentUserPrincipal principal) {
        List<FoodCategoryEntity> categories = foodCategoryRepository
                .findByActiveTrueOrderBySortOrderAscDisplayNameAsc();
        Map<UUID, List<FoodCategoryEntity>> byParent = new LinkedHashMap<>();
        for (FoodCategoryEntity category : categories) {
            UUID parentKey = category.getParentId();
            byParent.computeIfAbsent(parentKey, k -> new ArrayList<>()).add(category);
        }
        return byParent.getOrDefault(null, List.of()).stream()
                .map(category -> toCategoryView(category, byParent))
                .toList();
    }

    private FoodCategoryView toCategoryView(FoodCategoryEntity category,
                                            Map<UUID, List<FoodCategoryEntity>> byParent) {
        List<FoodCategoryView> children = byParent.getOrDefault(category.getCategoryId(), List.of()).stream()
                .map(child -> toCategoryView(child, byParent))
                .toList();
        return new FoodCategoryView(
                category.getCategoryId(),
                category.getParentId(),
                category.getCode(),
                category.getDisplayName(),
                category.getSlug(),
                category.getSortOrder(),
                children);
    }

    @Override
    @Transactional
    public RecipeView createRecipe(CurrentUserPrincipal principal, CreateRecipeRequest request) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setRecipeId(UUID.randomUUID());
        recipe.setUserId(principal.userId());
        recipe.setScope(RecipeScope.USER);
        recipe.setSource("USER");
        applyRecipe(recipe, request);
        RecipeEntity saved = recipeRepository.save(recipe);
        List<RecipeIngredientEntity> ingredients = replaceRecipeIngredients(saved.getRecipeId(), request.ingredients());
        recipeLibraryService.replaceSteps(saved.getRecipeId(), stepTexts(request));
        return toRecipeView(principal.userId(), saved, ingredients);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecipeView> listRecipes(CurrentUserPrincipal principal) {
        return recipeRepository.findByUserIdOrderByNameAsc(principal.userId()).stream()
                .map(recipe -> toRecipeView(principal.userId(), recipe,
                        recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getRecipeId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RecipeView getRecipe(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity recipe = getOwnedRecipe(principal.userId(), recipeId);
        return toRecipeView(principal.userId(), recipe,
                recipeIngredientRepository.findByRecipeIdOrderBySortOrderAsc(recipe.getRecipeId()));
    }

    @Override
    @Transactional
    public RecipeView updateRecipe(CurrentUserPrincipal principal, UUID recipeId, CreateRecipeRequest request) {
        RecipeEntity recipe = getOwnedRecipe(principal.userId(), recipeId);
        applyRecipe(recipe, request);
        RecipeEntity saved = recipeRepository.save(recipe);
        recipeIngredientRepository.deleteByRecipeId(saved.getRecipeId());
        List<RecipeIngredientEntity> ingredients = replaceRecipeIngredients(saved.getRecipeId(), request.ingredients());
        recipeLibraryService.replaceSteps(saved.getRecipeId(), stepTexts(request));
        return toRecipeView(principal.userId(), saved, ingredients);
    }

    @Override
    @Transactional
    public void deleteRecipe(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity recipe = getOwnedRecipe(principal.userId(), recipeId);
        // The recipe, its ingredients and steps stay in the tables, and every read leaves a deleted recipe out.
        recipe.setDeletedAt(Instant.now(Clock.systemUTC()));
        recipeRepository.save(recipe);
    }

    private List<String> stepTexts(CreateRecipeRequest request) {
        return request.steps().stream().map(RecipeStepInput::text).toList();
    }

    private void applyRecipe(RecipeEntity recipe, CreateRecipeRequest request) {
        recipe.setName(request.name().trim());
        recipe.setServings(request.servings());
        recipe.setNote(blankToNull(request.note()));
    }

    private RecipeEntity getOwnedRecipe(UUID userId, UUID recipeId) {
        return recipeRepository.findByRecipeIdAndUserId(recipeId, userId)
                .filter(recipe -> recipe.getScope() == RecipeScope.USER)
                .orElseThrow(() -> new ResourceNotFoundException("Recipe not found"));
    }

    private List<RecipeIngredientEntity> replaceRecipeIngredients(UUID recipeId, List<RecipeIngredientInput> inputs) {
        List<RecipeIngredientEntity> saved = new ArrayList<>();
        int sortOrder = 0;
        for (RecipeIngredientInput input : inputs) {
            RecipeIngredientEntity entity = new RecipeIngredientEntity();
            entity.setIngredientId(UUID.randomUUID());
            entity.setRecipeId(recipeId);
            entity.setFoodCatalogItemId(input.foodCatalogItemId());
            entity.setFoodServingId(input.foodServingId());
            entity.setQuantity(input.quantity());
            entity.setDisplayText(blankToNull(input.displayText()));
            entity.setGrams(input.grams());
            entity.setSortOrder(sortOrder++);
            saved.add(recipeIngredientRepository.save(entity));
        }
        return saved;
    }

    /**
     * Recomputes a recipe's nutrition by aggregating each ingredient's catalog nutrition (reusing
     * the meal-item resolution), so totals always reflect current catalog data. Per-serving values
     * are total / servings.
     */
    private RecipeView toRecipeView(UUID userId, RecipeEntity recipe, List<RecipeIngredientEntity> ingredients) {
        List<RecipeIngredientView> ingredientViews = new ArrayList<>();
        int totalCalories = 0;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        BigDecimal totalFiber = BigDecimal.ZERO;
        BigDecimal totalSugar = BigDecimal.ZERO;
        BigDecimal totalSodium = BigDecimal.ZERO;
        for (RecipeIngredientEntity ingredient : ingredients) {
            MealItemDraft draft = draftForIngredient(userId, ingredient, BigDecimal.ONE);
            ingredientViews.add(new RecipeIngredientView(
                    ingredient.getIngredientId(),
                    draft.foodCatalogItemId(),
                    draft.foodName(),
                    draft.brandName(),
                    draft.foodServingId(),
                    draft.snapshotServingLabel(),
                    ingredient.getQuantity(),
                    draft.unit(),
                    draft.calories(),
                    draft.proteinGrams(),
                    draft.carbsGrams(),
                    draft.fatGrams(),
                    draft.fiberGrams(),
                    draft.sugarGrams(),
                    draft.sodiumMg()));
            totalCalories += defaultInteger(draft.calories());
            totalProtein = totalProtein.add(defaultDecimal(draft.proteinGrams()));
            totalCarbs = totalCarbs.add(defaultDecimal(draft.carbsGrams()));
            totalFat = totalFat.add(defaultDecimal(draft.fatGrams()));
            totalFiber = totalFiber.add(defaultDecimal(draft.fiberGrams()));
            totalSugar = totalSugar.add(defaultDecimal(draft.sugarGrams()));
            totalSodium = totalSodium.add(defaultDecimal(draft.sodiumMg()));
        }
        BigDecimal servings = recipe.getServings();
        return new RecipeView(
                recipe.getRecipeId(),
                recipe.getName(),
                servings,
                recipe.getNote(),
                totalCalories,
                scaleTwo(totalProtein),
                scaleTwo(totalCarbs),
                scaleTwo(totalFat),
                scaleTwo(totalFiber),
                scaleTwo(totalSugar),
                scaleTwo(totalSodium),
                perServingInteger(totalCalories, servings),
                perServingDecimal(totalProtein, servings),
                perServingDecimal(totalCarbs, servings),
                perServingDecimal(totalFat, servings),
                perServingDecimal(totalFiber, servings),
                perServingDecimal(totalSugar, servings),
                perServingDecimal(totalSodium, servings),
                ingredientViews);
    }

    /**
     * The meal item draft of one recipe ingredient, scaled by a factor (1 for the recipe as written). An
     * ingredient with grams is counted in grams and any other in servings of its serving, and both go through
     * the same snapshot as a food logged by hand.
     */
    private MealItemDraft draftForIngredient(UUID userId, RecipeIngredientEntity ingredient, BigDecimal factor) {
        boolean byGrams = ingredient.getGrams() != null;
        BigDecimal quantity = scaledQuantity(byGrams ? ingredient.getGrams() : ingredient.getQuantity(), factor);
        MealItemInput input = new MealItemInput(
                null,
                ingredient.getFoodCatalogItemId(),
                byGrams ? null : ingredient.getFoodServingId(),
                null, null, null, null,
                quantity,
                byGrams ? "g" : null, null, null, null,
                null, null, null, null, null, null, null,
                null);
        return buildMealItemFromCatalog(userId, input, byGrams);
    }

    /**
     * Never below 0.01: a tiny share of an ingredient must not round to a zero quantity, which a logged item
     * never has (the API requires a positive one).
     */
    private BigDecimal scaledQuantity(BigDecimal amount, BigDecimal factor) {
        BigDecimal scaled = amount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
        return scaled.signum() > 0 ? scaled : new BigDecimal("0.01");
    }

    private BigDecimal scaleTwo(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private Integer perServingInteger(int total, BigDecimal servings) {
        if (servings == null || servings.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return BigDecimal.valueOf(total).divide(servings, 0, RoundingMode.HALF_UP).intValue();
    }

    private BigDecimal perServingDecimal(BigDecimal total, BigDecimal servings) {
        if (servings == null || servings.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return total.divide(servings, 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public void addFavorite(CurrentUserPrincipal principal, UUID foodId) {
        FoodCatalogItemEntity food = getAccessibleFood(principal.userId(), foodId, LocalDate.now(ZoneId.systemDefault()));
        if (!foodFavoriteRepository.existsByUserIdAndFoodId(principal.userId(), food.getFoodId())) {
            FoodFavoriteEntity favorite = new FoodFavoriteEntity();
            favorite.setFavoriteId(UUID.randomUUID());
            favorite.setUserId(principal.userId());
            favorite.setFoodId(food.getFoodId());
            foodFavoriteRepository.save(favorite);
        }
    }

    @Override
    @Transactional
    public void removeFavorite(CurrentUserPrincipal principal, UUID foodId) {
        foodFavoriteRepository.deleteByUserIdAndFoodId(principal.userId(), foodId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodSearchResultView> listFavorites(CurrentUserPrincipal principal) {
        List<FoodFavoriteEntity> favorites = foodFavoriteRepository
                .findByUserIdOrderByDateTimeCreatedDesc(principal.userId());
        List<UUID> foodIds = favorites.stream().map(FoodFavoriteEntity::getFoodId).toList();
        if (foodIds.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Map<UUID, FoodCatalogItemEntity> foodsById = new HashMap<>();
        for (FoodCatalogItemEntity food : foodCatalogItemRepository.findAllById(foodIds)) {
            foodsById.put(food.getFoodId(), food);
        }
        List<FoodCatalogItemEntity> foods = favorites.stream()
                .map(favorite -> foodsById.get(favorite.getFoodId()))
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<UUID, String> brandNames = loadBrandNames(foods.stream()
                .map(FoodCatalogItemEntity::getBrandId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, String> brandLogos = loadBrandLogos(foods.stream()
                .map(FoodCatalogItemEntity::getBrandId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<String, String> categoryNames = loadCategoryNames(foods.stream()
                .map(FoodCatalogItemEntity::getCategoryCode)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, FoodNutrientProfileEntity> profiles = loadPrimaryProfiles(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId).toList(), today);
        Map<UUID, String> primaryBarcodes = loadPrimaryBarcodes(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId).toList(), today);
        Map<String, String> foodGroups = loadFoodGroups();
        Map<UUID, FoodServingEntity> defaultServings = loadDefaultServings(foods, today);
        return foods.stream()
                .map(food -> toFoodSearchResultView(food, brandNames, brandLogos, categoryNames, foodGroups,
                        profiles.get(food.getFoodId()), primaryBarcodes.get(food.getFoodId()),
                        defaultServings.get(food.getFoodId())))
                .toList();
    }

    @Override
    @Transactional
    public MealTemplateView createMealTemplate(CurrentUserPrincipal principal, CreateMealTemplateRequest request) {
        if (request.fromMealId() != null && (!request.items().isEmpty() || !request.recipeItems().isEmpty())) {
            throw new BadRequestException("Provide either fromMealId or items, not both");
        }
        MealTemplateEntity template = new MealTemplateEntity();
        template.setTemplateId(UUID.randomUUID());
        template.setUserId(principal.userId());
        template.setName(request.name().trim());
        template.setMealType(request.mealType());
        template.setNote(blankToNull(request.note()));
        MealTemplateEntity saved = mealTemplateRepository.save(template);

        List<MealTemplateItemEntity> items = buildTemplateItems(principal.userId(), saved.getTemplateId(), request);
        return toMealTemplateView(saved, items);
    }

    private List<MealTemplateItemEntity> buildTemplateItems(UUID userId, UUID templateId, CreateMealTemplateRequest request) {
        List<MealTemplateItemEntity> items = new ArrayList<>();
        int sortOrder = 0;
        if (request.fromMealId() != null) {
            MealLogEntity meal = getOwnedMeal(userId, request.fromMealId());
            for (MealItemEntity src : mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId())) {
                items.add(mealTemplateItemRepository.save(templateItemFromMealItem(templateId, src, sortOrder++)));
            }
        } else {
            if (request.items().isEmpty() && request.recipeItems().isEmpty()) {
                throw new BadRequestException("Provide fromMealId or a non-empty items list");
            }
            for (MealItemInput input : request.items()) {
                MealItemDraft draft = buildMealItemDraft(userId, input);
                items.add(mealTemplateItemRepository.save(templateItemFromDraft(templateId, draft, sortOrder++)));
            }
            for (TemplateRecipeInput recipeInput : request.recipeItems()) {
                items.add(mealTemplateItemRepository.save(templateItemFromRecipe(userId, templateId, recipeInput, sortOrder++)));
            }
        }
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MealTemplateView> listMealTemplates(CurrentUserPrincipal principal) {
        return mealTemplateRepository.findByUserIdOrderByNameAsc(principal.userId()).stream()
                .map(template -> toMealTemplateView(template,
                        mealTemplateItemRepository.findByTemplateIdOrderBySortOrderAsc(template.getTemplateId())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MealTemplateView getMealTemplate(CurrentUserPrincipal principal, UUID templateId) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        return toMealTemplateView(template,
                mealTemplateItemRepository.findByTemplateIdOrderBySortOrderAsc(template.getTemplateId()));
    }

    @Override
    @Transactional
    public MealTemplateView addRecipeToMealTemplate(CurrentUserPrincipal principal, UUID templateId,
                                                    TemplateRecipeInput input) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        List<MealTemplateItemEntity> items = new ArrayList<>(
                mealTemplateItemRepository.findByTemplateIdOrderBySortOrderAsc(template.getTemplateId()));
        int sortOrder = items.isEmpty() ? 0 : items.get(items.size() - 1).getSortOrder() + 1;
        items.add(mealTemplateItemRepository.save(
                templateItemFromRecipe(principal.userId(), template.getTemplateId(), input, sortOrder)));
        return toMealTemplateView(template, items);
    }

    @Override
    @Transactional
    public void deleteMealTemplate(CurrentUserPrincipal principal, UUID templateId) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        mealTemplateItemRepository.deleteByTemplateId(template.getTemplateId());
        mealTemplateRepository.delete(template);
    }

    @Override
    @Transactional
    public MealView createMealFromTemplate(CurrentUserPrincipal principal, UUID templateId,
                                           MaterializeTemplateRequest request, String timezone) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        List<MealTemplateItemEntity> templateItems = mealTemplateItemRepository
                .findByTemplateIdOrderBySortOrderAsc(template.getTemplateId());
        if (templateItems.isEmpty()) {
            throw new BadRequestException("Template has no items");
        }
        List<PlannedItem> planned = new ArrayList<>();
        for (MealTemplateItemEntity templateItem : templateItems) {
            if (templateItem.getRecipeId() == null) {
                planned.add(new PlannedItem(
                        buildMealItemDraft(principal.userId(), mealItemInputFromTemplateItem(templateItem)), null, null));
            } else {
                // A recipe in a template is expanded from the recipe as it is now, not from the snapshot.
                RecipeEntity recipe = recipeLibraryService.findVisible(principal.userId(), templateItem.getRecipeId());
                planned.addAll(plannedRecipeItems(principal.userId(), recipe, templateItem.getRecipeServings()));
            }
        }
        MealType mealType = request.mealType() != null ? request.mealType()
                : (template.getMealType() != null ? template.getMealType() : MealType.SNACK);
        CreateMealRequest mealRequest = new CreateMealRequest(
                mealType,
                template.getName(),
                MealSource.MANUAL,
                request.consumedAt(),
                template.getNote(),
                List.of());
        return createMealFromPlanned(principal.userId(), mealRequest, timezone, planned);
    }

    private MealTemplateEntity getOwnedTemplate(UUID userId, UUID templateId) {
        return mealTemplateRepository.findByTemplateIdAndUserId(templateId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal template not found"));
    }

    private MealItemInput mealItemInputFromTemplateItem(MealTemplateItemEntity templateItem) {
        return new MealItemInput(
                templateItem.getCustomFoodId(),
                templateItem.getFoodCatalogItemId(),
                templateItem.getFoodServingId(),
                templateItem.getExternalFoodRef(),
                templateItem.getBarcode(),
                templateItem.getFoodName(),
                templateItem.getBrandName(),
                templateItem.getQuantity(),
                templateItem.getUnit(),
                templateItem.getServingSize(),
                templateItem.getServingUnit(),
                templateItem.getSnapshotServingLabel(),
                templateItem.getCalories(),
                templateItem.getProteinGrams(),
                templateItem.getCarbsGrams(),
                templateItem.getFatGrams(),
                templateItem.getFiberGrams(),
                templateItem.getSugarGrams(),
                templateItem.getSodiumMg(),
                null);
    }

    private MealTemplateView toMealTemplateView(MealTemplateEntity template, List<MealTemplateItemEntity> items) {
        int totalCalories = 0;
        List<MealTemplateItemView> itemViews = new ArrayList<>();
        Map<String, String> logos = loadBrandLogosByName(
                items.stream().map(MealTemplateItemEntity::getBrandName).toList());
        for (MealTemplateItemEntity templateItem : items) {
            totalCalories += defaultInteger(templateItem.getCalories());
            itemViews.add(new MealTemplateItemView(
                    templateItem.getTemplateItemId(),
                    templateItem.getCustomFoodId(),
                    templateItem.getFoodCatalogItemId(),
                    templateItem.getFoodServingId(),
                    templateItem.getFoodName(),
                    templateItem.getBrandName(),
                    logoOf(logos, templateItem.getBrandName()),
                    templateItem.getQuantity(),
                    templateItem.getUnit(),
                    templateItem.getSnapshotServingLabel(),
                    templateItem.getCalories(),
                    templateItem.getProteinGrams(),
                    templateItem.getCarbsGrams(),
                    templateItem.getFatGrams(),
                    templateItem.getFiberGrams(),
                    templateItem.getSugarGrams(),
                    templateItem.getSodiumMg(),
                    templateItem.getRecipeId(),
                    templateItem.getRecipeServings()));
        }
        return new MealTemplateView(template.getTemplateId(), template.getName(), template.getMealType(),
                template.getNote(), totalCalories, itemViews);
    }

    /**
     * A recipe in a template is one row that shows the recipe name and a snapshot of its nutrition for the
     * chosen servings. Logging the template expands the recipe again from its current ingredients.
     */
    private MealTemplateItemEntity templateItemFromRecipe(UUID userId, UUID templateId, TemplateRecipeInput input,
                                                          int sortOrder) {
        RecipeEntity recipe = recipeLibraryService.findVisible(userId, input.recipeId());
        RecipeInsightService.RecipeComputation computation = recipeInsightService.compute(recipe);
        BigDecimal servings = input.servings();
        MealTemplateItemEntity templateItem = new MealTemplateItemEntity();
        templateItem.setTemplateItemId(UUID.randomUUID());
        templateItem.setTemplateId(templateId);
        templateItem.setFoodName(truncate(recipe.getName(), 120));
        templateItem.setQuantity(servings);
        templateItem.setUnit("serving");
        templateItem.setCalories(BigDecimal.valueOf(computation.kcalPerServing()).multiply(servings)
                .setScale(0, RoundingMode.HALF_UP).intValue());
        templateItem.setProteinGrams(scaleTwo(computation.proteinPerServing().multiply(servings)));
        templateItem.setCarbsGrams(scaleTwo(computation.carbsPerServing().multiply(servings)));
        templateItem.setFatGrams(scaleTwo(computation.fatPerServing().multiply(servings)));
        templateItem.setFiberGrams(scaleTwo(computation.fibrePerServing().multiply(servings)));
        templateItem.setSugarGrams(scaleTwo(computation.sugarPerServing().multiply(servings)));
        templateItem.setSodiumMg(scaleTwo(computation.sodiumPerServing().multiply(servings)));
        templateItem.setRecipeId(recipe.getRecipeId());
        templateItem.setRecipeServings(servings);
        templateItem.setSortOrder(sortOrder);
        return templateItem;
    }

    /**
     * The meal items for some servings of a recipe: one per ingredient, scaled by the servings eaten over
     * the servings the recipe makes, each snapshotted like a food logged by hand and tagged with the recipe.
     */
    private List<PlannedItem> plannedRecipeItems(UUID userId, RecipeEntity recipe, BigDecimal servings) {
        List<RecipeIngredientEntity> ingredients = recipeIngredientRepository
                .findByRecipeIdOrderBySortOrderAsc(recipe.getRecipeId());
        if (ingredients.isEmpty()) {
            throw new BadRequestException("Recipe has no ingredients");
        }
        BigDecimal factor = servings.divide(recipe.getServings(), 6, RoundingMode.HALF_UP);
        List<PlannedItem> planned = new ArrayList<>();
        for (RecipeIngredientEntity ingredient : ingredients) {
            planned.add(new PlannedItem(draftForIngredient(userId, ingredient, factor), recipe.getRecipeId(), servings));
        }
        return planned;
    }

    @Override
    @Transactional
    public MealView createMealFromRecipe(CurrentUserPrincipal principal, UUID recipeId, RecipeMealRequest request,
                                         String timezone) {
        RecipeEntity recipe = recipeLibraryService.findVisible(principal.userId(), recipeId);
        List<PlannedItem> planned = plannedRecipeItems(principal.userId(), recipe, request.servings());
        CreateMealRequest header = new CreateMealRequest(
                request.mealType(),
                truncate(recipe.getName(), 120),
                MealSource.MANUAL,
                request.consumedAt(),
                null,
                List.of());
        return createMealFromPlanned(principal.userId(), header, timezone, planned);
    }

    @Override
    @Transactional
    public MealView addRecipeToMeal(CurrentUserPrincipal principal, UUID mealId, UUID recipeId,
                                    RecipeAddItemsRequest request) {
        MealLogEntity meal = getOwnedMeal(principal.userId(), mealId);
        RecipeEntity recipe = recipeLibraryService.findVisible(principal.userId(), recipeId);
        List<PlannedItem> planned = plannedRecipeItems(principal.userId(), recipe, request.servings());
        List<MealItemEntity> items = new ArrayList<>(mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId()));
        items.addAll(persistMealItems(meal.getMealId(), planned));
        publishMealUpdated(meal, items);
        return toMealView(meal, items);
    }

    private MealTemplateItemEntity templateItemFromMealItem(UUID templateId, MealItemEntity src, int sortOrder) {
        MealTemplateItemEntity templateItem = new MealTemplateItemEntity();
        templateItem.setTemplateItemId(UUID.randomUUID());
        templateItem.setTemplateId(templateId);
        templateItem.setCustomFoodId(src.getCustomFoodId());
        templateItem.setFoodCatalogItemId(src.getFoodCatalogItemId());
        templateItem.setFoodServingId(src.getFoodServingId());
        templateItem.setExternalFoodRef(src.getExternalFoodRef());
        templateItem.setBarcode(src.getBarcode());
        templateItem.setFoodName(src.getFoodName());
        templateItem.setBrandName(src.getBrandName());
        templateItem.setQuantity(src.getQuantity());
        templateItem.setUnit(src.getUnit());
        templateItem.setServingSize(src.getServingSize());
        templateItem.setServingUnit(src.getServingUnit());
        templateItem.setSnapshotServingLabel(src.getSnapshotServingLabel());
        templateItem.setCalories(src.getCalories());
        templateItem.setProteinGrams(src.getProteinGrams());
        templateItem.setCarbsGrams(src.getCarbsGrams());
        templateItem.setFatGrams(src.getFatGrams());
        templateItem.setFiberGrams(src.getFiberGrams());
        templateItem.setSugarGrams(src.getSugarGrams());
        templateItem.setSodiumMg(src.getSodiumMg());
        templateItem.setSortOrder(sortOrder);
        return templateItem;
    }

    private MealTemplateItemEntity templateItemFromDraft(UUID templateId, MealItemDraft draft, int sortOrder) {
        MealTemplateItemEntity templateItem = new MealTemplateItemEntity();
        templateItem.setTemplateItemId(UUID.randomUUID());
        templateItem.setTemplateId(templateId);
        templateItem.setCustomFoodId(draft.customFoodId());
        templateItem.setFoodCatalogItemId(draft.foodCatalogItemId());
        templateItem.setFoodServingId(draft.foodServingId());
        templateItem.setExternalFoodRef(draft.externalFoodRef());
        templateItem.setBarcode(draft.barcode());
        templateItem.setFoodName(draft.foodName());
        templateItem.setBrandName(draft.brandName());
        templateItem.setQuantity(draft.quantity());
        templateItem.setUnit(draft.unit());
        templateItem.setServingSize(draft.servingSize());
        templateItem.setServingUnit(draft.servingUnit());
        templateItem.setSnapshotServingLabel(draft.snapshotServingLabel());
        templateItem.setCalories(draft.calories());
        templateItem.setProteinGrams(draft.proteinGrams());
        templateItem.setCarbsGrams(draft.carbsGrams());
        templateItem.setFatGrams(draft.fatGrams());
        templateItem.setFiberGrams(draft.fiberGrams());
        templateItem.setSugarGrams(draft.sugarGrams());
        templateItem.setSodiumMg(draft.sodiumMg());
        templateItem.setSortOrder(sortOrder);
        return templateItem;
    }

    @Override
    @Transactional
    public WaterLogView logWater(CurrentUserPrincipal principal, LogWaterRequest request, String timezone) {
        WaterLogEntity entity = new WaterLogEntity();
        entity.setWaterId(UUID.randomUUID());
        entity.setUserId(principal.userId());
        entity.setAmountMl(request.amountMl());
        entity.setLoggedAt(request.loggedAt() == null ? Instant.now(Clock.systemUTC()) : request.loggedAt());
        entity.setTimezone(blankToNull(timezone));
        return toWaterLogView(waterLogRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public WaterDaySummaryView getWaterDay(CurrentUserPrincipal principal, LocalDate date, String timezone) {
        ZoneId zone = resolveZone(timezone);
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant();
        List<WaterLogEntity> entries = waterLogRepository
                .findByUserIdAndLoggedAtGreaterThanEqualAndLoggedAtLessThanEqualOrderByLoggedAtAsc(
                        principal.userId(), from, to);
        int total = entries.stream().mapToInt(entry -> defaultInteger(entry.getAmountMl())).sum();
        List<DrinkWaterEntryView> drinks = nutritionDiaryService.listDrinkWater(principal.userId(), from, to.plusNanos(1));
        int fromDrinks = drinks.stream().mapToInt(drink -> defaultInteger(drink.waterMl())).sum();
        return new WaterDaySummaryView(date, zone.getId(), total,
                entries.stream().map(this::toWaterLogView).toList(), fromDrinks, drinks);
    }

    @Override
    @Transactional
    public void deleteWater(CurrentUserPrincipal principal, UUID waterId) {
        WaterLogEntity entity = waterLogRepository.findByWaterIdAndUserId(waterId, principal.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Water log not found"));
        waterLogRepository.delete(entity);
    }

    private WaterLogView toWaterLogView(WaterLogEntity entity) {
        return new WaterLogView(entity.getWaterId(), entity.getAmountMl(), entity.getLoggedAt(),
                entity.getTimezone());
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportFoodDiary(CurrentUserPrincipal principal, Instant from, Instant to, String format) {
        if (format != null && !format.isBlank() && !"csv".equals(format)) {
            throw new BadRequestException("Unsupported export format: " + format + " (only \"csv\")");
        }
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
        if (from.plus(EXPORT_MAX_DAYS, ChronoUnit.DAYS).isBefore(to)) {
            throw new BadRequestException("Export range exceeds " + EXPORT_MAX_DAYS + " days");
        }
        List<MealLogEntity> meals = new ArrayList<>(mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                        principal.userId(), from, to));
        meals.sort(Comparator.comparing(MealLogEntity::getConsumedAt));

        StringBuilder csv = new StringBuilder();
        csv.append("consumed_at,meal_type,meal_name,food_name,brand_name,quantity,unit,"
                + "calories,protein_g,carbs_g,fat_g,fiber_g,sugar_g,sodium_mg\n");
        for (MealLogEntity meal : meals) {
            for (MealItemEntity item : mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId())) {
                csv.append(csvField(meal.getConsumedAt().toString())).append(',')
                        .append(csvField(meal.getMealType().name())).append(',')
                        .append(csvField(meal.getName())).append(',')
                        .append(csvField(item.getFoodName())).append(',')
                        .append(csvField(item.getBrandName())).append(',')
                        .append(csvField(plainNumber(item.getQuantity()))).append(',')
                        .append(csvField(item.getUnit())).append(',')
                        .append(csvField(item.getCalories() == null ? "" : item.getCalories().toString())).append(',')
                        .append(csvField(plainNumber(item.getProteinGrams()))).append(',')
                        .append(csvField(plainNumber(item.getCarbsGrams()))).append(',')
                        .append(csvField(plainNumber(item.getFatGrams()))).append(',')
                        .append(csvField(plainNumber(item.getFiberGrams()))).append(',')
                        .append(csvField(plainNumber(item.getSugarGrams()))).append(',')
                        .append(csvField(plainNumber(item.getSodiumMg()))).append('\n');
            }
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String plainNumber(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoggedFoodView> listRecentFoods(CurrentUserPrincipal principal, int limit, String foodGroup) {
        return aggregateLoggedFoods(principal.userId(), false, limit, normalizeFoodGroup(foodGroup));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoggedFoodView> listFrequentFoods(CurrentUserPrincipal principal, int limit, String foodGroup) {
        return aggregateLoggedFoods(principal.userId(), true, limit, normalizeFoodGroup(foodGroup));
    }

    /**
     * Collapses the caller's recent meal items by food identity. The most-recently-consumed
     * occurrence becomes the representative (carrying the last-used serving snapshot). When
     * {@code byFrequency} is true the result is ordered by occurrence count (recency breaks ties);
     * otherwise it preserves recency order.
     */
    private List<LoggedFoodView> aggregateLoggedFoods(UUID userId, boolean byFrequency, int limit, String foodGroup) {
        int cappedLimit = Math.min(Math.max(limit, 1), LOGGED_FOOD_MAX_LIMIT);
        PageRequest scanWindow = PageRequest.of(0, LOGGED_FOOD_SCAN_LIMIT);
        Map<String, String> foodGroups = loadFoodGroups();
        List<MealItemRepository.RecentLoggedFoodRow> rows;
        if (foodGroup == null) {
            rows = mealItemRepository.findRecentItemsByUserId(userId, scanWindow);
        } else if (FOOD_GROUP_OTHER.equals(foodGroup)) {
            // OTHER also holds items with no category or a category that maps to no group, which a join cannot see.
            rows = mealItemRepository.findRecentItemsByUserId(userId, scanWindow).stream()
                    .filter(row -> FOOD_GROUP_OTHER.equals(
                            foodGroups.getOrDefault(row.getItem().getCategoryCode(), FOOD_GROUP_OTHER)))
                    .toList();
        } else {
            rows = mealItemRepository.findRecentItemsByUserIdAndFoodGroup(userId, foodGroup, scanWindow);
        }

        LinkedHashMap<String, MealItemRepository.RecentLoggedFoodRow> representative = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (MealItemRepository.RecentLoggedFoodRow row : rows) {
            MealItemEntity item = row.getItem();
            String key = loggedFoodKey(item);
            representative.putIfAbsent(key, row);
            counts.merge(key, 1L, Long::sum);
        }

        var entries = representative.entrySet().stream();
        if (byFrequency) {
            entries = entries.sorted(Comparator.comparingLong(
                    (Map.Entry<String, MealItemRepository.RecentLoggedFoodRow> e) -> counts.get(e.getKey())).reversed());
        }
        List<Map.Entry<String, MealItemRepository.RecentLoggedFoodRow>> picked = entries.limit(cappedLimit).toList();
        Map<UUID, Boolean> countsTowardWater = loadCountsTowardWater(picked.stream()
                .map(e -> e.getValue().getItem().getFoodCatalogItemId())
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<String, String> logos = loadBrandLogosByName(picked.stream()
                .map(e -> e.getValue().getItem().getBrandName()).toList());
        return picked.stream()
                .map(e -> toLoggedFoodView(e.getValue().getItem(), counts.get(e.getKey()),
                        e.getValue().getConsumedAt(), foodGroups, countsTowardWater, logos))
                .toList();
    }

    /** Whether each catalogue food counts toward water, by food id. */
    private Map<UUID, Boolean> loadCountsTowardWater(Collection<UUID> foodIds) {
        if (foodIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Boolean> result = new HashMap<>();
        for (FoodCatalogItemEntity food : foodCatalogItemRepository.findAllById(foodIds.stream().distinct().toList())) {
            result.put(food.getFoodId(), food.isCountsTowardWater());
        }
        return result;
    }

    /** A blank filter means no filter; anything else must be one of the plate groups. */
    private String normalizeFoodGroup(String foodGroup) {
        String normalized = blankToNull(foodGroup);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!FOOD_GROUPS.contains(upper)) {
            throw new BadRequestException("Unknown food group");
        }
        return upper;
    }

    /**
     * The identity two logged items share when they are the same food: the catalogue food, else the custom
     * food, else the external reference, else the name ignoring case. The prefix keeps the kinds apart.
     */
    private String loggedFoodKey(MealItemEntity item) {
        if (item.getFoodCatalogItemId() != null) {
            return "C:" + item.getFoodCatalogItemId();
        }
        if (item.getCustomFoodId() != null) {
            return "U:" + item.getCustomFoodId();
        }
        String externalRef = blankToNull(item.getExternalFoodRef());
        if (externalRef != null) {
            return "E:" + externalRef;
        }
        String name = item.getFoodName() == null ? "" : item.getFoodName().trim().toLowerCase(Locale.ROOT);
        return "N:" + name;
    }

    private LoggedFoodView toLoggedFoodView(MealItemEntity item, long timesLogged, Instant lastLoggedAt,
                                            Map<String, String> foodGroups, Map<UUID, Boolean> countsTowardWater,
                                            Map<String, String> logos) {
        return new LoggedFoodView(
                item.getCustomFoodId(),
                item.getFoodCatalogItemId(),
                item.getFoodServingId(),
                item.getExternalFoodRef(),
                item.getBarcode(),
                item.getFoodName(),
                item.getBrandName(),
                logoOf(logos, item.getBrandName()),
                item.getQuantity(),
                item.getUnit(),
                item.getServingSize(),
                item.getServingUnit(),
                item.getSnapshotServingLabel(),
                item.getCalories(),
                item.getProteinGrams(),
                item.getCarbsGrams(),
                item.getFatGrams(),
                item.getFiberGrams(),
                item.getSugarGrams(),
                item.getSodiumMg(),
                timesLogged,
                lastLoggedAt,
                foodGroupOf(item.getCategoryCode(), foodGroups),
                item.getFoodCatalogItemId() != null
                        && Boolean.TRUE.equals(countsTowardWater.get(item.getFoodCatalogItemId())));
    }

    @Override
    @Transactional(readOnly = true)
    public FoodDetailView getFood(CurrentUserPrincipal principal, UUID foodId) {
        return toFoodDetailView(getAccessibleFood(principal.userId(), foodId, LocalDate.now(ZoneId.systemDefault())));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodServingView> listFoodServings(CurrentUserPrincipal principal, UUID foodId) {
        FoodCatalogItemEntity food = getAccessibleFood(principal.userId(), foodId, LocalDate.now(ZoneId.systemDefault()));
        return foodServingRepository.findActiveByFoodId(food.getFoodId(), LocalDate.now(ZoneId.systemDefault())).stream()
                .map(this::toFoodServingView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodDetailView getFoodByBarcode(CurrentUserPrincipal principal, String barcode) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Optional<FoodBarcodeEntity> barcodeEntity = foodBarcodeRepository.findActiveByBarcode(barcode, today);
        if (barcodeEntity.isPresent()) {
            return toFoodDetailView(getAccessibleFood(principal.userId(), barcodeEntity.get().getFoodId(), today));
        }
        // Local miss: fall back to the external food source (no-op unless enabled).
        return externalFoodSource.findByBarcode(barcode)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
    }

    /**
     * Not {@code @Transactional}: the import runs in its own transaction so that, when a concurrent request
     * imported the same barcode first and the unique constraint fails, the food that request saved can be
     * read and returned in a fresh one.
     */
    @Override
    public FoodDetailView importFoodByBarcode(CurrentUserPrincipal principal, String barcode) {
        String normalizedBarcode = requireNonBlank(barcode, "barcode is required");
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Optional<FoodBarcodeEntity> existing = foodBarcodeRepository.findActiveByBarcode(normalizedBarcode, today);
        if (existing.isPresent()) {
            return toFoodDetailView(getAccessibleFood(principal.userId(), existing.get().getFoodId(), today));
        }

        FoodDetailView externalFood = externalFoodSource.findByBarcode(normalizedBarcode)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
        if (externalFood.calories() == null) {
            throw new BadRequestException("External food is missing calories and cannot be imported");
        }

        try {
            return transactionTemplate.execute(status -> importFoodByBarcodeInTransaction(
                    principal.userId(), normalizedBarcode, externalFood, today));
        } catch (DataIntegrityViolationException ex) {
            // Lost the race on the barcode: return the food the other import saved, or rethrow if there is none.
            return foodBarcodeRepository.findActiveByBarcode(normalizedBarcode, today)
                    .map(barcodeEntity -> toFoodDetailView(
                            getAccessibleFood(principal.userId(), barcodeEntity.getFoodId(), today)))
                    .orElseThrow(() -> ex);
        }
    }

    private FoodDetailView importFoodByBarcodeInTransaction(UUID userId,
                                                            String barcode,
                                                            FoodDetailView externalFood,
                                                            LocalDate today) {
        Optional<FoodBarcodeEntity> existing = foodBarcodeRepository.findActiveByBarcode(barcode, today);
        if (existing.isPresent()) {
            return toFoodDetailView(getAccessibleFood(userId, existing.get().getFoodId(), today));
        }
        FoodCatalogItemEntity importedFood = persistExternalFood(barcode, externalFood);
        return toFoodDetailView(importedFood);
    }

    @Override
    @Transactional
    public FoodDetailView cloneFood(CurrentUserPrincipal principal, UUID foodId) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        FoodCatalogItemEntity baseFood = getAccessibleFood(principal.userId(), foodId, today);
        if (baseFood.getScope() != FoodCatalogScope.GLOBAL) {
            throw new BadRequestException("Only global foods can be cloned");
        }

        FoodCatalogItemEntity clonedFood = new FoodCatalogItemEntity();
        clonedFood.setFoodId(UUID.randomUUID());
        clonedFood.setScope(FoodCatalogScope.USER);
        clonedFood.setOwnerUserId(principal.userId());
        clonedFood.setBaseFoodId(baseFood.getFoodId());
        clonedFood.setCreationMode(FoodCreationMode.CLONED);
        clonedFood.setSource(baseFood.getSource());
        clonedFood.setDisplayName(baseFood.getDisplayName());
        clonedFood.setNormalizedName(baseFood.getNormalizedName());
        clonedFood.setCategoryCode(baseFood.getCategoryCode());
        clonedFood.setBrandId(baseFood.getBrandId());
        clonedFood.setVerified(false);
        clonedFood.setDensityGramsPerMl(baseFood.getDensityGramsPerMl());
        clonedFood.setCountsTowardWater(baseFood.isCountsTowardWater());
        clonedFood.setActive(true);
        foodCatalogItemRepository.save(clonedFood);

        cloneFoodProfiles(baseFood.getFoodId(), clonedFood.getFoodId(), today);
        List<FoodServingEntity> clonedServings = cloneFoodServings(baseFood.getFoodId(), clonedFood.getFoodId(), today);
        cloneFoodNutrientDetails(baseFood.getFoodId(), clonedFood.getFoodId(), today);
        UUID defaultServingId = clonedServings.stream()
                .filter(FoodServingEntity::isDefaultServing)
                .map(FoodServingEntity::getServingId)
                .findFirst()
                .orElseGet(() -> clonedServings.isEmpty() ? null : clonedServings.get(0).getServingId());
        clonedFood.setDefaultServingId(defaultServingId);
        foodCatalogItemRepository.save(clonedFood);
        return toFoodDetailView(clonedFood);
    }

    private void applyMeal(MealLogEntity entity, CreateMealRequest request, String timezone) {
        entity.setMealType(request.mealType());
        entity.setName(request.name());
        entity.setSource(request.source());
        entity.setConsumedAt(request.consumedAt());
        entity.setTimezone(blankToNull(timezone));
        entity.setNote(request.note());
    }

    private void applyCustomFood(CustomFoodEntity entity, CreateCustomFoodRequest request) {
        entity.setName(request.name());
        entity.setBrandName(request.brandName());
        entity.setServingSize(request.servingSize());
        entity.setServingUnit(request.servingUnit());
        entity.setCalories(request.calories());
        entity.setProteinGrams(request.proteinGrams());
        entity.setCarbsGrams(request.carbsGrams());
        entity.setFatGrams(request.fatGrams());
        entity.setFiberGrams(request.fiberGrams());
        entity.setSugarGrams(request.sugarGrams());
        entity.setSodiumMg(request.sodiumMg());
    }

    private List<MealItemEntity> replaceMealItems(UUID userId, UUID mealId, List<MealItemInput> itemInputs) {
        return persistMealItems(mealId, plannedFromInputs(userId, itemInputs));
    }

    private List<PlannedItem> plannedFromInputs(UUID userId, List<MealItemInput> itemInputs) {
        List<PlannedItem> planned = new ArrayList<>();
        for (MealItemInput input : itemInputs) {
            planned.add(new PlannedItem(buildMealItemDraft(userId, input), null, null));
        }
        return planned;
    }

    private List<MealItemEntity> persistMealItems(UUID mealId, List<PlannedItem> plannedItems) {
        List<MealItemEntity> entities = new ArrayList<>();
        for (PlannedItem planned : plannedItems) {
            MealItemDraft draft = planned.draft();
            MealItemEntity entity = new MealItemEntity();
            entity.setRecipeId(planned.recipeId());
            entity.setRecipeServings(planned.recipeServings());
            entity.setMealItemId(UUID.randomUUID());
            entity.setMealId(mealId);
            entity.setCustomFoodId(draft.customFoodId());
            entity.setFoodCatalogItemId(draft.foodCatalogItemId());
            entity.setFoodServingId(draft.foodServingId());
            entity.setExternalFoodRef(draft.externalFoodRef());
            entity.setBarcode(draft.barcode());
            entity.setFoodName(draft.foodName());
            entity.setBrandName(draft.brandName());
            entity.setQuantity(draft.quantity());
            entity.setUnit(draft.unit());
            entity.setServingSize(draft.servingSize());
            entity.setServingUnit(draft.servingUnit());
            entity.setSnapshotServingLabel(draft.snapshotServingLabel());
            entity.setCalories(draft.calories());
            entity.setProteinGrams(draft.proteinGrams());
            entity.setCarbsGrams(draft.carbsGrams());
            entity.setFatGrams(draft.fatGrams());
            entity.setFiberGrams(draft.fiberGrams());
            entity.setSugarGrams(draft.sugarGrams());
            entity.setSodiumMg(draft.sodiumMg());
            applyClassificationSnapshot(entity, draft);
            entity.setGramsConsumed(draft.gramsConsumed());
            entity.setSaturatedFatGrams(draft.saturatedFatGrams());
            entity.setWaterMl(draft.waterMl());
            MealItemEntity savedItem = mealItemRepository.save(entity);
            replaceMealItemNutrientDetails(savedItem.getMealItemId(), draft.nutrientDetails());
            entities.add(savedItem);
        }
        return entities;
    }

    /**
     * Snapshots the food's classification (food-group category, NOVA, Nutri-Score, source) onto the meal
     * item at log time (SNAP-3), so downstream food-group / diet-quality analysis is immune to later
     * catalog recategorization and works for custom/external foods. Resolved from the referenced catalog
     * item or custom food; left null when neither is present (e.g. free-text/barcode-only entries).
     */
    private void applyClassificationSnapshot(MealItemEntity entity, MealItemDraft draft) {
        if (draft.foodCatalogItemId() != null) {
            foodCatalogItemRepository.findById(draft.foodCatalogItemId()).ifPresent(food -> {
                entity.setCategoryCode(food.getCategoryCode());
                entity.setNovaGroup(food.getNovaGroup());
                entity.setNutriScore(food.getNutriScore());
                entity.setSourceDataset(food.getSourceDataset());
            });
        } else if (draft.customFoodId() != null) {
            customFoodRepository.findById(draft.customFoodId())
                    .ifPresent(custom -> entity.setCategoryCode(custom.getCategoryCode()));
        }
    }

    private List<MealLogEntity> findMeals(UUID userId, Instant from, Instant to, MealType mealType) {
        if (from != null && to != null && mealType != null) {
            return mealLogRepository
                    .findByUserIdAndMealTypeAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                            userId, mealType, from, to);
        }
        if (from != null && to != null) {
            return mealLogRepository.findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                    userId, from, to);
        }
        if (mealType != null) {
            return mealLogRepository.findByUserIdAndMealTypeOrderByConsumedAtDesc(userId, mealType);
        }
        return mealLogRepository.findByUserIdOrderByConsumedAtDesc(userId);
    }

    private MealLogEntity getOwnedMeal(UUID userId, UUID mealId) {
        return mealLogRepository.findByMealIdAndUserId(mealId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal not found"));
    }

    private CustomFoodEntity getOwnedCustomFood(UUID userId, UUID customFoodId) {
        return customFoodRepository.findByCustomFoodIdAndUserId(customFoodId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom food not found"));
    }

    /** Reads only the categories this meal's items use; callers rendering many meals pass one shared map. */
    private MealView toMealView(MealLogEntity meal, List<MealItemEntity> items) {
        return toMealView(meal, items, loadFoodGroups(items.stream().map(MealItemEntity::getCategoryCode).toList()));
    }

    private MealView toMealView(MealLogEntity meal, List<MealItemEntity> items, Map<String, String> foodGroups) {
        Map<UUID, List<NutrientDetailView>> nutrientDetailsByMealItemId = loadMealItemNutrientDetails(items);
        Map<String, String> logos = loadBrandLogosByName(items.stream().map(MealItemEntity::getBrandName).toList());
        List<MealItemView> itemViews = items.stream()
                .sorted(Comparator.comparing(MealItemEntity::getDateTimeCreated))
                .map(item -> toMealItemView(item,
                        nutrientDetailsByMealItemId.getOrDefault(item.getMealItemId(), List.of()), foodGroups, logos))
                .toList();
        Totals totals = calculateTotals(items);
        return new MealView(
                meal.getMealId(),
                meal.getMealType(),
                meal.getName(),
                meal.getSource(),
                meal.getConsumedAt(),
                meal.getTimezone(),
                meal.getNote(),
                totals.totalCalories(),
                totals.totalProteinGrams(),
                totals.totalCarbsGrams(),
                totals.totalFatGrams(),
                itemViews.size(),
                itemViews,
                meal.getDateTimeCreated(),
                meal.getDateTimeUpdated());
    }

    /** The plate group of every food category, by category code. */
    private Map<String, String> loadFoodGroups() {
        Map<String, String> groups = new HashMap<>();
        for (FoodCategoryEntity category : foodCategoryRepository.findAll()) {
            groups.put(category.getCode(), category.getFoodGroup());
        }
        return groups;
    }

    /**
     * The plate group of just the given category codes, for a view that shows one food or one meal and so
     * does not need the whole category table. Codes with no category row are left out, which
     * {@link #foodGroupOf} turns into OTHER exactly as it does with the full map.
     */
    private Map<String, String> loadFoodGroups(List<String> categoryCodes) {
        List<String> codes = categoryCodes.stream().filter(java.util.Objects::nonNull).distinct().toList();
        Map<String, String> groups = new HashMap<>();
        if (codes.isEmpty()) {
            return groups;
        }
        for (FoodCategoryEntity category : foodCategoryRepository.findByCodeIn(codes)) {
            groups.put(category.getCode(), category.getFoodGroup());
        }
        return groups;
    }

    /** An item without a category has no group; a category code with no category row falls into OTHER. */
    private String foodGroupOf(String categoryCode, Map<String, String> foodGroups) {
        if (categoryCode == null) {
            return null;
        }
        return foodGroups.getOrDefault(categoryCode, FOOD_GROUP_OTHER);
    }

    private MealItemView toMealItemView(MealItemEntity entity, List<NutrientDetailView> nutrientDetails,
                                        Map<String, String> foodGroups, Map<String, String> logos) {
        return new MealItemView(
                entity.getMealItemId(),
                entity.getCustomFoodId(),
                entity.getFoodCatalogItemId(),
                entity.getFoodServingId(),
                entity.getExternalFoodRef(),
                entity.getBarcode(),
                entity.getFoodName(),
                entity.getBrandName(),
                logoOf(logos, entity.getBrandName()),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getServingSize(),
                entity.getServingUnit(),
                entity.getSnapshotServingLabel(),
                entity.getCalories(),
                entity.getProteinGrams(),
                entity.getCarbsGrams(),
                entity.getFatGrams(),
                entity.getFiberGrams(),
                entity.getSugarGrams(),
                entity.getSodiumMg(),
                nutrientDetails,
                entity.getCategoryCode(),
                foodGroupOf(entity.getCategoryCode(), foodGroups),
                entity.getGramsConsumed(),
                entity.getWaterMl(),
                entity.getSaturatedFatGrams(),
                entity.getRecipeId(),
                entity.getRecipeServings());
    }

    private CustomFoodView toCustomFoodView(CustomFoodEntity entity) {
        return new CustomFoodView(
                entity.getCustomFoodId(),
                entity.getName(),
                entity.getBrandName(),
                entity.getServingSize(),
                entity.getServingUnit(),
                entity.getCalories(),
                entity.getProteinGrams(),
                entity.getCarbsGrams(),
                entity.getFatGrams(),
                entity.getFiberGrams(),
                entity.getSugarGrams(),
                entity.getSodiumMg(),
                entity.isVerified());
    }

    private Totals calculateTotals(List<MealItemEntity> items) {
        int calories = 0;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal fiber = BigDecimal.ZERO;
        BigDecimal sugar = BigDecimal.ZERO;
        BigDecimal sodium = BigDecimal.ZERO;
        for (MealItemEntity item : items) {
            calories += defaultInteger(item.getCalories());
            protein = protein.add(defaultDecimal(item.getProteinGrams()));
            carbs = carbs.add(defaultDecimal(item.getCarbsGrams()));
            fat = fat.add(defaultDecimal(item.getFatGrams()));
            fiber = fiber.add(defaultDecimal(item.getFiberGrams()));
            sugar = sugar.add(defaultDecimal(item.getSugarGrams()));
            sodium = sodium.add(defaultDecimal(item.getSodiumMg()));
        }
        return new Totals(calories, protein, carbs, fat, fiber, sugar, sodium);
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int defaultInteger(Integer value) {
        return value == null ? 0 : value.intValue();
    }

    private void replaceMealItemNutrientDetails(UUID mealItemId, List<NutrientDetailInput> nutrientDetails) {
        if (nutrientDetails == null || nutrientDetails.isEmpty()) {
            return;
        }
        List<MealItemNutrientDetailEntity> entities = new ArrayList<>();
        for (NutrientDetailInput nutrientDetail : nutrientDetails) {
            MealItemNutrientDetailEntity entity = new MealItemNutrientDetailEntity();
            entity.setDetailId(UUID.randomUUID());
            entity.setMealItemId(mealItemId);
            entity.setNutrientCode(nutrientDetail.nutrientCode());
            entity.setDisplayName(nutrientDetail.displayName());
            entity.setAmount(nutrientDetail.amount());
            entity.setUnit(nutrientDetail.unit());
            entity.setSortOrder(nutrientDetail.sortOrder());
            entities.add(entity);
        }
        mealItemNutrientDetailRepository.saveAll(entities);
    }

    private Map<UUID, List<NutrientDetailView>> loadMealItemNutrientDetails(List<MealItemEntity> items) {
        List<UUID> mealItemIds = items.stream()
                .map(MealItemEntity::getMealItemId)
                .toList();
        if (mealItemIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<NutrientDetailView>> result = new HashMap<>();
        for (MealItemNutrientDetailEntity detail : mealItemNutrientDetailRepository
                .findByMealItemIdInOrderByMealItemIdAscSortOrderAsc(mealItemIds)) {
            result.computeIfAbsent(detail.getMealItemId(), ignored -> new ArrayList<>())
                    .add(toNutrientDetailView(detail));
        }
        return result;
    }

    private FoodCatalogItemEntity getAccessibleFood(UUID userId, UUID foodId, LocalDate onDate) {
        return foodCatalogItemRepository.findAccessibleFoodById(foodId, userId, onDate)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
    }

    private FoodDetailView toFoodDetailView(FoodCatalogItemEntity food) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        FoodNutrientProfileEntity profile = foodNutrientProfileRepository.findActiveProfilesByFoodId(food.getFoodId(), today)
                .stream()
                .findFirst()
                .orElse(null);
        Map<UUID, String> brandNames = food.getBrandId() == null
                ? Map.of()
                : loadBrandNames(List.of(food.getBrandId()));
        Map<UUID, String> brandLogos = food.getBrandId() == null
                ? Map.of()
                : loadBrandLogos(List.of(food.getBrandId()));
        String primaryBarcode = loadPrimaryBarcodes(List.of(food.getFoodId()), today).get(food.getFoodId());
        List<FoodServingView> servings = foodServingRepository.findActiveByFoodId(food.getFoodId(), today).stream()
                .map(this::toFoodServingView)
                .toList();
        List<NutrientDetailView> nutrientDetails = foodNutrientDetailRepository.findActiveByFoodId(food.getFoodId(), today).stream()
                .map(this::toNutrientDetailView)
                .toList();
        BigDecimal alcoholGrams = nutrientDetails.stream()
                .filter(detail -> ALCOHOL_CODE.equals(detail.nutrientCode()))
                .map(NutrientDetailView::amount)
                .findFirst()
                .orElse(null);

        return new FoodDetailView(
                food.getFoodId(),
                food.getDisplayName(),
                food.getNormalizedName(),
                food.getBrandId() == null ? null : brandNames.get(food.getBrandId()),
                food.getBrandId() == null ? null : brandLogos.get(food.getBrandId()),
                food.getCategoryCode(),
                food.getScope(),
                food.getBaseFoodId(),
                food.getCreationMode(),
                food.getSource(),
                food.isVerified(),
                food.getDensityGramsPerMl(),
                profile == null ? null : profile.getCalories(),
                profile == null ? null : profile.getProteinGrams(),
                profile == null ? null : profile.getCarbsGrams(),
                profile == null ? null : profile.getFatGrams(),
                profile == null ? null : profile.getSaturatedFatGrams(),
                profile == null ? null : profile.getTransFatGrams(),
                profile == null ? null : profile.getFiberGrams(),
                profile == null ? null : profile.getSugarGrams(),
                profile == null ? null : profile.getSodiumMg(),
                profile == null ? null : profile.getPotassiumMg(),
                profile == null ? null : profile.getCholesterolMg(),
                profile == null ? null : profile.getBasisQuantity(),
                profile == null ? null : profile.getBasisUnit(),
                primaryBarcode,
                food.getNovaGroup(),
                food.getNutriScore(),
                food.getSourceDataset(),
                servings,
                nutrientDetails,
                foodGroupOf(food.getCategoryCode(), loadFoodGroups(Collections.singletonList(food.getCategoryCode()))),
                food.isCountsTowardWater(),
                profile == null ? null : profile.getWaterG(),
                alcoholGrams);
    }

    private FoodSearchResultView toFoodSearchResultView(FoodCatalogItemEntity food,
                                                        Map<UUID, String> brandNames,
                                                        Map<UUID, String> brandLogos,
                                                        Map<String, String> categoryNames,
                                                        Map<String, String> foodGroups,
                                                        FoodNutrientProfileEntity profile,
                                                        String primaryBarcode,
                                                        FoodServingEntity defaultServing) {
        String basisLabel = profile == null ? null : profile.getBasisQuantity().stripTrailingZeros().toPlainString() + " " + profile.getBasisUnit();
        return new FoodSearchResultView(
                food.getFoodId(),
                food.getDisplayName(),
                food.getBrandId() == null ? null : brandNames.get(food.getBrandId()),
                food.getBrandId() == null ? null : brandLogos.get(food.getBrandId()),
                food.getCategoryCode(),
                food.getCategoryCode() == null ? null : categoryNames.get(food.getCategoryCode()),
                food.getScope(),
                food.getSource(),
                food.isVerified(),
                profile == null ? null : profile.getCalories(),
                basisLabel,
                primaryBarcode,
                foodGroupOf(food.getCategoryCode(), foodGroups),
                food.isCountsTowardWater(),
                defaultServing == null ? null : new DefaultServingView(
                        defaultServing.getServingId(), defaultServing.getLabel(), defaultServing.getGramsEquivalent()));
    }

    /** The serving each food is shown with: the one marked default, otherwise the first by sort order. */
    private Map<UUID, FoodServingEntity> loadDefaultServings(List<FoodCatalogItemEntity> foods, LocalDate today) {
        if (foods.isEmpty()) {
            return Map.of();
        }
        Map<UUID, FoodServingEntity> byFood = new HashMap<>();
        for (FoodServingEntity serving : foodServingRepository.findActiveByFoodIdIn(
                foods.stream().map(FoodCatalogItemEntity::getFoodId).toList(), today)) {
            FoodServingEntity current = byFood.get(serving.getFoodId());
            if (current == null || (!current.isDefaultServing() && serving.isDefaultServing())) {
                byFood.put(serving.getFoodId(), serving);
            }
        }
        return byFood;
    }

    private FoodServingView toFoodServingView(FoodServingEntity entity) {
        return new FoodServingView(
                entity.getServingId(),
                entity.getLabel(),
                entity.getQuantity(),
                entity.getUnit(),
                entity.getGramsEquivalent(),
                entity.getMillilitersEquivalent(),
                entity.getSortOrder(),
                entity.isDefaultServing());
    }

    private NutrientDetailView toNutrientDetailView(MealItemNutrientDetailEntity entity) {
        return new NutrientDetailView(
                entity.getNutrientCode(),
                entity.getDisplayName(),
                entity.getAmount(),
                entity.getUnit(),
                entity.getSortOrder());
    }

    private NutrientDetailView toNutrientDetailView(FoodNutrientDetailEntity entity) {
        return new NutrientDetailView(
                entity.getNutrientCode(),
                entity.getDisplayName(),
                entity.getAmount(),
                entity.getUnit(),
                entity.getSortOrder());
    }

    private Map<UUID, String> loadBrandNames(Collection<UUID> brandIds) {
        List<UUID> ids = brandIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (FoodBrandEntity brand : foodBrandRepository.findAllById(ids)) {
            result.put(brand.getBrandId(), brand.getName());
        }
        return result;
    }

    private Map<UUID, String> loadBrandLogos(Collection<UUID> brandIds) {
        List<UUID> ids = brandIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (FoodBrandEntity brand : foodBrandRepository.findAllById(ids)) {
            if (brand.getLogoUrl() != null) {
                result.put(brand.getBrandId(), brand.getLogoUrl());
            }
        }
        return result;
    }

    /** The logo of each known brand, by the brand name a line item stores. Brands without a logo are left out. */
    private Map<String, String> loadBrandLogosByName(Collection<String> brandNames) {
        List<String> normalized = brandNames.stream()
                .map(this::blankToNull)
                .filter(java.util.Objects::nonNull)
                .map(this::normalizeName)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (FoodBrandEntity brand : foodBrandRepository.findByNormalizedNameInAndActiveTrue(normalized)) {
            if (brand.getLogoUrl() != null) {
                result.put(brand.getNormalizedName(), brand.getLogoUrl());
            }
        }
        return result;
    }

    private String logoOf(Map<String, String> logos, String brandName) {
        String name = blankToNull(brandName);
        return name == null ? null : logos.get(normalizeName(name));
    }

    private Map<String, String> loadCategoryNames(Collection<String> categoryCodes) {
        List<String> codes = categoryCodes.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (codes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (FoodCategoryEntity category : foodCategoryRepository.findByCodeInAndActiveTrue(codes)) {
            result.put(category.getCode(), category.getDisplayName());
        }
        return result;
    }

    /**
     * The newest active profile of each food, read in one query for the whole page rather than one per food.
     * The rows come back newest first, so the first one seen for a food is the one a single-food read would pick.
     */
    private Map<UUID, FoodNutrientProfileEntity> loadPrimaryProfiles(List<UUID> foodIds, LocalDate onDate) {
        Map<UUID, FoodNutrientProfileEntity> result = new HashMap<>();
        if (foodIds.isEmpty()) {
            return result;
        }
        for (FoodNutrientProfileEntity profile
                : foodNutrientProfileRepository.findActiveProfilesByFoodIdIn(foodIds.stream().distinct().toList(), onDate)) {
            result.putIfAbsent(profile.getFoodId(), profile);
        }
        return result;
    }

    private Map<UUID, String> loadPrimaryBarcodes(List<UUID> foodIds, LocalDate onDate) {
        Map<UUID, String> result = new HashMap<>();
        for (FoodBarcodeEntity barcode : foodBarcodeRepository.findActiveByFoodIds(foodIds.stream().distinct().toList(), onDate)) {
            if (!result.containsKey(barcode.getFoodId())) {
                result.put(barcode.getFoodId(), barcode.getBarcode());
            }
        }
        return result;
    }

    private String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String normalized = query.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeName(String value) {
        return truncate(value.toLowerCase(Locale.ROOT), 120);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private MealItemDraft buildMealItemDraft(UUID userId, MealItemInput input) {
        if (input.foodCatalogItemId() != null) {
            return buildMealItemFromCatalog(userId, input);
        }
        validateManualMealItem(input);
        return new MealItemDraft(
                input.customFoodId(),
                null,
                null,
                input.externalFoodRef(),
                input.barcode(),
                input.foodName().trim(),
                blankToNull(input.brandName()),
                input.quantity(),
                input.unit().trim(),
                input.servingSize(),
                blankToNull(input.servingUnit()),
                blankToNull(input.snapshotServingLabel()),
                input.calories(),
                input.proteinGrams(),
                input.carbsGrams(),
                input.fatGrams(),
                input.fiberGrams(),
                input.sugarGrams(),
                input.sodiumMg(),
                input.nutrientDetails(),
                null,
                null,
                null);
    }

    private MealItemDraft buildMealItemFromCatalog(UUID userId, MealItemInput input) {
        return buildMealItemFromCatalog(userId, input, false);
    }

    /**
     * Builds the draft for a catalogue food. With {@code byGrams} the input quantity is a weight in grams and
     * no serving is used, so the nutrition is the profile per 100 g (or per 100 ml at the food's density)
     * scaled to those grams.
     */
    private MealItemDraft buildMealItemFromCatalog(UUID userId, MealItemInput input, boolean byGrams) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        FoodCatalogItemEntity food = foodCatalogItemRepository.findAccessibleFoodById(input.foodCatalogItemId(), userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
        FoodNutrientProfileEntity profile = foodNutrientProfileRepository.findActiveProfilesByFoodId(food.getFoodId(), today).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Food nutrient profile is unavailable"));
        FoodServingEntity serving = byGrams ? null : resolveServing(food, input.foodServingId(), today);
        BigDecimal multiplier = byGrams ? resolveGramsMultiplier(food, profile, input.quantity())
                : resolveMultiplier(input, profile, serving);
        Map<UUID, String> brandNames = loadBrandNames(
                food.getBrandId() == null ? List.of() : List.of(food.getBrandId()));
        List<NutrientDetailInput> nutrientDetails = buildScaledNutrientDetails(food.getFoodId(), today, multiplier);
        return new MealItemDraft(
                input.customFoodId(),
                food.getFoodId(),
                serving == null ? null : serving.getServingId(),
                input.externalFoodRef(),
                input.barcode(),
                food.getDisplayName(),
                food.getBrandId() == null ? null : brandNames.get(food.getBrandId()),
                input.quantity(),
                serving == null ? requireNonBlank(input.unit(), "unit is required when foodServingId is omitted") : serving.getUnit(),
                serving == null ? input.servingSize() : serving.getQuantity(),
                serving == null ? blankToNull(input.servingUnit()) : serving.getUnit(),
                serving == null ? blankToNull(input.snapshotServingLabel()) : serving.getLabel(),
                scaleInteger(profile.getCalories(), multiplier),
                scaleDecimal(profile.getProteinGrams(), multiplier),
                scaleDecimal(profile.getCarbsGrams(), multiplier),
                scaleDecimal(profile.getFatGrams(), multiplier),
                scaleDecimal(profile.getFiberGrams(), multiplier),
                scaleDecimal(profile.getSugarGrams(), multiplier),
                scaleDecimal(profile.getSodiumMg(), multiplier),
                nutrientDetails,
                resolveGramsConsumed(food, profile, multiplier),
                scaleDecimal(profile.getSaturatedFatGrams(), multiplier),
                resolveWaterMl(food, profile, multiplier));
    }

    /**
     * The weight of the portion in grams, which the plate on the Diary is drawn from. The nutrient profile
     * gives the basis the portion was scaled against, so grams are the multiplier times a gram basis, or a
     * millilitre basis times the food's density. A millilitre food with no known density weighs as much as
     * water, the same rule {@link #resolveGramsMultiplier} logs it by, so the weight shown for a portion is
     * the weight the user entered. Null when the basis is neither grams nor millilitres.
     */
    private BigDecimal resolveGramsConsumed(FoodCatalogItemEntity food, FoodNutrientProfileEntity profile, BigDecimal multiplier) {
        BigDecimal basisAmount = profile.getBasisQuantity().multiply(multiplier);
        if (matchesUnit(profile.getBasisUnit(), "g")) {
            return basisAmount.setScale(2, RoundingMode.HALF_UP);
        }
        if (matchesUnit(profile.getBasisUnit(), "ml")) {
            return basisAmount.multiply(gramsPerMl(food)).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** The food's density, or that of water (1 g per ml) when no usable density is recorded. */
    private static BigDecimal gramsPerMl(FoodCatalogItemEntity food) {
        BigDecimal density = food.getDensityGramsPerMl();
        return density != null && density.signum() > 0 ? density : BigDecimal.ONE;
    }

    /**
     * The water a drink adds to the day, in millilitres, taken as the food's water content scaled to the
     * portion (a gram of water is a millilitre). Null for a food that does not count toward water or has
     * no recorded water content.
     */
    private Integer resolveWaterMl(FoodCatalogItemEntity food, FoodNutrientProfileEntity profile, BigDecimal multiplier) {
        if (!food.isCountsTowardWater() || profile.getWaterG() == null) {
            return null;
        }
        return profile.getWaterG().multiply(multiplier).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private void validateManualMealItem(MealItemInput input) {
        if (input.foodName() == null || input.foodName().isBlank()) {
            throw new BadRequestException("foodName is required when foodCatalogItemId is not provided");
        }
        if (input.unit() == null || input.unit().isBlank()) {
            throw new BadRequestException("unit is required when foodCatalogItemId is not provided");
        }
        if (input.calories() == null) {
            throw new BadRequestException("calories is required when foodCatalogItemId is not provided");
        }
    }

    private FoodServingEntity resolveServing(FoodCatalogItemEntity food, UUID servingId, LocalDate today) {
        if (servingId != null) {
            FoodServingEntity serving = foodServingRepository.findActiveByServingId(servingId, today)
                    .orElseThrow(() -> new ResourceNotFoundException("Food serving not found"));
            if (!food.getFoodId().equals(serving.getFoodId())) {
                throw new BadRequestException("Food serving does not belong to the selected food");
            }
            return serving;
        }
        // One read serves both choices: the default serving if the food has one, else its first serving.
        List<FoodServingEntity> servings = foodServingRepository.findActiveByFoodId(food.getFoodId(), today);
        return servings.stream()
                .filter(FoodServingEntity::isDefaultServing)
                .findFirst()
                .orElseGet(() -> servings.stream().findFirst().orElse(null));
    }

    private BigDecimal resolveMultiplier(MealItemInput input,
                                         FoodNutrientProfileEntity profile,
                                         FoodServingEntity serving) {
        BigDecimal basisQuantity = profile.getBasisQuantity();
        if (basisQuantity == null || basisQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Food nutrient profile basis quantity is invalid");
        }
        BigDecimal totalBasisQuantity;
        if (serving != null) {
            totalBasisQuantity = resolveServingBasisQuantity(profile, serving).multiply(input.quantity());
        } else if (matchesUnit(profile.getBasisUnit(), input.unit())) {
            totalBasisQuantity = input.quantity();
        } else if (input.servingSize() != null && matchesUnit(profile.getBasisUnit(), input.servingUnit())) {
            totalBasisQuantity = input.servingSize().multiply(input.quantity());
        } else {
            throw new BadRequestException("Unable to derive nutrient totals from the selected food and serving");
        }
        return totalBasisQuantity.divide(basisQuantity, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveGramsMultiplier(FoodCatalogItemEntity food, FoodNutrientProfileEntity profile, BigDecimal grams) {
        BigDecimal basisQuantity = profile.getBasisQuantity();
        if (basisQuantity == null || basisQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Food nutrient profile basis quantity is invalid");
        }
        if (matchesUnit(profile.getBasisUnit(), "g")) {
            return grams.divide(basisQuantity, 6, RoundingMode.HALF_UP);
        }
        if (matchesUnit(profile.getBasisUnit(), "ml")) {
            // Without a known density the food is taken to weigh as much as water (1 g per ml).
            return grams.divide(gramsPerMl(food), 6, RoundingMode.HALF_UP).divide(basisQuantity, 6, RoundingMode.HALF_UP);
        }
        throw new BadRequestException("Unable to derive nutrient totals from grams for this food");
    }

    private BigDecimal resolveServingBasisQuantity(FoodNutrientProfileEntity profile, FoodServingEntity serving) {
        if (matchesUnit(profile.getBasisUnit(), serving.getUnit())) {
            return serving.getQuantity();
        }
        if (matchesUnit(profile.getBasisUnit(), "g") && serving.getGramsEquivalent() != null) {
            return serving.getGramsEquivalent();
        }
        if (matchesUnit(profile.getBasisUnit(), "ml") && serving.getMillilitersEquivalent() != null) {
            return serving.getMillilitersEquivalent();
        }
        throw new BadRequestException("Unable to convert serving to nutrient profile basis unit");
    }

    private List<NutrientDetailInput> buildScaledNutrientDetails(UUID foodId, LocalDate today, BigDecimal multiplier) {
        return foodNutrientDetailRepository.findActiveByFoodId(foodId, today).stream()
                .map(detail -> new NutrientDetailInput(
                        detail.getNutrientCode(),
                        detail.getDisplayName(),
                        scaleDecimal(detail.getAmount(), multiplier),
                        detail.getUnit(),
                        detail.getSortOrder()))
                .toList();
    }

    private List<FoodServingEntity> cloneFoodServings(UUID sourceFoodId, UUID targetFoodId, LocalDate today) {
        List<FoodServingEntity> clonedServings = new ArrayList<>();
        for (FoodServingEntity baseServing : foodServingRepository.findActiveByFoodId(sourceFoodId, today)) {
            FoodServingEntity clonedServing = new FoodServingEntity();
            clonedServing.setServingId(UUID.randomUUID());
            clonedServing.setFoodId(targetFoodId);
            clonedServing.setLabel(baseServing.getLabel());
            clonedServing.setQuantity(baseServing.getQuantity());
            clonedServing.setUnit(baseServing.getUnit());
            clonedServing.setGramsEquivalent(baseServing.getGramsEquivalent());
            clonedServing.setMillilitersEquivalent(baseServing.getMillilitersEquivalent());
            clonedServing.setSortOrder(baseServing.getSortOrder());
            clonedServing.setDefaultServing(baseServing.isDefaultServing());
            clonedServing.setActive(true);
            clonedServings.add(foodServingRepository.save(clonedServing));
        }
        return clonedServings;
    }

    private FoodCatalogItemEntity persistExternalFood(String barcode, FoodDetailView externalFood) {
        UUID foodId = UUID.randomUUID();
        UUID brandId = resolveImportedBrandId(externalFood.brandName());
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.IMPORTED);
        food.setCreationMode(FoodCreationMode.MANUAL);
        food.setDisplayName(truncate(requireNonBlank(externalFood.displayName(), "food name is required"), 120));
        food.setNormalizedName(normalizeName(food.getDisplayName()));
        food.setCategoryCode(Optional.ofNullable(blankToNull(externalFood.categoryCode())).orElse("OTHER"));
        food.setBrandId(brandId);
        food.setVerified(false);
        food.setDensityGramsPerMl(externalFood.densityGramsPerMl());
        // Provenance + diet-quality signals from the external source (Open Food Facts today). Captured
        // here so analytics can use NOVA / Nutri-Score and so imports are upsert-keyable by source ref.
        food.setSourceDataset("OFF");
        food.setSourceRef(barcode);
        food.setNovaGroup(externalFood.novaGroup());
        food.setNutriScore(externalFood.nutriScore());
        food.setActive(true);
        foodCatalogItemRepository.save(food);

        FoodNutrientProfileEntity profile = new FoodNutrientProfileEntity();
        profile.setNutrientProfileId(UUID.randomUUID());
        profile.setFoodId(foodId);
        profile.setBasisQuantity(Optional.ofNullable(externalFood.basisQuantity()).orElse(new BigDecimal("100.00")));
        profile.setBasisUnit(Optional.ofNullable(blankToNull(externalFood.basisUnit())).orElse("g"));
        profile.setCalories(externalFood.calories());
        profile.setProteinGrams(externalFood.proteinGrams());
        profile.setCarbsGrams(externalFood.carbsGrams());
        profile.setFatGrams(externalFood.fatGrams());
        profile.setSaturatedFatGrams(externalFood.saturatedFatGrams());
        profile.setTransFatGrams(externalFood.transFatGrams());
        profile.setFiberGrams(externalFood.fiberGrams());
        profile.setSugarGrams(externalFood.sugarGrams());
        profile.setSodiumMg(externalFood.sodiumMg());
        profile.setPotassiumMg(externalFood.potassiumMg());
        profile.setCholesterolMg(externalFood.cholesterolMg());
        profile.setActive(true);
        foodNutrientProfileRepository.save(profile);

        FoodServingEntity serving = new FoodServingEntity();
        serving.setServingId(UUID.randomUUID());
        serving.setFoodId(foodId);
        serving.setLabel(profile.getBasisQuantity().stripTrailingZeros().toPlainString() + " " + profile.getBasisUnit());
        serving.setQuantity(profile.getBasisQuantity());
        serving.setUnit(profile.getBasisUnit());
        // Compared the same way as everywhere else units meet, so a basis of "G" still gets its weight.
        if (matchesUnit(profile.getBasisUnit(), "g")) {
            serving.setGramsEquivalent(profile.getBasisQuantity());
        }
        serving.setSortOrder(1);
        serving.setDefaultServing(true);
        serving.setActive(true);
        foodServingRepository.save(serving);

        for (NutrientDetailView nutrientDetail : externalFood.nutrientDetails()) {
            FoodNutrientDetailEntity detail = new FoodNutrientDetailEntity();
            detail.setDetailId(UUID.randomUUID());
            detail.setFoodId(foodId);
            detail.setNutrientCode(nutrientDetail.nutrientCode());
            detail.setDisplayName(nutrientDetail.displayName());
            detail.setAmount(nutrientDetail.amount());
            detail.setUnit(nutrientDetail.unit());
            detail.setSortOrder(nutrientDetail.sortOrder());
            detail.setActive(true);
            foodNutrientDetailRepository.save(detail);
        }

        FoodBarcodeEntity barcodeEntity = new FoodBarcodeEntity();
        barcodeEntity.setBarcodeId(UUID.randomUUID());
        barcodeEntity.setFoodId(foodId);
        barcodeEntity.setBarcode(barcode);
        barcodeEntity.setPrimaryBarcode(true);
        barcodeEntity.setActive(true);
        foodBarcodeRepository.saveAndFlush(barcodeEntity);

        food.setDefaultServingId(serving.getServingId());
        return foodCatalogItemRepository.save(food);
    }

    private UUID resolveImportedBrandId(String brandName) {
        String normalizedBrandName = blankToNull(brandName);
        if (normalizedBrandName == null) {
            return null;
        }
        String safeBrandName = truncate(normalizedBrandName, 120);
        String normalizedName = normalizeName(safeBrandName);
        return foodBrandRepository.findByNormalizedNameAndActiveTrue(normalizedName)
                .map(FoodBrandEntity::getBrandId)
                .orElseGet(() -> {
                    FoodBrandEntity brand = new FoodBrandEntity();
                    brand.setBrandId(UUID.randomUUID());
                    brand.setName(safeBrandName);
                    brand.setNormalizedName(normalizedName);
                    brand.setActive(true);
                    return foodBrandRepository.save(brand).getBrandId();
                });
    }

    private void cloneFoodProfiles(UUID sourceFoodId, UUID targetFoodId, LocalDate today) {
        for (FoodNutrientProfileEntity baseProfile : foodNutrientProfileRepository.findActiveProfilesByFoodId(sourceFoodId, today)) {
            FoodNutrientProfileEntity clonedProfile = new FoodNutrientProfileEntity();
            clonedProfile.setNutrientProfileId(UUID.randomUUID());
            clonedProfile.setFoodId(targetFoodId);
            clonedProfile.setBasisQuantity(baseProfile.getBasisQuantity());
            clonedProfile.setBasisUnit(baseProfile.getBasisUnit());
            clonedProfile.setCalories(baseProfile.getCalories());
            clonedProfile.setProteinGrams(baseProfile.getProteinGrams());
            clonedProfile.setCarbsGrams(baseProfile.getCarbsGrams());
            clonedProfile.setFatGrams(baseProfile.getFatGrams());
            clonedProfile.setSaturatedFatGrams(baseProfile.getSaturatedFatGrams());
            clonedProfile.setTransFatGrams(baseProfile.getTransFatGrams());
            clonedProfile.setFiberGrams(baseProfile.getFiberGrams());
            clonedProfile.setSugarGrams(baseProfile.getSugarGrams());
            clonedProfile.setSodiumMg(baseProfile.getSodiumMg());
            clonedProfile.setPotassiumMg(baseProfile.getPotassiumMg());
            clonedProfile.setCholesterolMg(baseProfile.getCholesterolMg());
            clonedProfile.setWaterG(baseProfile.getWaterG());
            clonedProfile.setActive(true);
            foodNutrientProfileRepository.save(clonedProfile);
        }
    }

    private void cloneFoodNutrientDetails(UUID sourceFoodId, UUID targetFoodId, LocalDate today) {
        for (FoodNutrientDetailEntity baseDetail : foodNutrientDetailRepository.findActiveByFoodId(sourceFoodId, today)) {
            FoodNutrientDetailEntity clonedDetail = new FoodNutrientDetailEntity();
            clonedDetail.setDetailId(UUID.randomUUID());
            clonedDetail.setFoodId(targetFoodId);
            clonedDetail.setNutrientCode(baseDetail.getNutrientCode());
            clonedDetail.setDisplayName(baseDetail.getDisplayName());
            clonedDetail.setAmount(baseDetail.getAmount());
            clonedDetail.setUnit(baseDetail.getUnit());
            clonedDetail.setSortOrder(baseDetail.getSortOrder());
            clonedDetail.setActive(true);
            foodNutrientDetailRepository.save(clonedDetail);
        }
    }

    private boolean matchesUnit(String left, String right) {
        return left != null
                && right != null
                && String.CASE_INSENSITIVE_ORDER.compare(left, right) == 0;
    }

    private Integer scaleInteger(Integer value, BigDecimal multiplier) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value)
                .multiply(multiplier)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal scaleDecimal(BigDecimal value, BigDecimal multiplier) {
        if (value == null) {
            return null;
        }
        return value.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNonBlank(String value, String message) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private void publishMealLogged(MealLogEntity meal, List<MealItemEntity> items) {
        Totals totals = calculateTotals(items);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealLogged",
                1,
                Instant.now(Clock.systemUTC()),
                SOURCE,
                meal.getUserId(),
                new MealLoggedPayload(
                        meal.getMealId(),
                        meal.getMealType().name(),
                        meal.getConsumedAt(),
                        totals.totalCalories(),
                        totals.totalProteinGrams(),
                        totals.totalCarbsGrams(),
                        totals.totalFatGrams(),
                        items.size(),
                        meal.getSource().name(),
                        caloriesByCategoryCode(items))));
    }

    /**
     * Energy for the meal per internal category code (summed kcal), over items that carry a category snapshot
     * (SNAP-3). The keys are category codes such as {@code FRUITS}, not the plate food groups; the event field
     * this fills is still called {@code foodGroupCalories} because that name is part of the MealLogged and
     * MealUpdated contract that analytics reads (EV-1), so only this method carries the accurate name.
     * Unclassified items are omitted; null calories count as 0.
     */
    private static Map<String, Integer> caloriesByCategoryCode(List<MealItemEntity> items) {
        Map<String, Integer> byGroup = new HashMap<>();
        for (MealItemEntity item : items) {
            String code = item.getCategoryCode();
            if (code == null) {
                continue;
            }
            Integer kcal = item.getCalories() == null ? Integer.valueOf(0) : item.getCalories();
            byGroup.merge(code, kcal, Integer::sum);
        }
        return byGroup;
    }

    private void publishMealUpdated(MealLogEntity meal, List<MealItemEntity> items) {
        Totals totals = calculateTotals(items);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealUpdated",
                1,
                Instant.now(Clock.systemUTC()),
                SOURCE,
                meal.getUserId(),
                new MealUpdatedPayload(
                        meal.getMealId(),
                        meal.getMealType().name(),
                        meal.getConsumedAt(),
                        totals.totalCalories(),
                        totals.totalProteinGrams(),
                        totals.totalCarbsGrams(),
                        totals.totalFatGrams(),
                        items.size(),
                        meal.getSource().name(),
                        caloriesByCategoryCode(items))));
    }

    /**
     * Bad input from the client is reported with {@link BadRequestException}, like every other check in this
     * service, so the error carries the service's own code and status rather than relying on the generic
     * handler for {@link IllegalArgumentException}.
     */
    private void validateDateRange(Instant from, Instant to) {
        if ((from == null) != (to == null)) {
            throw new BadRequestException("from and to must be provided together");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("to must not be before from");
        }
    }

    /** A blank timezone means UTC; one that is not a known zone id is the client's error (400). */
    private ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Invalid timezone");
        }
    }

    private record Totals(
            int totalCalories,
            BigDecimal totalProteinGrams,
            BigDecimal totalCarbsGrams,
            BigDecimal totalFatGrams,
            BigDecimal totalFiberGrams,
            BigDecimal totalSugarGrams,
            BigDecimal totalSodiumMg) {
    }

    /** A meal item ready to be saved, with the recipe it came from when it is one ingredient of a recipe. */
    private record PlannedItem(MealItemDraft draft, UUID recipeId, BigDecimal recipeServings) {
    }

    private record MealItemDraft(
            UUID customFoodId,
            UUID foodCatalogItemId,
            UUID foodServingId,
            String externalFoodRef,
            String barcode,
            String foodName,
            String brandName,
            BigDecimal quantity,
            String unit,
            BigDecimal servingSize,
            String servingUnit,
            String snapshotServingLabel,
            Integer calories,
            BigDecimal proteinGrams,
            BigDecimal carbsGrams,
            BigDecimal fatGrams,
            BigDecimal fiberGrams,
            BigDecimal sugarGrams,
            BigDecimal sodiumMg,
            List<NutrientDetailInput> nutrientDetails,
            BigDecimal gramsConsumed,
            BigDecimal saturatedFatGrams,
            Integer waterMl) {
    }
}
