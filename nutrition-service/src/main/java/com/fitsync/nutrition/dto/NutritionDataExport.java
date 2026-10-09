package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything nutrition-service holds that the signed-in person entered or owns, in one object, for the
 * personal data export. Meals, recipes, cooks and the other lists cover the whole history and are not paged.
 * Rows the person deleted in the app are left out, as they are everywhere else in the API.
 */
public record NutritionDataExport(
        String service,
        Instant exportedAt,
        List<MealView> meals,
        List<RecipeDetailView> recipes,
        List<RecipeCookView> recipeCooks,
        List<MealTemplateView> mealTemplates,
        List<FoodSearchResultView> foodFavorites,
        List<RecipeFavoriteExportView> recipeFavorites,
        List<CustomFoodView> customFoods,
        List<FoodDetailView> ownedFoods,
        List<WaterLogView> waterLogs) {

    public NutritionDataExport {
        meals = copy(meals);
        recipes = copy(recipes);
        recipeCooks = copy(recipeCooks);
        mealTemplates = copy(mealTemplates);
        foodFavorites = copy(foodFavorites);
        recipeFavorites = copy(recipeFavorites);
        customFoods = copy(customFoods);
        ownedFoods = copy(ownedFoods);
        waterLogs = copy(waterLogs);
    }

    @Override
    public List<MealView> meals() {
        return List.copyOf(meals);
    }

    @Override
    public List<RecipeDetailView> recipes() {
        return List.copyOf(recipes);
    }

    @Override
    public List<RecipeCookView> recipeCooks() {
        return List.copyOf(recipeCooks);
    }

    @Override
    public List<MealTemplateView> mealTemplates() {
        return List.copyOf(mealTemplates);
    }

    @Override
    public List<FoodSearchResultView> foodFavorites() {
        return List.copyOf(foodFavorites);
    }

    @Override
    public List<RecipeFavoriteExportView> recipeFavorites() {
        return List.copyOf(recipeFavorites);
    }

    @Override
    public List<CustomFoodView> customFoods() {
        return List.copyOf(customFoods);
    }

    @Override
    public List<FoodDetailView> ownedFoods() {
        return List.copyOf(ownedFoods);
    }

    @Override
    public List<WaterLogView> waterLogs() {
        return List.copyOf(waterLogs);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
