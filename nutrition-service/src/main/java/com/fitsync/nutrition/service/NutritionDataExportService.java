package com.fitsync.nutrition.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.NutritionDataExport;
import com.fitsync.nutrition.dto.RecipeCookView;
import com.fitsync.nutrition.dto.RecipeDetailView;
import com.fitsync.nutrition.dto.RecipeFavoriteExportView;
import com.fitsync.nutrition.dto.WaterLogView;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.RecipeCookEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import com.fitsync.nutrition.entity.WaterLogEntity;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.RecipeCookRepository;
import com.fitsync.nutrition.repository.RecipeFavoriteRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.WaterLogRepository;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the personal data export of nutrition-service for the signed-in person. It covers the same per-user
 * data the account purge removes (see AccountDeletionProjectionService), apart from the projections of data
 * owned by other services, and it reuses the views of the list endpoints so that the export reads like the API
 * does. Every read is scoped to the caller's own user id, and rows the person deleted in the app are left out.
 *
 * <p>The collaborators are reached through ObjectProvider so that this singleton does not keep references to
 * beans that SpotBugs takes for mutable state.
 */
@Service
public class NutritionDataExportService {

    static final String SERVICE_NAME = "nutrition-service";

    private final ObjectProvider<NutritionService> nutritionService;
    private final ObjectProvider<RecipeLibraryService> recipeLibraryService;
    private final ObjectProvider<RecipeRepository> recipeRepository;
    private final ObjectProvider<RecipeCookRepository> recipeCookRepository;
    private final ObjectProvider<RecipeFavoriteRepository> recipeFavoriteRepository;
    private final ObjectProvider<FoodCatalogItemRepository> foodCatalogItemRepository;
    private final ObjectProvider<WaterLogRepository> waterLogRepository;
    private final ObjectProvider<Clock> clock;

    public NutritionDataExportService(ObjectProvider<NutritionService> nutritionService,
                                      ObjectProvider<RecipeLibraryService> recipeLibraryService,
                                      ObjectProvider<RecipeRepository> recipeRepository,
                                      ObjectProvider<RecipeCookRepository> recipeCookRepository,
                                      ObjectProvider<RecipeFavoriteRepository> recipeFavoriteRepository,
                                      ObjectProvider<FoodCatalogItemRepository> foodCatalogItemRepository,
                                      ObjectProvider<WaterLogRepository> waterLogRepository,
                                      ObjectProvider<Clock> clock) {
        this.nutritionService = nutritionService;
        this.recipeLibraryService = recipeLibraryService;
        this.recipeRepository = recipeRepository;
        this.recipeCookRepository = recipeCookRepository;
        this.recipeFavoriteRepository = recipeFavoriteRepository;
        this.foodCatalogItemRepository = foodCatalogItemRepository;
        this.waterLogRepository = waterLogRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NutritionDataExport export(CurrentUserPrincipal principal) {
        UUID userId = principal.userId();
        NutritionService nutrition = nutritionService.getObject();
        return new NutritionDataExport(
                SERVICE_NAME,
                Instant.now(clock.getIfAvailable(Clock::systemUTC)),
                nutrition.listMeals(principal, null, null, null),
                recipes(principal),
                recipeCooks(userId),
                nutrition.listMealTemplates(principal),
                nutrition.listFavorites(principal),
                recipeFavorites(userId),
                nutrition.listCustomFoods(principal),
                ownedFoods(principal),
                waterLogs(userId));
    }

    /** The recipes the person wrote or copied, each with its ingredients and steps. */
    private List<RecipeDetailView> recipes(CurrentUserPrincipal principal) {
        RecipeLibraryService library = recipeLibraryService.getObject();
        return recipeRepository.getObject().findByUserIdOrderByNameAsc(principal.userId()).stream()
                .map(RecipeEntity::getRecipeId)
                .map(recipeId -> library.detail(principal, recipeId))
                .toList();
    }

    /** Every cook the person recorded, on their own recipes and on library recipes alike. */
    private List<RecipeCookView> recipeCooks(UUID userId) {
        return recipeCookRepository.getObject().findByUserIdAndDeletedAtIsNullOrderByFinishedAtAsc(userId).stream()
                .map(NutritionDataExportService::toCookView)
                .toList();
    }

    private List<RecipeFavoriteExportView> recipeFavorites(UUID userId) {
        return recipeFavoriteRepository.getObject().findByUserIdOrderBySavedAtAsc(userId).stream()
                .map(NutritionDataExportService::toRecipeFavoriteView)
                .toList();
    }

    /**
     * The catalogue foods the person owns, such as clones. Each food is looked up first so that one which is no
     * longer active is skipped quietly. A not-found error inside this read-only transaction would otherwise mark
     * it for rollback and fail the whole export.
     */
    private List<FoodDetailView> ownedFoods(CurrentUserPrincipal principal) {
        FoodCatalogItemRepository foods = foodCatalogItemRepository.getObject();
        NutritionService nutrition = nutritionService.getObject();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return foods.findByOwnerUserId(principal.userId()).stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .filter(foodId -> foods.findAccessibleFoodById(foodId, principal.userId(), today).isPresent())
                .map(foodId -> nutrition.getFood(principal, foodId))
                .toList();
    }

    private List<WaterLogView> waterLogs(UUID userId) {
        return waterLogRepository.getObject().findByUserIdOrderByLoggedAtAsc(userId).stream()
                .map(NutritionDataExportService::toWaterLogView)
                .toList();
    }

    private static RecipeCookView toCookView(RecipeCookEntity cook) {
        return new RecipeCookView(cook.getCookId(), cook.getRecipeId(), cook.getServings(), cook.getStartedAt(),
                cook.getFinishedAt(), cook.getServingsLeft());
    }

    private static RecipeFavoriteExportView toRecipeFavoriteView(RecipeFavoriteEntity favorite) {
        return new RecipeFavoriteExportView(favorite.getRecipeId(), favorite.getSavedAt());
    }

    private static WaterLogView toWaterLogView(WaterLogEntity entity) {
        return new WaterLogView(entity.getWaterId(), entity.getAmountMl(), entity.getLoggedAt(), entity.getTimezone());
    }
}
