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
import com.fitsync.nutrition.dto.RecipeIngredientInput;
import com.fitsync.nutrition.dto.RecipeIngredientView;
import com.fitsync.nutrition.dto.RecipeView;
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
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NutritionServiceImpl implements NutritionService {

    private static final String SOURCE = "nutrition-service";
    private static final String NUTRITION_EVENTS_TOPIC = "nutrition-events";
    private static final int FOOD_SEARCH_MAX_SIZE = 50;
    private static final int EXTERNAL_SEARCH_FALLBACK_THRESHOLD = 5;
    private static final int LOGGED_FOOD_DEFAULT_LIMIT = 20;
    private static final int LOGGED_FOOD_MAX_LIMIT = 50;
    private static final int LOGGED_FOOD_SCAN_LIMIT = 500;
    private static final long EXPORT_MAX_DAYS = 370;
    private static final String FOOD_GROUP_OTHER = "OTHER";
    private static final Set<String> FOOD_GROUPS = Set.of(
            "VEG", "FRUIT", "GRAINS", "PROTEIN", "DAIRY", "NUTS", "LEGUMES", "DRINKS", "SWEETS", FOOD_GROUP_OTHER);
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
                                NutritionDiaryService nutritionDiaryService) {
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
    }

    @Override
    @Transactional
    public MealView createMeal(CurrentUserPrincipal principal, CreateMealRequest request) {
        MealLogEntity entity = new MealLogEntity();
        entity.setMealId(UUID.randomUUID());
        entity.setUserId(principal.userId());
        applyMeal(entity, request);
        MealLogEntity savedMeal = mealLogRepository.save(entity);
        List<MealItemEntity> savedItems = replaceMealItems(principal.userId(), savedMeal.getMealId(), request.items());
        publishMealLogged(savedMeal, savedItems);
        return toMealView(savedMeal, savedItems);
    }

    @Override
    @Transactional
    public MealView quickAddMeal(CurrentUserPrincipal principal, QuickAddMealRequest request) {
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
                blankToNull(request.timezone()),
                null,
                List.of(item));
        return createMeal(principal, mealRequest);
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
    public MealView updateMeal(CurrentUserPrincipal principal, UUID mealId, CreateMealRequest request) {
        MealLogEntity meal = getOwnedMeal(principal.userId(), mealId);
        applyMeal(meal, request);
        MealLogEntity savedMeal = mealLogRepository.save(meal);
        mealItemRepository.deleteByMealId(savedMeal.getMealId());
        List<MealItemEntity> savedItems = replaceMealItems(principal.userId(), savedMeal.getMealId(), request.items());
        publishMealUpdated(savedMeal, savedItems);
        return toMealView(savedMeal, savedItems);
    }

    @Override
    @Transactional
    public void deleteMeal(CurrentUserPrincipal principal, UUID mealId) {
        MealLogEntity meal = getOwnedMeal(principal.userId(), mealId);
        // The meal and its items stay in the table, marked as deleted, and every read leaves them out.
        meal.setDeletedAt(Instant.now(Clock.systemUTC()));
        mealLogRepository.save(meal);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealDeleted",
                1,
                Instant.now(Clock.systemUTC()),
                SOURCE,
                principal.userId(),
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
        return copyMealTo(principal.userId(), source, request.consumedAt(), request.mealType());
    }

    @Override
    @Transactional
    public List<MealView> copyDay(CurrentUserPrincipal principal, LocalDate sourceDate, CopyDayRequest request) {
        ZoneId zone = resolveZone(request.timezone());
        Instant from = sourceDate.atStartOfDay(zone).toInstant();
        Instant to = sourceDate.plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant();
        List<MealLogEntity> meals = mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
                        principal.userId(), from, to);
        List<MealView> copied = new ArrayList<>();
        for (MealLogEntity meal : meals) {
            LocalTime localTime = meal.getConsumedAt().atZone(zone).toLocalTime();
            Instant target = request.targetDate().atTime(localTime).atZone(zone).toInstant();
            copied.add(copyMealTo(principal.userId(), meal, target, null));
        }
        return copied;
    }

    /** Clones a meal and its item snapshots (+ nutrient details) to a new time, emitting MealLogged. */
    private MealView copyMealTo(UUID userId, MealLogEntity source, Instant targetConsumedAt, MealType mealTypeOverride) {
        MealLogEntity newMeal = new MealLogEntity();
        newMeal.setMealId(UUID.randomUUID());
        newMeal.setUserId(userId);
        newMeal.setMealType(mealTypeOverride != null ? mealTypeOverride : source.getMealType());
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
        return toMealView(savedMeal, newItems);
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
        List<FoodCatalogItemEntity> foods = foodPage.getContent();

        Map<UUID, String> brandNames = loadBrandNames(foods.stream()
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
        List<FoodSearchResultView> localResults = foods.stream()
                .map(food -> toFoodSearchResultView(food, brandNames, categoryNames, foodGroups,
                        profiles.get(food.getFoodId()), primaryBarcodes.get(food.getFoodId())))
                .toList();
        if (!shouldSearchExternalFoods(criteria, normalizedQuery, page, localResults.size())) {
            return new PageImpl<>(localResults, pageRequest, foodPage.getTotalElements());
        }
        int externalLimit = Math.min(size - localResults.size(), EXTERNAL_SEARCH_FALLBACK_THRESHOLD - localResults.size());
        if (externalLimit <= 0) {
            return new PageImpl<>(localResults, pageRequest, foodPage.getTotalElements());
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
            return new PageImpl<>(localResults, pageRequest, foodPage.getTotalElements());
        }
        if (externalResults.isEmpty()) {
            return new PageImpl<>(localResults, pageRequest, foodPage.getTotalElements());
        }
        List<FoodSearchResultView> combined = new ArrayList<>(localResults);
        combined.addAll(externalResults);
        return new PageImpl<>(combined, pageRequest, foodPage.getTotalElements() + externalResults.size());
    }

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
        applyRecipe(recipe, request);
        RecipeEntity saved = recipeRepository.save(recipe);
        List<RecipeIngredientEntity> ingredients = replaceRecipeIngredients(saved.getRecipeId(), request.ingredients());
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
        return toRecipeView(principal.userId(), saved, ingredients);
    }

    @Override
    @Transactional
    public void deleteRecipe(CurrentUserPrincipal principal, UUID recipeId) {
        RecipeEntity recipe = getOwnedRecipe(principal.userId(), recipeId);
        recipeIngredientRepository.deleteByRecipeId(recipe.getRecipeId());
        recipeRepository.delete(recipe);
    }

    private void applyRecipe(RecipeEntity recipe, CreateRecipeRequest request) {
        recipe.setName(request.name().trim());
        recipe.setServings(request.servings());
        recipe.setNote(blankToNull(request.note()));
    }

    private RecipeEntity getOwnedRecipe(UUID userId, UUID recipeId) {
        return recipeRepository.findByRecipeIdAndUserId(recipeId, userId)
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
            MealItemDraft draft = buildMealItemFromCatalog(userId, recipeIngredientInput(ingredient));
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

    private MealItemInput recipeIngredientInput(RecipeIngredientEntity ingredient) {
        return new MealItemInput(
                null,
                ingredient.getFoodCatalogItemId(),
                ingredient.getFoodServingId(),
                null, null, null, null,
                ingredient.getQuantity(),
                null, null, null, null,
                null, null, null, null, null, null, null,
                null);
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
        Map<String, String> categoryNames = loadCategoryNames(foods.stream()
                .map(FoodCatalogItemEntity::getCategoryCode)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, FoodNutrientProfileEntity> profiles = loadPrimaryProfiles(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId).toList(), today);
        Map<UUID, String> primaryBarcodes = loadPrimaryBarcodes(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId).toList(), today);
        Map<String, String> foodGroups = loadFoodGroups();
        return foods.stream()
                .map(food -> toFoodSearchResultView(food, brandNames, categoryNames, foodGroups,
                        profiles.get(food.getFoodId()), primaryBarcodes.get(food.getFoodId())))
                .toList();
    }

    @Override
    @Transactional
    public MealTemplateView createMealTemplate(CurrentUserPrincipal principal, CreateMealTemplateRequest request) {
        if (request.fromMealId() != null && !request.items().isEmpty()) {
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
            if (request.items().isEmpty()) {
                throw new BadRequestException("Provide fromMealId or a non-empty items list");
            }
            for (MealItemInput input : request.items()) {
                MealItemDraft draft = buildMealItemDraft(userId, input);
                items.add(mealTemplateItemRepository.save(templateItemFromDraft(templateId, draft, sortOrder++)));
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
    public void deleteMealTemplate(CurrentUserPrincipal principal, UUID templateId) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        mealTemplateItemRepository.deleteByTemplateId(template.getTemplateId());
        mealTemplateRepository.delete(template);
    }

    @Override
    @Transactional
    public MealView createMealFromTemplate(CurrentUserPrincipal principal, UUID templateId,
                                           MaterializeTemplateRequest request) {
        MealTemplateEntity template = getOwnedTemplate(principal.userId(), templateId);
        List<MealTemplateItemEntity> templateItems = mealTemplateItemRepository
                .findByTemplateIdOrderBySortOrderAsc(template.getTemplateId());
        if (templateItems.isEmpty()) {
            throw new BadRequestException("Template has no items");
        }
        List<MealItemInput> items = templateItems.stream()
                .map(this::mealItemInputFromTemplateItem)
                .toList();
        MealType mealType = request.mealType() != null ? request.mealType()
                : (template.getMealType() != null ? template.getMealType() : MealType.SNACK);
        CreateMealRequest mealRequest = new CreateMealRequest(
                mealType,
                template.getName(),
                MealSource.MANUAL,
                request.consumedAt(),
                blankToNull(request.timezone()),
                template.getNote(),
                items);
        return createMeal(principal, mealRequest);
    }

    private MealTemplateEntity getOwnedTemplate(UUID userId, UUID templateId) {
        return mealTemplateRepository.findByTemplateIdAndUserId(templateId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meal template not found"));
    }

    private MealItemInput mealItemInputFromTemplateItem(MealTemplateItemEntity ti) {
        return new MealItemInput(
                ti.getCustomFoodId(), ti.getFoodCatalogItemId(), ti.getFoodServingId(),
                ti.getExternalFoodRef(), ti.getBarcode(), ti.getFoodName(), ti.getBrandName(),
                ti.getQuantity(), ti.getUnit(), ti.getServingSize(), ti.getServingUnit(),
                ti.getSnapshotServingLabel(), ti.getCalories(), ti.getProteinGrams(), ti.getCarbsGrams(),
                ti.getFatGrams(), ti.getFiberGrams(), ti.getSugarGrams(), ti.getSodiumMg(), null);
    }

    private MealTemplateView toMealTemplateView(MealTemplateEntity template, List<MealTemplateItemEntity> items) {
        int totalCalories = 0;
        List<MealTemplateItemView> itemViews = new ArrayList<>();
        for (MealTemplateItemEntity ti : items) {
            totalCalories += defaultInteger(ti.getCalories());
            itemViews.add(new MealTemplateItemView(
                    ti.getTemplateItemId(), ti.getCustomFoodId(), ti.getFoodCatalogItemId(), ti.getFoodServingId(),
                    ti.getFoodName(), ti.getBrandName(), ti.getQuantity(), ti.getUnit(), ti.getSnapshotServingLabel(),
                    ti.getCalories(), ti.getProteinGrams(), ti.getCarbsGrams(), ti.getFatGrams(),
                    ti.getFiberGrams(), ti.getSugarGrams(), ti.getSodiumMg()));
        }
        return new MealTemplateView(template.getTemplateId(), template.getName(), template.getMealType(),
                template.getNote(), totalCalories, itemViews);
    }

    private MealTemplateItemEntity templateItemFromMealItem(UUID templateId, MealItemEntity src, int sortOrder) {
        MealTemplateItemEntity ti = new MealTemplateItemEntity();
        ti.setTemplateItemId(UUID.randomUUID());
        ti.setTemplateId(templateId);
        ti.setCustomFoodId(src.getCustomFoodId());
        ti.setFoodCatalogItemId(src.getFoodCatalogItemId());
        ti.setFoodServingId(src.getFoodServingId());
        ti.setExternalFoodRef(src.getExternalFoodRef());
        ti.setBarcode(src.getBarcode());
        ti.setFoodName(src.getFoodName());
        ti.setBrandName(src.getBrandName());
        ti.setQuantity(src.getQuantity());
        ti.setUnit(src.getUnit());
        ti.setServingSize(src.getServingSize());
        ti.setServingUnit(src.getServingUnit());
        ti.setSnapshotServingLabel(src.getSnapshotServingLabel());
        ti.setCalories(src.getCalories());
        ti.setProteinGrams(src.getProteinGrams());
        ti.setCarbsGrams(src.getCarbsGrams());
        ti.setFatGrams(src.getFatGrams());
        ti.setFiberGrams(src.getFiberGrams());
        ti.setSugarGrams(src.getSugarGrams());
        ti.setSodiumMg(src.getSodiumMg());
        ti.setSortOrder(sortOrder);
        return ti;
    }

    private MealTemplateItemEntity templateItemFromDraft(UUID templateId, MealItemDraft draft, int sortOrder) {
        MealTemplateItemEntity ti = new MealTemplateItemEntity();
        ti.setTemplateItemId(UUID.randomUUID());
        ti.setTemplateId(templateId);
        ti.setCustomFoodId(draft.customFoodId());
        ti.setFoodCatalogItemId(draft.foodCatalogItemId());
        ti.setFoodServingId(draft.foodServingId());
        ti.setExternalFoodRef(draft.externalFoodRef());
        ti.setBarcode(draft.barcode());
        ti.setFoodName(draft.foodName());
        ti.setBrandName(draft.brandName());
        ti.setQuantity(draft.quantity());
        ti.setUnit(draft.unit());
        ti.setServingSize(draft.servingSize());
        ti.setServingUnit(draft.servingUnit());
        ti.setSnapshotServingLabel(draft.snapshotServingLabel());
        ti.setCalories(draft.calories());
        ti.setProteinGrams(draft.proteinGrams());
        ti.setCarbsGrams(draft.carbsGrams());
        ti.setFatGrams(draft.fatGrams());
        ti.setFiberGrams(draft.fiberGrams());
        ti.setSugarGrams(draft.sugarGrams());
        ti.setSodiumMg(draft.sodiumMg());
        ti.setSortOrder(sortOrder);
        return ti;
    }

    @Override
    @Transactional
    public WaterLogView logWater(CurrentUserPrincipal principal, LogWaterRequest request) {
        WaterLogEntity entity = new WaterLogEntity();
        entity.setWaterId(UUID.randomUUID());
        entity.setUserId(principal.userId());
        entity.setAmountMl(request.amountMl());
        entity.setLoggedAt(request.loggedAt() == null ? Instant.now(Clock.systemUTC()) : request.loggedAt());
        entity.setTimezone(blankToNull(request.timezone()));
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
        return picked.stream()
                .map(e -> toLoggedFoodView(e.getValue().getItem(), counts.get(e.getKey()),
                        e.getValue().getConsumedAt(), foodGroups, countsTowardWater))
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
                                            Map<String, String> foodGroups, Map<UUID, Boolean> countsTowardWater) {
        return new LoggedFoodView(
                item.getCustomFoodId(),
                item.getFoodCatalogItemId(),
                item.getFoodServingId(),
                item.getExternalFoodRef(),
                item.getBarcode(),
                item.getFoodName(),
                item.getBrandName(),
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

    private void applyMeal(MealLogEntity entity, CreateMealRequest request) {
        entity.setMealType(request.mealType());
        entity.setName(request.name());
        entity.setSource(request.source());
        entity.setConsumedAt(request.consumedAt());
        entity.setTimezone(request.timezone());
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
        List<MealItemEntity> entities = new ArrayList<>();
        for (MealItemInput input : itemInputs) {
            MealItemDraft draft = buildMealItemDraft(userId, input);
            MealItemEntity entity = new MealItemEntity();
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

    private MealView toMealView(MealLogEntity meal, List<MealItemEntity> items) {
        return toMealView(meal, items, loadFoodGroups());
    }

    private MealView toMealView(MealLogEntity meal, List<MealItemEntity> items, Map<String, String> foodGroups) {
        Map<UUID, List<NutrientDetailView>> nutrientDetailsByMealItemId = loadMealItemNutrientDetails(items);
        List<MealItemView> itemViews = items.stream()
                .sorted(Comparator.comparing(MealItemEntity::getDateTimeCreated))
                .map(item -> toMealItemView(item,
                        nutrientDetailsByMealItemId.getOrDefault(item.getMealItemId(), List.of()), foodGroups))
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

    /** A code with no category row, or an item without a category, falls into OTHER or stays unset. */
    private String foodGroupOf(String categoryCode, Map<String, String> foodGroups) {
        if (categoryCode == null) {
            return null;
        }
        return foodGroups.getOrDefault(categoryCode, FOOD_GROUP_OTHER);
    }

    private MealItemView toMealItemView(MealItemEntity entity, List<NutrientDetailView> nutrientDetails,
                                        Map<String, String> foodGroups) {
        return new MealItemView(
                entity.getMealItemId(),
                entity.getCustomFoodId(),
                entity.getFoodCatalogItemId(),
                entity.getFoodServingId(),
                entity.getExternalFoodRef(),
                entity.getBarcode(),
                entity.getFoodName(),
                entity.getBrandName(),
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
                entity.getSaturatedFatGrams());
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
                foodGroupOf(food.getCategoryCode(), loadFoodGroups()),
                food.isCountsTowardWater(),
                profile == null ? null : profile.getWaterG(),
                alcoholGrams);
    }

    private FoodSearchResultView toFoodSearchResultView(FoodCatalogItemEntity food,
                                                        Map<UUID, String> brandNames,
                                                        Map<String, String> categoryNames,
                                                        Map<String, String> foodGroups,
                                                        FoodNutrientProfileEntity profile,
                                                        String primaryBarcode) {
        String basisLabel = profile == null ? null : profile.getBasisQuantity().stripTrailingZeros().toPlainString() + " " + profile.getBasisUnit();
        return new FoodSearchResultView(
                food.getFoodId(),
                food.getDisplayName(),
                food.getBrandId() == null ? null : brandNames.get(food.getBrandId()),
                food.getCategoryCode(),
                food.getCategoryCode() == null ? null : categoryNames.get(food.getCategoryCode()),
                food.getScope(),
                food.getSource(),
                food.isVerified(),
                profile == null ? null : profile.getCalories(),
                basisLabel,
                primaryBarcode,
                foodGroupOf(food.getCategoryCode(), foodGroups),
                food.isCountsTowardWater());
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

    private Map<UUID, FoodNutrientProfileEntity> loadPrimaryProfiles(List<UUID> foodIds, LocalDate onDate) {
        Map<UUID, FoodNutrientProfileEntity> result = new HashMap<>();
        for (UUID foodId : foodIds.stream().distinct().toList()) {
            foodNutrientProfileRepository.findActiveProfilesByFoodId(foodId, onDate).stream()
                    .findFirst()
                    .ifPresent(profile -> result.put(foodId, profile));
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
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        FoodCatalogItemEntity food = foodCatalogItemRepository.findAccessibleFoodById(input.foodCatalogItemId(), userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
        FoodServingEntity serving = resolveServing(food, input.foodServingId(), today);
        FoodNutrientProfileEntity profile = foodNutrientProfileRepository.findActiveProfilesByFoodId(food.getFoodId(), today).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Food nutrient profile is unavailable"));
        BigDecimal multiplier = resolveMultiplier(input, profile, serving);
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
     * millilitre basis times the food's density when the density is known. Null when neither is possible.
     */
    private BigDecimal resolveGramsConsumed(FoodCatalogItemEntity food, FoodNutrientProfileEntity profile, BigDecimal multiplier) {
        BigDecimal basisAmount = profile.getBasisQuantity().multiply(multiplier);
        if (matchesUnit(profile.getBasisUnit(), "g")) {
            return basisAmount.setScale(2, RoundingMode.HALF_UP);
        }
        if (matchesUnit(profile.getBasisUnit(), "ml") && food.getDensityGramsPerMl() != null) {
            return basisAmount.multiply(food.getDensityGramsPerMl()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
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
        return foodServingRepository.findActiveByFoodId(food.getFoodId(), today).stream()
                .filter(FoodServingEntity::isDefaultServing)
                .findFirst()
                .orElseGet(() -> foodServingRepository.findActiveByFoodId(food.getFoodId(), today).stream().findFirst().orElse(null));
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
        if ("g".equals(profile.getBasisUnit())) {
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
                        foodGroupCalories(items))));
    }

    /**
     * Per-food-group energy for the meal (internal category code → summed kcal), over items that carry a
     * category snapshot (SNAP-3). Projected onto the MealLogged event so analytics gets food-group balance
     * without reading nutrition internals (EV-1). Unclassified items are omitted; null calories count as 0.
     */
    private static Map<String, Integer> foodGroupCalories(List<MealItemEntity> items) {
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
                        foodGroupCalories(items))));
    }

    private void validateDateRange(Instant from, Instant to) {
        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException("from and to must be provided together");
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
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

    private record Totals(
            int totalCalories,
            BigDecimal totalProteinGrams,
            BigDecimal totalCarbsGrams,
            BigDecimal totalFatGrams,
            BigDecimal totalFiberGrams,
            BigDecimal totalSugarGrams,
            BigDecimal totalSodiumMg) {
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
