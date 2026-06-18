package com.fitsync.nutrition.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;

public interface NutritionService {

    MealView createMeal(CurrentUserPrincipal principal, CreateMealRequest request);

    List<MealView> listMeals(CurrentUserPrincipal principal, Instant from, Instant to, MealType mealType);

    MealView getMeal(CurrentUserPrincipal principal, UUID mealId);

    MealView updateMeal(CurrentUserPrincipal principal, UUID mealId, CreateMealRequest request);

    void deleteMeal(CurrentUserPrincipal principal, UUID mealId);

    MealView getLatestMeal(CurrentUserPrincipal principal);

    CustomFoodView createCustomFood(CurrentUserPrincipal principal, CreateCustomFoodRequest request);

    List<CustomFoodView> listCustomFoods(CurrentUserPrincipal principal);

    CustomFoodView getCustomFood(CurrentUserPrincipal principal, UUID customFoodId);

    CustomFoodView updateCustomFood(CurrentUserPrincipal principal, UUID customFoodId, CreateCustomFoodRequest request);

    void deleteCustomFood(CurrentUserPrincipal principal, UUID customFoodId);

    DailyNutritionSummaryView getDailySummary(CurrentUserPrincipal principal, LocalDate date, String timezone);

    List<FoodSearchResultView> searchFoods(CurrentUserPrincipal principal, String query,
                                           UUID categoryId, boolean includeSubcategories);

    List<FoodCategoryView> listCategories(CurrentUserPrincipal principal);

    List<LoggedFoodView> listRecentFoods(CurrentUserPrincipal principal, int limit);

    List<LoggedFoodView> listFrequentFoods(CurrentUserPrincipal principal, int limit);

    FoodDetailView getFood(CurrentUserPrincipal principal, UUID foodId);

    List<FoodServingView> listFoodServings(CurrentUserPrincipal principal, UUID foodId);

    FoodDetailView getFoodByBarcode(CurrentUserPrincipal principal, String barcode);

    FoodDetailView cloneFood(CurrentUserPrincipal principal, UUID foodId);
}
