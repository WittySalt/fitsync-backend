package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Covers what to cook tonight: the five states of the day, the facts, how many suggestions each state gives,
 * the reasons, the sources, the usual dinner time and that nothing leaks between people. The numbers are worked
 * out by hand from foods stated per 100 g.
 */
class RecipeSuggestionsApiTests extends DiaryApiTestBase {

    private static final String SUGGESTIONS = BASE + "/recipes/suggestions";

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeIngredientRepository ingredientRepository;

    @Autowired
    private NutritionTargetRepository nutritionTargetRepository;

    private Seeded chicken;
    private Seeded rice;
    private Seeded broccoli;
    private UUID chickenBowl;
    private UUID vegStirFry;
    private UUID chickenSalad;

    @BeforeEach
    void seedRecipes() {
        jdbcTemplate.update("delete from recipe_cooks");
        jdbcTemplate.update("delete from recipe_steps");
        jdbcTemplate.update("delete from recipe_ingredients");
        jdbcTemplate.update("delete from recipes");
        chicken = seedFood("Chicken breast", "MEAT_POULTRY", false, "g", 165, "3.6", null, mapOf(), profile ->
                profile.setProteinGrams(new BigDecimal("31"))).addServing("100 g", "g", "100", "100");
        rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile ->
                profile.setProteinGrams(new BigDecimal("2.7"))).addServing("100 g", "g", "100", "100");
        broccoli = seedFood("Broccoli", "VEGETABLES", false, "g", 34, "0.04", null, mapOf(), profile ->
                profile.setProteinGrams(new BigDecimal("2.8"))).addServing("100 g", "g", "100", "100");
        // 377 kcal and 36.45 g protein a serving.
        chickenBowl = recipe("Chicken rice bowl", RecipeScope.SYSTEM, null, "2", ingredient(chicken, "200"),
                ingredient(rice, "300"), ingredient(broccoli, "100"));
        // 204 kcal and 16.8 g protein a serving, mostly vegetables.
        vegStirFry = recipe("Veg stir fry", RecipeScope.SYSTEM, null, "1", ingredient(broccoli, "600"));
        // 282 kcal and 49.3 g protein a serving.
        chickenSalad = recipe("Chicken salad", RecipeScope.SYSTEM, null, "1", ingredient(chicken, "150"),
                ingredient(broccoli, "100"));
    }

    private ResultActions suggestions(String token, String... params) throws Exception {
        var request = get(SUGGESTIONS).header("Authorization", "Bearer " + token);
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    // The five states

    @Test
    void nothingLoggedAndNothingEverCookedSuggestsTheBestLibraryPick() throws Exception {
        String token = tokenFor("sg-empty@example.com");
        targetIs(token, 1800, "120");

        // One meal is 600 kcal and the bowl at 377 is the nearest, with protein towards the whole target.
        suggestions(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NOTHING_LOGGED"))
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0].source").value("LIBRARY"))
                .andExpect(jsonPath("$.facts.eatenKcal").value(0))
                .andExpect(jsonPath("$.facts.targetKcal").value(1800))
                .andExpect(jsonPath("$.facts.usualDinnerTime").doesNotExist());
    }

    @Test
    void nothingLoggedSuggestsTheMostCookedRecipeOfTheMonth() throws Exception {
        String token = tokenFor("sg-cooked@example.com");
        String other = tokenFor("sg-cooked-other@example.com");
        targetIs(token, 1800, "120");
        Instant now = Instant.now();
        cook(token, vegStirFry, now.minus(Duration.ofDays(3)));
        cook(token, chickenBowl, now.minus(Duration.ofDays(9)));
        cook(token, chickenBowl, now.minus(Duration.ofDays(5)));
        // A cook from before the last 30 days does not count.
        cook(token, chickenSalad, now.minus(Duration.ofDays(40)));
        cook(token, chickenSalad, now.minus(Duration.ofDays(41)));
        cook(token, chickenSalad, now.minus(Duration.ofDays(42)));

        suggestions(token)
                .andExpect(jsonPath("$.state").value("NOTHING_LOGGED"))
                .andExpect(jsonPath("$.suggestions[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.suggestions[0].reason").value("Your most cooked this month"))
                .andExpect(jsonPath("$.suggestions[0].source").value("RECENT"));
        // Cooks are private to the person who cooked.
        targetIs(other, 1800, "120");
        suggestions(other)
                .andExpect(jsonPath("$.suggestions[0].reason").value(org.hamcrest.Matchers.not("Your most cooked this month")));
    }

    @Test
    void aMealOtherThanDinnerLeavesTheFitsStateWithThreeSuggestionsAndTheirReasons() throws Exception {
        String token = tokenFor("sg-fits@example.com");
        targetIs(token, 2400, "120");
        // 130 kcal and 2.7 g protein of rice for breakfast.
        logAt(token, "BREAKFAST", rice, "1", Instant.now());

        suggestions(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FITS"))
                .andExpect(jsonPath("$.facts.kcalLeft").value(2270))
                .andExpect(jsonPath("$.facts.eatenKcal").value(130))
                .andExpect(jsonPath("$.facts.targetKcal").value(2400))
                .andExpect(jsonPath("$.facts.proteinMet").value(false))
                .andExpect(jsonPath("$.facts.proteinShortGrams").value(117.3))
                .andExpect(jsonPath("$.suggestions.length()").value(3))
                // One meal is 800 kcal and all three servings are further than 30 % from it, so none scores for
                // the budget and the protein towards the gap of 117.3 g decides: 49.3 g, 36.5 g, 16.8 g.
                .andExpect(jsonPath("$.suggestions[0].name").value("Chicken salad"))
                .andExpect(jsonPath("$.suggestions[0].kcalPerServing").value(282))
                .andExpect(jsonPath("$.suggestions[0].proteinPerServing").value(49.3))
                .andExpect(jsonPath("$.suggestions[0].imageUrl").isString())
                .andExpect(jsonPath("$.suggestions[0].reason").value("Adds 49 g of protein"))
                .andExpect(jsonPath("$.suggestions[1].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.suggestions[1].reason").value("Adds 36 g of protein"))
                .andExpect(jsonPath("$.suggestions[2].name").value("Veg stir fry"));
    }

    @Test
    void aDinnerLoggedTodayFoldsTheCard() throws Exception {
        String token = tokenFor("sg-dinner@example.com");
        targetIs(token, 2100, "120");
        logAt(token, "DINNER", rice, "3", Instant.now());

        suggestions(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DINNER_LOGGED"))
                .andExpect(jsonPath("$.suggestions.length()").value(0))
                .andExpect(jsonPath("$.facts.eatenKcal").value(390))
                .andExpect(jsonPath("$.facts.targetKcal").value(2100));
    }

    @Test
    void pastTheTargetSuggestsTheLightestRecipeUnderFourHundredWithProtein() throws Exception {
        String token = tokenFor("sg-past@example.com");
        targetIs(token, 600, "120");
        logAt(token, "LUNCH", rice, "5", Instant.now());

        suggestions(token)
                .andExpect(jsonPath("$.state").value("PAST_TARGET"))
                .andExpect(jsonPath("$.facts.kcalLeft").value(-50))
                .andExpect(jsonPath("$.suggestions.length()").value(1))
                .andExpect(jsonPath("$.suggestions[0].name").value("Veg stir fry"))
                .andExpect(jsonPath("$.suggestions[0].reason").value("204 kcal, mostly vegetables"));

        // Without the vegetable dish the lightest qualifying recipe is the salad, which is mostly chicken.
        jdbcTemplate.update("update recipes set deleted_at = current_timestamp where recipe_id = ?", vegStirFry);
        suggestions(token)
                .andExpect(jsonPath("$.suggestions[0].name").value("Chicken salad"))
                .andExpect(jsonPath("$.suggestions[0].reason").value("282 kcal, a light option"));
    }

    @Test
    void whenAboutOneMealIsLeftAndProteinIsShortThereAreTwoSuggestionsWithAtLeast25GramsOfProtein() throws Exception {
        String token = tokenFor("sg-protein@example.com");
        targetIs(token, 1800, "120");
        // 1,170 kcal eaten leaves 630, within 15 % of one meal (600), and 24.3 g of 120 g protein is eaten.
        logAt(token, "LUNCH", rice, "9", Instant.now());

        suggestions(token)
                .andExpect(jsonPath("$.state").value("PROTEIN_ONLY"))
                .andExpect(jsonPath("$.facts.kcalLeft").value(630))
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0].name").value("Chicken salad"))
                .andExpect(jsonPath("$.suggestions[1].name").value("Chicken rice bowl"));
    }

    // Parameters, access and the other facts

    @Test
    void limitCapsTheNumberOfSuggestionsAndMustBeBetweenOneAndThree() throws Exception {
        String token = tokenFor("sg-limit@example.com");
        targetIs(token, 2400, "120");
        logAt(token, "BREAKFAST", rice, "1", Instant.now());

        suggestions(token, "limit", "1").andExpect(jsonPath("$.suggestions.length()").value(1));
        suggestions(token, "limit", "2").andExpect(jsonPath("$.suggestions.length()").value(2));
        suggestions(token, "limit", "0").andExpect(status().isBadRequest());
        suggestions(token, "limit", "4").andExpect(status().isBadRequest());
        mockMvc.perform(get(SUGGESTIONS)).andExpect(status().isUnauthorized());
    }

    @Test
    void theUsualDinnerTimeIsTheMedianOfTheLastThirtyDaysAndNeedsThreeDinners() throws Exception {
        String token = tokenFor("sg-usual@example.com");
        targetIs(token, 1800, "120");
        Instant day = Instant.now().truncatedTo(ChronoUnit.DAYS);
        logAt(token, "DINNER", rice, "1", day.minus(Duration.ofDays(2)).plus(Duration.ofMinutes(19 * 60 + 30)));
        logAt(token, "DINNER", rice, "1", day.minus(Duration.ofDays(3)).plus(Duration.ofMinutes(20 * 60)));
        suggestions(token).andExpect(jsonPath("$.facts.usualDinnerTime").doesNotExist());

        logAt(token, "DINNER", rice, "1", day.minus(Duration.ofDays(4)).plus(Duration.ofMinutes(19 * 60)));
        suggestions(token).andExpect(jsonPath("$.facts.usualDinnerTime").value("19:30"));
    }

    @Test
    void aRecipeOfTheCallersOwnIsMineAndStaysPrivate() throws Exception {
        String token = tokenFor("sg-mine@example.com");
        String other = tokenFor("sg-mine-other@example.com");
        targetIs(token, 2400, "120");
        targetIs(other, 2400, "120");
        logAt(token, "BREAKFAST", rice, "1", Instant.now());
        logAt(other, "BREAKFAST", rice, "1", Instant.now());
        // Exactly one meal of 800 kcal and plenty of protein, so it leads for its owner.
        recipe("My big plate", RecipeScope.USER, UUID.fromString(userIdOf(token)), "1",
                ingredient(chicken, "250"), ingredient(rice, "300"));

        suggestions(token)
                .andExpect(jsonPath("$.suggestions[0].name").value("My big plate"))
                .andExpect(jsonPath("$.suggestions[0].source").value("MINE"));
        suggestions(other)
                .andExpect(jsonPath("$.suggestions[?(@.name == 'My big plate')]").isEmpty());
    }

    @Test
    void aRecipeCookedInTheLastWeekMovesDownAndLibrarySortAgreesWithTheCard() throws Exception {
        String token = tokenFor("sg-recent@example.com");
        targetIs(token, 2400, "120");
        logAt(token, "BREAKFAST", rice, "1", Instant.now());
        cook(token, chickenBowl, Instant.now().minus(Duration.ofDays(1)));

        suggestions(token)
                .andExpect(jsonPath("$.suggestions[0].name").value("Chicken salad"))
                .andExpect(jsonPath("$.suggestions[2].name").value("Chicken rice bowl"));
        mockMvc.perform(get(BASE + "/recipes").header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit"))
                .andExpect(jsonPath("$.content[0].name").value("Chicken salad"))
                .andExpect(jsonPath("$.content[2].name").value("Chicken rice bowl"));
    }

    // Helpers

    private void targetIs(String token, int calories, String protein) {
        NutritionTargetEntity target = new NutritionTargetEntity();
        target.setUserId(UUID.fromString(userIdOf(token)));
        target.setCalorieTarget(calories);
        target.setProteinTarget(new BigDecimal(protein));
        target.setProjectionUpdatedAt(Instant.now());
        nutritionTargetRepository.save(target);
    }

    private void logAt(String token, String mealType, Seeded food, String quantity, Instant at) throws Exception {
        mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson(mealType, at.toString(), food, food.servingIds.keySet().iterator().next(),
                                quantity)))
                .andExpect(status().isCreated());
    }

    private void cook(String token, UUID recipeId, Instant finishedAt) throws Exception {
        mockMvc.perform(post(BASE + "/recipes/" + recipeId + "/cooks").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"clientCookId\": \"" + UUID.randomUUID() + "\", \"servings\": 1, \"finishedAt\": \""
                                + finishedAt + "\"}"))
                .andExpect(status().isCreated());
    }

    private RecipeIngredientEntity ingredient(Seeded food, String grams) {
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setIngredientId(UUID.randomUUID());
        ingredient.setFoodCatalogItemId(food.foodId);
        ingredient.setQuantity(BigDecimal.ONE);
        ingredient.setGrams(new BigDecimal(grams));
        return ingredient;
    }

    private UUID recipe(String name, RecipeScope scope, UUID owner, String servings,
                        RecipeIngredientEntity... ingredients) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setRecipeId(UUID.randomUUID());
        recipe.setScope(scope);
        recipe.setUserId(owner);
        recipe.setName(name);
        recipe.setServings(new BigDecimal(servings));
        recipe.setSource(scope == RecipeScope.USER ? "USER" : "THEMEALDB");
        recipe.setImageUrl("https://images.example.test/" + name.split(" ")[0].toLowerCase() + ".jpg");
        recipe.setCategory("Misc");
        recipe.setPublished(true);
        recipeRepository.save(recipe);
        int order = 0;
        for (RecipeIngredientEntity ingredient : ingredients) {
            ingredient.setRecipeId(recipe.getRecipeId());
            ingredient.setSortOrder(order++);
            ingredientRepository.save(ingredient);
        }
        return recipe.getRecipeId();
    }
}
