package com.fitsync.nutrition.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.CopyDayRequest;
import com.fitsync.nutrition.dto.CopyMealRequest;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CreateMealTemplateRequest;
import com.fitsync.nutrition.dto.CreateRecipeRequest;
import com.fitsync.nutrition.dto.MaterializeTemplateRequest;
import com.fitsync.nutrition.dto.MealTemplateView;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.dto.LogWaterRequest;
import com.fitsync.nutrition.dto.QuickAddMealRequest;
import com.fitsync.nutrition.dto.RecipeView;
import com.fitsync.nutrition.dto.WaterDaySummaryView;
import com.fitsync.nutrition.dto.WaterLogView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import org.springframework.data.domain.Page;

public interface NutritionService {

    MealView createMeal(CurrentUserPrincipal principal, CreateMealRequest request);

    MealView quickAddMeal(CurrentUserPrincipal principal, QuickAddMealRequest request);

    List<MealView> listMeals(CurrentUserPrincipal principal, Instant from, Instant to, MealType mealType);

    MealView getMeal(CurrentUserPrincipal principal, UUID mealId);

    MealView updateMeal(CurrentUserPrincipal principal, UUID mealId, CreateMealRequest request);

    void deleteMeal(CurrentUserPrincipal principal, UUID mealId);

    MealView getLatestMeal(CurrentUserPrincipal principal);

    MealView copyMeal(CurrentUserPrincipal principal, UUID mealId, CopyMealRequest request);

    List<MealView> copyDay(CurrentUserPrincipal principal, LocalDate sourceDate, CopyDayRequest request);

    CustomFoodView createCustomFood(CurrentUserPrincipal principal, CreateCustomFoodRequest request);

    List<CustomFoodView> listCustomFoods(CurrentUserPrincipal principal);

    CustomFoodView getCustomFood(CurrentUserPrincipal principal, UUID customFoodId);

    CustomFoodView updateCustomFood(CurrentUserPrincipal principal, UUID customFoodId, CreateCustomFoodRequest request);

    void deleteCustomFood(CurrentUserPrincipal principal, UUID customFoodId);

    DailyNutritionSummaryView getDailySummary(CurrentUserPrincipal principal, LocalDate date, String timezone);

    Page<FoodSearchResultView> searchFoods(CurrentUserPrincipal principal, FoodSearchCriteria criteria);

    List<FoodCategoryView> listCategories(CurrentUserPrincipal principal);

    RecipeView createRecipe(CurrentUserPrincipal principal, CreateRecipeRequest request);

    List<RecipeView> listRecipes(CurrentUserPrincipal principal);

    RecipeView getRecipe(CurrentUserPrincipal principal, UUID recipeId);

    RecipeView updateRecipe(CurrentUserPrincipal principal, UUID recipeId, CreateRecipeRequest request);

    void deleteRecipe(CurrentUserPrincipal principal, UUID recipeId);

    List<LoggedFoodView> listRecentFoods(CurrentUserPrincipal principal, int limit, String foodGroup);

    List<LoggedFoodView> listFrequentFoods(CurrentUserPrincipal principal, int limit, String foodGroup);

    FoodDetailView getFood(CurrentUserPrincipal principal, UUID foodId);

    List<FoodServingView> listFoodServings(CurrentUserPrincipal principal, UUID foodId);

    FoodDetailView getFoodByBarcode(CurrentUserPrincipal principal, String barcode);

    FoodDetailView importFoodByBarcode(CurrentUserPrincipal principal, String barcode);

    FoodDetailView cloneFood(CurrentUserPrincipal principal, UUID foodId);

    void addFavorite(CurrentUserPrincipal principal, UUID foodId);

    void removeFavorite(CurrentUserPrincipal principal, UUID foodId);

    List<FoodSearchResultView> listFavorites(CurrentUserPrincipal principal);

    MealTemplateView createMealTemplate(CurrentUserPrincipal principal, CreateMealTemplateRequest request);

    List<MealTemplateView> listMealTemplates(CurrentUserPrincipal principal);

    MealTemplateView getMealTemplate(CurrentUserPrincipal principal, UUID templateId);

    void deleteMealTemplate(CurrentUserPrincipal principal, UUID templateId);

    MealView createMealFromTemplate(CurrentUserPrincipal principal, UUID templateId, MaterializeTemplateRequest request);

    WaterLogView logWater(CurrentUserPrincipal principal, LogWaterRequest request);

    WaterDaySummaryView getWaterDay(CurrentUserPrincipal principal, LocalDate date, String timezone);

    void deleteWater(CurrentUserPrincipal principal, UUID waterId);

    byte[] exportFoodDiary(CurrentUserPrincipal principal, Instant from, Instant to, String format);
}
