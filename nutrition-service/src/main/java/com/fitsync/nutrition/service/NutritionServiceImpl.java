package com.fitsync.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.MealItemInput;
import com.fitsync.nutrition.dto.MealItemView;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.dto.NutrientDetailInput;
import com.fitsync.nutrition.dto.NutrientDetailView;
import com.fitsync.nutrition.entity.CustomFoodEntity;
import com.fitsync.nutrition.entity.FoodBarcodeEntity;
import com.fitsync.nutrition.entity.FoodBrandEntity;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealItemNutrientDetailEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.messaging.DomainEventPublisher;
import com.fitsync.nutrition.repository.CustomFoodRepository;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodBrandRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.web.error.BadRequestException;
import com.fitsync.web.error.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NutritionServiceImpl implements NutritionService {

    private static final String SOURCE = "nutrition-service";
    private static final String NUTRITION_EVENTS_TOPIC = "nutrition-events";
    private static final int FOOD_SEARCH_MAX_SIZE = 50;
    private static final int LOGGED_FOOD_DEFAULT_LIMIT = 20;
    private static final int LOGGED_FOOD_MAX_LIMIT = 50;
    private static final int LOGGED_FOOD_SCAN_LIMIT = 500;

    private final DomainEventPublisher domainEventPublisher;
    private final MealLogRepository mealLogRepository;
    private final MealItemRepository mealItemRepository;
    private final MealItemNutrientDetailRepository mealItemNutrientDetailRepository;
    private final CustomFoodRepository customFoodRepository;
    private final NutritionTargetRepository nutritionTargetRepository;
    private final FoodCatalogItemRepository foodCatalogItemRepository;
    private final FoodCategoryRepository foodCategoryRepository;
    private final FoodBrandRepository foodBrandRepository;
    private final FoodServingRepository foodServingRepository;
    private final FoodBarcodeRepository foodBarcodeRepository;
    private final FoodNutrientProfileRepository foodNutrientProfileRepository;
    private final FoodNutrientDetailRepository foodNutrientDetailRepository;

    public NutritionServiceImpl(DomainEventPublisher domainEventPublisher,
                                MealLogRepository mealLogRepository,
                                MealItemRepository mealItemRepository,
                                MealItemNutrientDetailRepository mealItemNutrientDetailRepository,
                                CustomFoodRepository customFoodRepository,
                                NutritionTargetRepository nutritionTargetRepository,
                                FoodCatalogItemRepository foodCatalogItemRepository,
                                FoodCategoryRepository foodCategoryRepository,
                                FoodBrandRepository foodBrandRepository,
                                FoodServingRepository foodServingRepository,
                                FoodBarcodeRepository foodBarcodeRepository,
                                FoodNutrientProfileRepository foodNutrientProfileRepository,
                                FoodNutrientDetailRepository foodNutrientDetailRepository) {
        this.domainEventPublisher = domainEventPublisher;
        this.mealLogRepository = mealLogRepository;
        this.mealItemRepository = mealItemRepository;
        this.mealItemNutrientDetailRepository = mealItemNutrientDetailRepository;
        this.customFoodRepository = customFoodRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
        this.foodCatalogItemRepository = foodCatalogItemRepository;
        this.foodCategoryRepository = foodCategoryRepository;
        this.foodBrandRepository = foodBrandRepository;
        this.foodServingRepository = foodServingRepository;
        this.foodBarcodeRepository = foodBarcodeRepository;
        this.foodNutrientProfileRepository = foodNutrientProfileRepository;
        this.foodNutrientDetailRepository = foodNutrientDetailRepository;
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
    @Transactional(readOnly = true)
    public List<MealView> listMeals(CurrentUserPrincipal principal, Instant from, Instant to, MealType mealType) {
        validateDateRange(from, to);
        List<MealLogEntity> meals = findMeals(principal.userId(), from, to, mealType);
        return meals.stream()
                .map(meal -> toMealView(meal, mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId())))
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
        mealItemRepository.deleteByMealId(meal.getMealId());
        mealLogRepository.delete(meal);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealDeleted",
                1,
                Instant.now(),
                SOURCE,
                principal.userId(),
                new MealDeletedPayload(meal.getMealId(), Instant.now())));
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
        ZoneId zoneId = resolveZone(timezone);
        Instant from = date.atStartOfDay(zoneId).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zoneId).minusNanos(1).toInstant();
        List<MealLogEntity> meals = mealLogRepository
                .findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(principal.userId(), from, to);

        int totalCalories = 0;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        for (MealLogEntity meal : meals) {
            Totals totals = calculateTotals(mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(meal.getMealId()));
            totalCalories += totals.totalCalories();
            totalProtein = totalProtein.add(totals.totalProteinGrams());
            totalCarbs = totalCarbs.add(totals.totalCarbsGrams());
            totalFat = totalFat.add(totals.totalFatGrams());
        }
        NutritionTargetEntity target = nutritionTargetRepository.findById(principal.userId()).orElse(null);
        Integer targetCalories = target == null ? null : target.getCalorieTarget();
        BigDecimal targetProtein = target == null ? null : target.getProteinTarget();
        BigDecimal targetCarbs = target == null ? null : target.getCarbTargetGrams();
        BigDecimal targetFat = target == null ? null : target.getFatTargetGrams();

        return new DailyNutritionSummaryView(
                date,
                zoneId.getId(),
                totalCalories,
                totalProtein,
                totalCarbs,
                totalFat,
                targetCalories,
                targetProtein,
                targetCarbs,
                targetFat,
                targetCalories == null ? null : targetCalories - totalCalories,
                targetProtein == null ? null : targetProtein.subtract(totalProtein),
                targetCarbs == null ? null : targetCarbs.subtract(totalCarbs),
                targetFat == null ? null : targetFat.subtract(totalFat),
                meals.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodSearchResultView> searchFoods(CurrentUserPrincipal principal, FoodSearchCriteria criteria) {
        LocalDate today = LocalDate.now();
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

        int size = Math.min(Math.max(criteria.size(), 1), FOOD_SEARCH_MAX_SIZE);
        int page = Math.max(criteria.page(), 0);
        List<FoodCatalogItemEntity> foods = foodCatalogItemRepository.searchAccessibleFoods(
                principal.userId(), normalizedQuery, today, categoryCode, pathPrefix,
                criteria.brandId(), criteria.verified(), criteria.scope(), PageRequest.of(page, size));

        Map<UUID, String> brandNames = loadBrandNames(foods.stream()
                .map(FoodCatalogItemEntity::getBrandId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<UUID, FoodNutrientProfileEntity> profiles = loadPrimaryProfiles(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .toList(), today);
        Map<UUID, String> primaryBarcodes = loadPrimaryBarcodes(foods.stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .toList(), today);
        return foods.stream()
                .map(food -> toFoodSearchResultView(food, brandNames, profiles.get(food.getFoodId()),
                        primaryBarcodes.get(food.getFoodId())))
                .toList();
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
    @Transactional(readOnly = true)
    public List<LoggedFoodView> listRecentFoods(CurrentUserPrincipal principal, int limit) {
        return aggregateLoggedFoods(principal.userId(), false, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoggedFoodView> listFrequentFoods(CurrentUserPrincipal principal, int limit) {
        return aggregateLoggedFoods(principal.userId(), true, limit);
    }

    /**
     * Collapses the caller's recent meal items by food identity. The most-recently-consumed
     * occurrence becomes the representative (carrying the last-used serving snapshot). When
     * {@code byFrequency} is true the result is ordered by occurrence count (recency breaks ties);
     * otherwise it preserves recency order.
     */
    private List<LoggedFoodView> aggregateLoggedFoods(UUID userId, boolean byFrequency, int limit) {
        int cappedLimit = Math.min(Math.max(limit, 1), LOGGED_FOOD_MAX_LIMIT);
        List<MealItemEntity> items = mealItemRepository.findRecentItemsByUserId(
                userId, PageRequest.of(0, LOGGED_FOOD_SCAN_LIMIT));

        LinkedHashMap<String, MealItemEntity> representative = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (MealItemEntity item : items) {
            String key = loggedFoodKey(item);
            representative.putIfAbsent(key, item);
            counts.merge(key, 1L, Long::sum);
        }

        var entries = representative.entrySet().stream();
        if (byFrequency) {
            entries = entries.sorted(Comparator.comparingLong(
                    (Map.Entry<String, MealItemEntity> e) -> counts.get(e.getKey())).reversed());
        }
        return entries
                .limit(cappedLimit)
                .map(e -> toLoggedFoodView(e.getValue(), counts.get(e.getKey())))
                .toList();
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

    private LoggedFoodView toLoggedFoodView(MealItemEntity item, long timesLogged) {
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
                timesLogged);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodDetailView getFood(CurrentUserPrincipal principal, UUID foodId) {
        return toFoodDetailView(getAccessibleFood(principal.userId(), foodId, LocalDate.now()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodServingView> listFoodServings(CurrentUserPrincipal principal, UUID foodId) {
        FoodCatalogItemEntity food = getAccessibleFood(principal.userId(), foodId, LocalDate.now());
        return foodServingRepository.findActiveByFoodId(food.getFoodId(), LocalDate.now()).stream()
                .map(this::toFoodServingView)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodDetailView getFoodByBarcode(CurrentUserPrincipal principal, String barcode) {
        FoodBarcodeEntity barcodeEntity = foodBarcodeRepository.findActiveByBarcode(barcode, LocalDate.now())
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
        return toFoodDetailView(getAccessibleFood(principal.userId(), barcodeEntity.getFoodId(), LocalDate.now()));
    }

    @Override
    @Transactional
    public FoodDetailView cloneFood(CurrentUserPrincipal principal, UUID foodId) {
        LocalDate today = LocalDate.now();
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
            MealItemEntity savedItem = mealItemRepository.save(entity);
            replaceMealItemNutrientDetails(savedItem.getMealItemId(), draft.nutrientDetails());
            entities.add(savedItem);
        }
        return entities;
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
        Map<UUID, List<NutrientDetailView>> nutrientDetailsByMealItemId = loadMealItemNutrientDetails(items);
        List<MealItemView> itemViews = items.stream()
                .sorted(Comparator.comparing(MealItemEntity::getDateTimeCreated))
                .map(item -> toMealItemView(item, nutrientDetailsByMealItemId.getOrDefault(item.getMealItemId(), List.of())))
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

    private MealItemView toMealItemView(MealItemEntity entity, List<NutrientDetailView> nutrientDetails) {
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
                nutrientDetails);
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
        for (MealItemEntity item : items) {
            calories += defaultInteger(item.getCalories());
            protein = protein.add(defaultDecimal(item.getProteinGrams()));
            carbs = carbs.add(defaultDecimal(item.getCarbsGrams()));
            fat = fat.add(defaultDecimal(item.getFatGrams()));
        }
        return new Totals(calories, protein, carbs, fat);
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
        LocalDate today = LocalDate.now();
        FoodNutrientProfileEntity profile = foodNutrientProfileRepository.findActiveProfilesByFoodId(food.getFoodId(), today)
                .stream()
                .findFirst()
                .orElse(null);
        Map<UUID, String> brandNames = loadBrandNames(List.of(food.getBrandId()));
        String primaryBarcode = loadPrimaryBarcodes(List.of(food.getFoodId()), today).get(food.getFoodId());
        List<FoodServingView> servings = foodServingRepository.findActiveByFoodId(food.getFoodId(), today).stream()
                .map(this::toFoodServingView)
                .toList();
        List<NutrientDetailView> nutrientDetails = foodNutrientDetailRepository.findActiveByFoodId(food.getFoodId(), today).stream()
                .map(this::toNutrientDetailView)
                .toList();

        return new FoodDetailView(
                food.getFoodId(),
                food.getDisplayName(),
                food.getNormalizedName(),
                brandNames.get(food.getBrandId()),
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
                servings,
                nutrientDetails);
    }

    private FoodSearchResultView toFoodSearchResultView(FoodCatalogItemEntity food,
                                                        Map<UUID, String> brandNames,
                                                        FoodNutrientProfileEntity profile,
                                                        String primaryBarcode) {
        String basisLabel = profile == null ? null : profile.getBasisQuantity().stripTrailingZeros().toPlainString() + " " + profile.getBasisUnit();
        return new FoodSearchResultView(
                food.getFoodId(),
                food.getDisplayName(),
                food.getBrandId() == null ? null : brandNames.get(food.getBrandId()),
                food.getCategoryCode(),
                food.getScope(),
                food.getSource(),
                food.isVerified(),
                profile == null ? null : profile.getCalories(),
                basisLabel,
                primaryBarcode);
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
                input.nutrientDetails());
    }

    private MealItemDraft buildMealItemFromCatalog(UUID userId, MealItemInput input) {
        LocalDate today = LocalDate.now();
        FoodCatalogItemEntity food = foodCatalogItemRepository.findAccessibleFoodById(input.foodCatalogItemId(), userId, today)
                .orElseThrow(() -> new ResourceNotFoundException("Food not found"));
        FoodServingEntity serving = resolveServing(food, input.foodServingId(), today);
        FoodNutrientProfileEntity profile = foodNutrientProfileRepository.findActiveProfilesByFoodId(food.getFoodId(), today).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Food nutrient profile is unavailable"));
        BigDecimal multiplier = resolveMultiplier(input, profile, serving);
        Map<UUID, String> brandNames = loadBrandNames(List.of(food.getBrandId()));
        List<NutrientDetailInput> nutrientDetails = buildScaledNutrientDetails(food.getFoodId(), today, multiplier);
        return new MealItemDraft(
                input.customFoodId(),
                food.getFoodId(),
                serving == null ? null : serving.getServingId(),
                input.externalFoodRef(),
                input.barcode(),
                food.getDisplayName(),
                brandNames.get(food.getBrandId()),
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
                nutrientDetails);
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
                Instant.now(),
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
                        meal.getSource().name())));
    }

    private void publishMealUpdated(MealLogEntity meal, List<MealItemEntity> items) {
        Totals totals = calculateTotals(items);
        domainEventPublisher.publish(NUTRITION_EVENTS_TOPIC, "MealLog", meal.getMealId().toString(), new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "MealUpdated",
                1,
                Instant.now(),
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
                        meal.getSource().name())));
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
            BigDecimal totalFatGrams) {
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
            List<NutrientDetailInput> nutrientDetails) {
    }
}
