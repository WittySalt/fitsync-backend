package com.fitsync.nutrition.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.dto.CopyDayRequest;
import com.fitsync.nutrition.dto.CopyMealRequest;
import com.fitsync.nutrition.dto.CreateCustomFoodRequest;
import com.fitsync.nutrition.dto.CreateMealRequest;
import com.fitsync.nutrition.dto.CreateMealTemplateRequest;
import com.fitsync.nutrition.dto.CookLeftoverView;
import com.fitsync.nutrition.dto.CreateRecipeCookRequest;
import com.fitsync.nutrition.dto.LeftoverMealRequest;
import com.fitsync.nutrition.dto.LeftoverMealView;
import com.fitsync.nutrition.dto.CreateRecipeRequest;
import com.fitsync.nutrition.dto.MaterializeTemplateRequest;
import com.fitsync.nutrition.dto.MealTemplateView;
import com.fitsync.nutrition.dto.CustomFoodView;
import com.fitsync.nutrition.dto.DailyNutritionSummaryView;
import com.fitsync.nutrition.dto.NutrientPageView;
import com.fitsync.nutrition.dto.NutrientSummaryView;
import com.fitsync.nutrition.dto.NutritionPageInsightView;
import com.fitsync.nutrition.dto.RangeSummaryView;
import com.fitsync.nutrition.dto.FoodCategoryView;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchCriteria;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.dto.FoodServingView;
import com.fitsync.nutrition.dto.LoggedFoodView;
import com.fitsync.nutrition.dto.LogWaterRequest;
import com.fitsync.nutrition.dto.MealView;
import com.fitsync.nutrition.dto.QuickAddMealRequest;
import com.fitsync.nutrition.dto.UsualMealsView;
import com.fitsync.nutrition.dto.RecipeAddItemsRequest;
import com.fitsync.nutrition.dto.RecipeCookView;
import com.fitsync.nutrition.dto.RecipeDetailView;
import com.fitsync.nutrition.dto.RecipeMealRequest;
import com.fitsync.nutrition.dto.RecipeSuggestionsView;
import com.fitsync.nutrition.dto.RecipeView;
import com.fitsync.nutrition.dto.TemplateRecipeInput;
import com.fitsync.nutrition.dto.WaterDaySummaryView;
import com.fitsync.nutrition.dto.WaterLogView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;
import com.fitsync.nutrition.service.NutritionDiaryService;
import com.fitsync.nutrition.service.NutritionPageInsightService;
import com.fitsync.nutrition.service.NutritionService;
import com.fitsync.nutrition.service.RecipeCookService;
import com.fitsync.nutrition.service.RecipeLibraryService;
import com.fitsync.nutrition.service.RecipeSuggestionService;
import com.fitsync.nutrition.service.UsualMealsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.fitsync.web.timezone.RequestTimezone;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final NutritionPageInsightService nutritionPageInsightService;
    private final UsualMealsService usualMealsService;
    private final NutritionDiaryService nutritionDiaryService;
    private final RecipeLibraryService recipeLibraryService;
    private final RecipeCookService recipeCookService;
    private final RecipeSuggestionService recipeSuggestionService;

    public NutritionController(NutritionService nutritionService,
                               NutritionPageInsightService nutritionPageInsightService,
                               UsualMealsService usualMealsService,
                               NutritionDiaryService nutritionDiaryService,
                               RecipeLibraryService recipeLibraryService,
                               RecipeCookService recipeCookService,
                               RecipeSuggestionService recipeSuggestionService) {
        this.nutritionService = nutritionService;
        this.nutritionPageInsightService = nutritionPageInsightService;
        this.usualMealsService = usualMealsService;
        this.nutritionDiaryService = nutritionDiaryService;
        this.recipeLibraryService = recipeLibraryService;
        this.recipeCookService = recipeCookService;
        this.recipeSuggestionService = recipeSuggestionService;
    }

    @PostMapping("/meals")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView createMeal(Authentication authentication,
                               @Valid @RequestBody CreateMealRequest request,
                               @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.createMeal(currentPrincipal(authentication), request, zoneId(zone));
    }

    @PostMapping("/meals/quick-add")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView quickAddMeal(Authentication authentication,
                                 @Valid @RequestBody QuickAddMealRequest request,
                                 @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.quickAddMeal(currentPrincipal(authentication), request, zoneId(zone));
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
                               @Valid @RequestBody CreateMealRequest request,
                               @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.updateMeal(currentPrincipal(authentication), mealId, request, zoneId(zone));
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

    @GetMapping("/meals/usual")
    public UsualMealsView usualMeals(Authentication authentication,
                                     @RequestTimezone Optional<ZoneId> zone) {
        return usualMealsService.getUsualMeals(currentPrincipal(authentication), zoneId(zone));
    }

    @PostMapping("/meals/{mealId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView copyMeal(Authentication authentication,
                             @PathVariable UUID mealId,
                             @Valid @RequestBody CopyMealRequest request) {
        return nutritionService.copyMeal(currentPrincipal(authentication), mealId, request);
    }

    @PostMapping("/days/{date}/copy-to")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MealView> copyDay(Authentication authentication,
                                  @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @Valid @RequestBody CopyDayRequest request,
                                  @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.copyDay(currentPrincipal(authentication), date, request, zoneId(zone));
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
                                                  @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.getDailySummary(currentPrincipal(authentication), date, zoneId(zone));
    }

    @GetMapping("/summary/range")
    public RangeSummaryView rangeSummary(Authentication authentication,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestTimezone Optional<ZoneId> zone) {
        return nutritionDiaryService.getRangeSummary(currentPrincipal(authentication), from, to, zoneId(zone));
    }

    @GetMapping("/nutrients/summary")
    public NutrientSummaryView nutrientSummary(Authentication authentication,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                               @RequestTimezone Optional<ZoneId> zone,
                                               @RequestParam(defaultValue = "diary") String scope) {
        return nutritionDiaryService.getNutrientSummary(currentPrincipal(authentication), from, to, zoneId(zone),
                scope);
    }

    @GetMapping("/nutrients/{code}")
    public NutrientPageView nutrientPage(Authentication authentication,
                                         @PathVariable String code,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestTimezone Optional<ZoneId> zone) {
        return nutritionDiaryService.getNutrientPage(currentPrincipal(authentication), code, from, to, zoneId(zone));
    }

    @GetMapping("/insights/page")
    public NutritionPageInsightView nutritionPageInsight(Authentication authentication,
                                                         @RequestTimezone Optional<ZoneId> zone,
                                                         @RequestParam(defaultValue = "false") boolean thisWeek) {
        return nutritionPageInsightService.getNutritionPageInsight(currentPrincipal(authentication), zoneId(zone),
                thisWeek);
    }

    @GetMapping("/foods/search")
    public Page<FoodSearchResultView> searchFoods(Authentication authentication,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(required = false) UUID categoryId,
                                                  @RequestParam(defaultValue = "true") boolean includeSubcategories,
                                                  @RequestParam(required = false) UUID brandId,
                                                  @RequestParam(required = false) Boolean verified,
                                                  @RequestParam(required = false) FoodCatalogScope scope,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String foodGroup) {
        FoodSearchCriteria criteria = new FoodSearchCriteria(
                q, categoryId, includeSubcategories, brandId, verified, scope, page, size, foodGroup);
        return nutritionService.searchFoods(currentPrincipal(authentication), criteria);
    }

    @GetMapping("/categories")
    public List<FoodCategoryView> categories(Authentication authentication) {
        return nutritionService.listCategories(currentPrincipal(authentication));
    }

    @GetMapping("/foods/recent")
    public List<LoggedFoodView> recentFoods(Authentication authentication,
                                            @RequestParam(defaultValue = "20") int limit,
                                            @RequestParam(required = false) String foodGroup) {
        return nutritionService.listRecentFoods(currentPrincipal(authentication), limit, foodGroup);
    }

    @GetMapping("/foods/frequent")
    public List<LoggedFoodView> frequentFoods(Authentication authentication,
                                              @RequestParam(defaultValue = "20") int limit,
                                              @RequestParam(required = false) String foodGroup) {
        return nutritionService.listFrequentFoods(currentPrincipal(authentication), limit, foodGroup);
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

    @Operation(summary = "Import an external food by barcode into the reusable food catalog")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "External food imported or existing catalog food returned"),
            @ApiResponse(responseCode = "400", description = "External food cannot be imported"),
            @ApiResponse(responseCode = "404", description = "External food not found or external source disabled")
    })
    @PostMapping("/foods/import/barcode/{barcode}")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodDetailView importFoodByBarcode(Authentication authentication, @PathVariable String barcode) {
        return nutritionService.importFoodByBarcode(currentPrincipal(authentication), barcode);
    }

    @PostMapping("/foods/{foodId}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    public FoodDetailView cloneFood(Authentication authentication, @PathVariable UUID foodId) {
        return nutritionService.cloneFood(currentPrincipal(authentication), foodId);
    }

    @PostMapping("/foods/{foodId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(Authentication authentication, @PathVariable UUID foodId) {
        nutritionService.addFavorite(currentPrincipal(authentication), foodId);
    }

    @DeleteMapping("/foods/{foodId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(Authentication authentication, @PathVariable UUID foodId) {
        nutritionService.removeFavorite(currentPrincipal(authentication), foodId);
    }

    @GetMapping("/favorites")
    public List<FoodSearchResultView> favorites(Authentication authentication) {
        return nutritionService.listFavorites(currentPrincipal(authentication));
    }

    @PostMapping("/meal-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public MealTemplateView createMealTemplate(Authentication authentication,
                                               @Valid @RequestBody CreateMealTemplateRequest request) {
        return nutritionService.createMealTemplate(currentPrincipal(authentication), request);
    }

    @GetMapping("/meal-templates")
    public List<MealTemplateView> mealTemplates(Authentication authentication) {
        return nutritionService.listMealTemplates(currentPrincipal(authentication));
    }

    @GetMapping("/meal-templates/{templateId}")
    public MealTemplateView mealTemplate(Authentication authentication, @PathVariable UUID templateId) {
        return nutritionService.getMealTemplate(currentPrincipal(authentication), templateId);
    }

    @PostMapping("/meal-templates/{templateId}/recipe-items")
    @ResponseStatus(HttpStatus.CREATED)
    public MealTemplateView addRecipeToMealTemplate(Authentication authentication,
                                                    @PathVariable UUID templateId,
                                                    @Valid @RequestBody TemplateRecipeInput request) {
        return nutritionService.addRecipeToMealTemplate(currentPrincipal(authentication), templateId, request);
    }

    @DeleteMapping("/meal-templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMealTemplate(Authentication authentication, @PathVariable UUID templateId) {
        nutritionService.deleteMealTemplate(currentPrincipal(authentication), templateId);
    }

    @PostMapping("/meals/from-template/{templateId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView createMealFromTemplate(Authentication authentication,
                                           @PathVariable UUID templateId,
                                           @Valid @RequestBody MaterializeTemplateRequest request,
                                           @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.createMealFromTemplate(currentPrincipal(authentication), templateId, request,
                zoneId(zone));
    }

    @PostMapping("/water")
    @ResponseStatus(HttpStatus.CREATED)
    public WaterLogView logWater(Authentication authentication,
                                 @Valid @RequestBody LogWaterRequest request,
                                 @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.logWater(currentPrincipal(authentication), request, zoneId(zone));
    }

    @GetMapping("/water")
    public WaterDaySummaryView waterDay(Authentication authentication,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.getWaterDay(currentPrincipal(authentication), date, zoneId(zone));
    }

    @DeleteMapping("/water/{waterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWater(Authentication authentication, @PathVariable UUID waterId) {
        nutritionService.deleteWater(currentPrincipal(authentication), waterId);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportFoodDiary(Authentication authentication,
                                                  @RequestParam Instant from,
                                                  @RequestParam Instant to,
                                                  @RequestParam(defaultValue = "csv") String format) {
        byte[] body = nutritionService.exportFoodDiary(currentPrincipal(authentication), from, to, format);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"food-diary.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @PostMapping("/recipes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeView createRecipe(Authentication authentication,
                                   @Valid @RequestBody CreateRecipeRequest request) {
        return nutritionService.createRecipe(currentPrincipal(authentication), request);
    }

    /**
     * With a scope (mine, library, favorites or recent) this is a page of recipe cards. Without one it keeps
     * its earlier shape, the caller's own recipes as a plain list. sort=fit ranks the cards by how well one
     * serving fits what is left of today, and the X-Timezone header says where today is.
     */
    @GetMapping("/recipes")
    public Object recipes(Authentication authentication,
                          @RequestParam(required = false) String scope,
                          @RequestParam(required = false) String category,
                          @RequestParam(required = false) String q,
                          @RequestParam(required = false) String diet,
                          @RequestParam(required = false) String tag,
                          @RequestParam(required = false) String sort,
                          @RequestTimezone Optional<ZoneId> zone,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "20") int size) {
        if (scope == null) {
            return nutritionService.listRecipes(currentPrincipal(authentication));
        }
        return recipeLibraryService.list(currentPrincipal(authentication), scope, category, q, diet, tag, sort,
                zoneId(zone), page, size);
    }

    /**
     * What to cook tonight: the state of today, the facts behind it and up to three suggestions, each with one
     * sentence of reason. The X-Timezone header says where today is.
     */
    @GetMapping("/recipes/suggestions")
    public RecipeSuggestionsView recipeSuggestions(Authentication authentication,
                                                   @RequestParam(defaultValue = "3") int limit,
                                                   @RequestTimezone Optional<ZoneId> zone) {
        return recipeSuggestionService.suggestions(currentPrincipal(authentication), limit, zoneId(zone));
    }

    @GetMapping("/recipes/{recipeId}")
    public RecipeDetailView recipe(Authentication authentication, @PathVariable UUID recipeId) {
        return recipeLibraryService.detail(currentPrincipal(authentication), recipeId);
    }

    /**
     * Records that the caller cooked a recipe. The first write answers 201, and a retry with the same
     * clientCookId answers 200 with the same record.
     */
    @PostMapping("/recipes/{recipeId}/cooks")
    public org.springframework.http.ResponseEntity<RecipeCookView> recordCook(Authentication authentication,
                                                                              @PathVariable UUID recipeId,
                                                                              @Valid @RequestBody CreateRecipeCookRequest request) {
        RecipeCookService.Recorded recorded = recipeCookService.record(currentPrincipal(authentication), recipeId, request);
        return org.springframework.http.ResponseEntity
                .status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(recorded.view());
    }

    /** The caller's cooks of the last 7 days with servings left, newest first. */
    @GetMapping("/recipes/cooks/leftovers")
    public List<CookLeftoverView> cookLeftovers(Authentication authentication) {
        return recipeCookService.leftovers(currentPrincipal(authentication));
    }

    /** Logs servings of a cook's leftovers as a meal and lowers the count; 409 when too few are left. */
    @PostMapping("/recipes/cooks/{cookId}/leftover")
    @ResponseStatus(HttpStatus.CREATED)
    public LeftoverMealView logLeftover(Authentication authentication,
                                        @PathVariable UUID cookId,
                                        @Valid @RequestBody LeftoverMealRequest request,
                                        @RequestTimezone Optional<ZoneId> zone) {
        return recipeCookService.logLeftover(currentPrincipal(authentication), cookId, request, zoneId(zone));
    }

    @DeleteMapping("/recipes/cooks/{cookId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCook(Authentication authentication, @PathVariable UUID cookId) {
        recipeCookService.delete(currentPrincipal(authentication), cookId);
    }

    @PostMapping("/recipes/{recipeId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void favoriteRecipe(Authentication authentication, @PathVariable UUID recipeId) {
        recipeLibraryService.favorite(currentPrincipal(authentication), recipeId);
    }

    @DeleteMapping("/recipes/{recipeId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfavoriteRecipe(Authentication authentication, @PathVariable UUID recipeId) {
        recipeLibraryService.unfavorite(currentPrincipal(authentication), recipeId);
    }

    @PostMapping("/recipes/{recipeId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeDetailView copyRecipe(Authentication authentication, @PathVariable UUID recipeId) {
        return recipeLibraryService.copy(currentPrincipal(authentication), recipeId);
    }

    @PostMapping("/meals/from-recipe/{recipeId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView createMealFromRecipe(Authentication authentication,
                                         @PathVariable UUID recipeId,
                                         @Valid @RequestBody RecipeMealRequest request,
                                         @RequestTimezone Optional<ZoneId> zone) {
        return nutritionService.createMealFromRecipe(currentPrincipal(authentication), recipeId, request,
                zoneId(zone));
    }

    @PostMapping("/meals/{mealId}/items/from-recipe/{recipeId}")
    @ResponseStatus(HttpStatus.CREATED)
    public MealView addRecipeToMeal(Authentication authentication,
                                    @PathVariable UUID mealId,
                                    @PathVariable UUID recipeId,
                                    @Valid @RequestBody RecipeAddItemsRequest request) {
        return nutritionService.addRecipeToMeal(currentPrincipal(authentication), mealId, recipeId, request);
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

    /** The zone id from the X-Timezone header, or null so the service falls back to UTC. */
    private static String zoneId(Optional<ZoneId> zone) {
        return zone.map(ZoneId::getId).orElse(null);
    }

    private CurrentUserPrincipal currentPrincipal(Authentication authentication) {
        return (CurrentUserPrincipal) authentication.getPrincipal();
    }
}
