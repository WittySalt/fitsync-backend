package com.fitsync.nutrition;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.MealTemplateEntity;
import com.fitsync.nutrition.messaging.AccountDeletionProjectionService;
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
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountDeletionProjectionServiceTests {

    @Test
    void deletesUserNutritionDataAndChildRows() {
        Fixtures fixtures = new Fixtures();
        UUID userId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID foodId = UUID.randomUUID();
        MealTemplateEntity template = new MealTemplateEntity();
        template.setTemplateId(templateId);
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        when(fixtures.mealTemplateRepository.findByUserIdOrderByNameAsc(userId)).thenReturn(List.of(template));
        when(fixtures.foodCatalogItemRepository.findByOwnerUserId(userId)).thenReturn(List.of(food));

        fixtures.service.deleteUserData(userId);

        // Meals go through native purges so that soft-deleted rows are removed as well.
        InOrder mealOrder = inOrder(fixtures.mealLogRepository);
        mealOrder.verify(fixtures.mealLogRepository).purgeItemDetailsByUserId(userId);
        mealOrder.verify(fixtures.mealLogRepository).purgeItemsByUserId(userId);
        mealOrder.verify(fixtures.mealLogRepository).purgeByUserId(userId);
        // Recipes go through native purges as well, so that soft-deleted recipes are removed with their steps.
        verify(fixtures.recipeFavoriteRepository).deleteByUserId(userId);
        verify(fixtures.recipeRepository).purgeStepsByUserId(userId);
        verify(fixtures.recipeRepository).purgeIngredientsByUserId(userId);
        verify(fixtures.recipeRepository).purgeByUserId(userId);
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
        private final RecipeRepository recipeRepository = mock(RecipeRepository.class);
        private final RecipeFavoriteRepository recipeFavoriteRepository = mock(RecipeFavoriteRepository.class);
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
                recipeRepository,
                recipeFavoriteRepository,
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
