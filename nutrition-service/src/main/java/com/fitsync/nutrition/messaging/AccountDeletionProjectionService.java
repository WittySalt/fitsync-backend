package com.fitsync.nutrition.messaging;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.MealTemplateEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.repository.CustomFoodRepository;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodFavoriteRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.MealTemplateItemRepository;
import com.fitsync.nutrition.repository.MealTemplateRepository;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.repository.RecipeFavoriteRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.WaterLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionProjectionService {

    private final MealLogRepository mealLogRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeFavoriteRepository recipeFavoriteRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final MealTemplateItemRepository mealTemplateItemRepository;
    private final FoodCatalogItemRepository foodCatalogItemRepository;
    private final FoodServingRepository foodServingRepository;
    private final FoodNutrientProfileRepository foodNutrientProfileRepository;
    private final FoodNutrientDetailRepository foodNutrientDetailRepository;
    private final FoodBarcodeRepository foodBarcodeRepository;
    private final FoodFavoriteRepository foodFavoriteRepository;
    private final CustomFoodRepository customFoodRepository;
    private final WaterLogRepository waterLogRepository;
    private final NutritionPreferenceProjectionRepository nutritionPreferenceProjectionRepository;
    private final NutritionTargetRepository nutritionTargetRepository;

    public AccountDeletionProjectionService(MealLogRepository mealLogRepository,
                                            RecipeRepository recipeRepository,
                                            RecipeFavoriteRepository recipeFavoriteRepository,
                                            MealTemplateRepository mealTemplateRepository,
                                            MealTemplateItemRepository mealTemplateItemRepository,
                                            FoodCatalogItemRepository foodCatalogItemRepository,
                                            FoodServingRepository foodServingRepository,
                                            FoodNutrientProfileRepository foodNutrientProfileRepository,
                                            FoodNutrientDetailRepository foodNutrientDetailRepository,
                                            FoodBarcodeRepository foodBarcodeRepository,
                                            FoodFavoriteRepository foodFavoriteRepository,
                                            CustomFoodRepository customFoodRepository,
                                            WaterLogRepository waterLogRepository,
                                            NutritionPreferenceProjectionRepository nutritionPreferenceProjectionRepository,
                                            NutritionTargetRepository nutritionTargetRepository) {
        this.mealLogRepository = mealLogRepository;
        this.recipeRepository = recipeRepository;
        this.recipeFavoriteRepository = recipeFavoriteRepository;
        this.mealTemplateRepository = mealTemplateRepository;
        this.mealTemplateItemRepository = mealTemplateItemRepository;
        this.foodCatalogItemRepository = foodCatalogItemRepository;
        this.foodServingRepository = foodServingRepository;
        this.foodNutrientProfileRepository = foodNutrientProfileRepository;
        this.foodNutrientDetailRepository = foodNutrientDetailRepository;
        this.foodBarcodeRepository = foodBarcodeRepository;
        this.foodFavoriteRepository = foodFavoriteRepository;
        this.customFoodRepository = customFoodRepository;
        this.waterLogRepository = waterLogRepository;
        this.nutritionPreferenceProjectionRepository = nutritionPreferenceProjectionRepository;
        this.nutritionTargetRepository = nutritionTargetRepository;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        if (userId == null) {
            return;
        }
        deleteMeals(userId);
        deleteRecipes(userId);
        deleteMealTemplates(userId);
        deleteOwnedCatalogFoods(userId);
        foodFavoriteRepository.deleteByUserId(userId);
        customFoodRepository.deleteByUserId(userId);
        waterLogRepository.deleteByUserId(userId);
        nutritionPreferenceProjectionRepository.deleteById(userId);
        nutritionTargetRepository.deleteById(userId);
    }

    /** Removes every meal row of the user, deleted ones included, together with its items and nutrient details. */
    private void deleteMeals(UUID userId) {
        mealLogRepository.purgeItemDetailsByUserId(userId);
        mealLogRepository.purgeItemsByUserId(userId);
        mealLogRepository.purgeByUserId(userId);
    }

    /** Removes every recipe row of the user, deleted ones included, together with its steps and ingredients, and the recipes the user saved. */
    private void deleteRecipes(UUID userId) {
        recipeFavoriteRepository.deleteByUserId(userId);
        recipeRepository.purgeCooksByUserId(userId);
        recipeRepository.purgeStepsByUserId(userId);
        recipeRepository.purgeIngredientsByUserId(userId);
        recipeRepository.purgeByUserId(userId);
    }

    private void deleteMealTemplates(UUID userId) {
        List<MealTemplateEntity> templates = mealTemplateRepository.findByUserIdOrderByNameAsc(userId);
        for (MealTemplateEntity template : templates) {
            mealTemplateItemRepository.deleteByTemplateId(template.getTemplateId());
        }
        mealTemplateRepository.deleteAll(templates);
    }

    private void deleteOwnedCatalogFoods(UUID userId) {
        List<FoodCatalogItemEntity> foods = foodCatalogItemRepository.findByOwnerUserId(userId);
        List<UUID> foodIds = foods.stream()
                .map(FoodCatalogItemEntity::getFoodId)
                .toList();
        if (foodIds.isEmpty()) {
            return;
        }
        foodFavoriteRepository.deleteByFoodIdIn(foodIds);
        foodBarcodeRepository.deleteByFoodIdIn(foodIds);
        foodNutrientDetailRepository.deleteByFoodIdIn(foodIds);
        foodNutrientProfileRepository.deleteByFoodIdIn(foodIds);
        foodServingRepository.deleteByFoodIdIn(foodIds);
        foodCatalogItemRepository.deleteAll(foods);
    }
}
