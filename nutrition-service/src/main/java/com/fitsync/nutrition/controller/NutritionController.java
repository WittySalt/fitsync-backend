package com.fitsync.nutrition.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CreateRecipeRequest;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.dto.RecipeView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutritionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/nutrition")
public class NutritionController {

    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView createMeal(Authentication authentication,
                               @Valid @RequestBody CreateMealRequest request) {
        return nutritionService.createMeal(currentPrincipal(authentication), request);
    }

    @GetMapping("/meals")
    public List<MealView> meals(Authentication authentication,
                                @RequestParam(required = false) Instant from,
                                @RequestParam(required = false) Instant to,
                                @RequestParam(required = false) MealType mealType) {
        return nutritionService.listMeals(currentPrincipal(authentication), from, to, mealType);
    }

    @GetMapping("/meals/{mealId}")
    public MealView meal(Authentication authentication, @PathVariable UUID mealId) {
        return nutritionService.getMeal(currentPrincipal(authentication), mealId);
    }

    @PutMapping("/meals/{mealId}")
    public MealView updateMeal(Authentication authentication,
                               @PathVariable UUID mealId,
                               @Valid @RequestBody CreateMealRequest request) {
        return nutritionService.updateMeal(currentPrincipal(authentication), mealId, request);
    }

    @DeleteMapping("/meals/{mealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMeal(Authentication authentication, @PathVariable UUID mealId) {
        nutritionService.deleteMeal(currentPrincipal(authentication), mealId);
    }

    @GetMapping("/meals/latest")
    public MealView latestMeal(Authentication authentication) {
        return nutritionService.getLatestMeal(currentPrincipal(authentication));
    }

    @PostMapping("/custom-foods")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomFoodView createCustomFood(Authentication authentication,
                                           @Valid @RequestBody CreateCustomFoodRequest request) {
        return nutritionService.createCustomFood(currentPrincipal(authentication), request);
    }

    @GetMapping("/custom-foods")
    public List<CustomFoodView> customFoods(Authentication authentication) {
        return nutritionService.listCustomFoods(currentPrincipal(authentication));
    }

    @GetMapping("/custom-foods/{customFoodId}")
    public CustomFoodView customFood(Authentication authentication, @PathVariable UUID customFoodId) {
        return nutritionService.getCustomFood(currentPrincipal(authentication), customFoodId);
    }

    @PutMapping("/custom-foods/{customFoodId}")
    public CustomFoodView updateCustomFood(Authentication authentication,
                                           @PathVariable UUID customFoodId,
                                           @Valid @RequestBody CreateCustomFoodRequest request) {
        return nutritionService.updateCustomFood(currentPrincipal(authentication), customFoodId, request);
    }

    @DeleteMapping("/custom-foods/{customFoodId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCustomFood(Authentication authentication, @PathVariable UUID customFoodId) {
        nutritionService.deleteCustomFood(currentPrincipal(authentication), customFoodId);
    }

    @GetMapping("/summary/daily")
    public DailyNutritionSummaryView dailySummary(Authentication authentication,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @RequestParam(required = false) String timezone) {
        return nutritionService.getDailySummary(currentPrincipal(authentication), date, timezone);
    }

    @GetMapping("/foods/search")
    public List<FoodSearchResultView> searchFoods(Authentication authentication,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID categoryId,
                                                  @RequestParam(defaultValue = "true") boolean includeSubcategories,
                                                  @RequestParam(required = false) UUID brandId,
                                                  @RequestParam(required = false) Boolean verified,
                                                  @RequestParam(required = false) FoodCatalogScope scope,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        FoodSearchCriteria criteria = new FoodSearchCriteria(
                q, categoryId, includeSubcategories, brandId, verified, scope, page, size);
        return nutritionService.searchFoods(currentPrincipal(authentication), criteria);
    }

    @GetMapping("/categories")
    public List<FoodCategoryView> categories(Authentication authentication) {
        return nutritionService.listCategories(currentPrincipal(authentication));
    }

    @GetMapping("/foods/recent")
    public List<LoggedFoodView> recentFoods(Authentication authentication,
                                            @RequestParam(defaultValue = "20") int limit) {
        return nutritionService.listRecentFoods(currentPrincipal(authentication), limit);
    }

    @GetMapping("/foods/frequent")
    public List<LoggedFoodView> frequentFoods(Authentication authentication,
                                              @RequestParam(defaultValue = "20") int limit) {
        return nutritionService.listFrequentFoods(currentPrincipal(authentication), limit);
    }

    @GetMapping("/foods/{foodId}")
    public FoodDetailView food(Authentication authentication, @PathVariable UUID foodId) {
        return nutritionService.getFood(currentPrincipal(authentication), foodId);
    }

    @GetMapping("/foods/{foodId}/servings")
    public List<FoodServingView> foodServings(Authentication authentication, @PathVariable UUID foodId) {
        return nutritionService.listFoodServings(currentPrincipal(authentication), foodId);
    }

    @GetMapping("/barcodes/{barcode}")
    public FoodDetailView barcodeLookup(Authentication authentication, @PathVariable String barcode) {
        return nutritionService.getFoodByBarcode(currentPrincipal(authentication), barcode);
    }

    @PostMapping("/foods/{foodId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodDetailView cloneFood(Authentication authentication, @PathVariable UUID foodId) {
        return nutritionService.cloneFood(currentPrincipal(authentication), foodId);
    }

    @PostMapping("/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView createRecipe(Authentication authentication,
                                   @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.createRecipe(currentPrincipal(authentication), request);
    }

    @GetMapping("/recipes")
    public List<RecipeView> recipes(Authentication authentication) {
        return nutritionService.listRecipes(currentPrincipal(authentication));
    }

    @GetMapping("/recipes/{recipeId}")
    public RecipeView recipe(Authentication authentication, @PathVariable UUID recipeId) {
        return nutritionService.getRecipe(currentPrincipal(authentication), recipeId);
    }

    @PutMapping("/recipes/{recipeId}")
    public RecipeView updateRecipe(Authentication authentication,
                                   @PathVariable UUID recipeId,
                                   @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.updateRecipe(currentPrincipal(authentication), recipeId, request);
    }

    @DeleteMapping("/recipes/{recipeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecipe(Authentication authentication, @PathVariable UUID recipeId) {
        nutritionService.deleteRecipe(currentPrincipal(authentication), recipeId);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
