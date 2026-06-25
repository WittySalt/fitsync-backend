package com.fitsync.nutrition;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.MealTemplateEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.messaging.AccountDeletionProjectionService;
import com.fitsync.nutrition.repository.CustomFoodRepository;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodFavoriteRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.MealTemplateItemRepository;
import com.fitsync.nutrition.repository.MealTemplateRepository;
import com.fitsync.nutrition.repository.NutritionPreferenceProjectionRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.WaterLogRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountDeletionProjectionServiceTests {

    @Test
    void deletesUserNutritionDataAndChildRows() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        UUID mealId = UUID.randomUUID();
        UUID mealItemId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        MealLogEntity meal = new MealLogEntity();
        meal.setMealId(mealId);
        MealItemEntity mealItem = new MealItemEntity();
        mealItem.setMealItemId(mealItemId);
        MealTemplateEntity template = new MealTemplateEntity();
        template.setTemplateId(templateId);
        RecipeEntity recipe = new RecipeEntity();
        recipe.setRecipeId(recipeId);
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        when(fixtures.mealLogRepository.findByUserIdOrderByConsumedAtDesc(userId)).thenReturn(List.of(meal));
        when(fixtures.mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(mealId)).thenReturn(List.of(mealItem));
        when(fixtures.recipeRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(recipe));
        when(fixtures.mealTemplateRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(template));
        when(fixtures.foodCatalogItemRepository.findByOwnerUserId(userId)).thenReturn(List.of(food));

        fixtures.service.deleteUserData(userId);

        InOrder mealOrder = inOrder(fixtures.mealItemNutrientDetailRepository,
                fixtures.mealItemRepository, fixtures.mealLogRepository);
        mealOrder.verify(fixtures.mealItemNutrientDetailRepository).deleteByMealItemId(mealItemId);
        mealOrder.verify(fixtures.mealItemRepository).deleteByMealId(mealId);
        mealOrder.verify(fixtures.mealLogRepository).deleteByUserId(userId);
        verify(fixtures.recipeIngredientRepository).deleteByRecipeId(recipeId);
        verify(fixtures.recipeRepository).deleteAll(List.of(recipe));
        verify(fixtures.mealTemplateItemRepository).deleteByTemplateId(templateId);
        verify(fixtures.mealTemplateRepository).deleteAll(List.of(template));
        verify(fixtures.foodFavoriteRepository).deleteByFoodIdIn(List.of(foodId));
        verify(fixtures.foodBarcodeRepository).deleteByFoodIdIn(List.of(foodId));
        verify(fixtures.foodNutrientDetailRepository).deleteByFoodIdIn(List.of(foodId));
        verify(fixtures.foodNutrientProfileRepository).deleteByFoodIdIn(List.of(foodId));
        verify(fixtures.foodServingRepository).deleteByFoodIdIn(List.of(foodId));
        verify(fixtures.foodCatalogItemRepository).deleteAll(List.of(food));
        verify(fixtures.foodFavoriteRepository).deleteByUserId(userId);
        verify(fixtures.customFoodRepository).deleteByUserId(userId);
        verify(fixtures.waterLogRepository).deleteByUserId(userId);
        verify(fixtures.nutritionPreferenceProjectionRepository).deleteById(userId);
        verify(fixtures.nutritionTargetRepository).deleteById(userId);
    }

    private static final class Fixtures {
        private final MealLogRepository mealLogRepository = mock(MealLogRepository.class);
        private final MealItemRepository mealItemRepository = mock(MealItemRepository.class);
        private final MealItemNutrientDetailRepository mealItemNutrientDetailRepository =
                mock(MealItemNutrientDetailRepository.class);
        private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
        private final RecipeIngredientRepository recipeIngredientRepository = mock(RecipeIngredientRepository.class);
        private final MealTemplateRepository mealTemplateRepository = mock(MealTemplateRepository.class);
        private final MealTemplateItemRepository mealTemplateItemRepository = mock(MealTemplateItemRepository.class);
        private final FoodCatalogItemRepository foodCatalogItemRepository = mock(FoodCatalogItemRepository.class);
        private final FoodServingRepository foodServingRepository = mock(FoodServingRepository.class);
        private final FoodNutrientProfileRepository foodNutrientProfileRepository =
                mock(FoodNutrientProfileRepository.class);
        private final FoodNutrientDetailRepository foodNutrientDetailRepository =
                mock(FoodNutrientDetailRepository.class);
        private final FoodBarcodeRepository foodBarcodeRepository = mock(FoodBarcodeRepository.class);
        private final FoodFavoriteRepository foodFavoriteRepository = mock(FoodFavoriteRepository.class);
        private final CustomFoodRepository customFoodRepository = mock(CustomFoodRepository.class);
        private final WaterLogRepository waterLogRepository = mock(WaterLogRepository.class);
        private final NutritionPreferenceProjectionRepository nutritionPreferenceProjectionRepository =
                mock(NutritionPreferenceProjectionRepository.class);
        private final NutritionTargetRepository nutritionTargetRepository = mock(NutritionTargetRepository.class);

        private final AccountDeletionProjectionService service = new AccountDeletionProjectionService(
                mealLogRepository,
                mealItemRepository,
                mealItemNutrientDetailRepository,
                recipeRepository,
                recipeIngredientRepository,
                mealTemplateRepository,
                mealTemplateItemRepository,
                foodCatalogItemRepository,
                foodServingRepository,
                foodNutrientProfileRepository,
                foodNutrientDetailRepository,
                foodBarcodeRepository,
                foodFavoriteRepository,
                customFoodRepository,
                waterLogRepository,
                nutritionPreferenceProjectionRepository,
                nutritionTargetRepository);
    }
}
