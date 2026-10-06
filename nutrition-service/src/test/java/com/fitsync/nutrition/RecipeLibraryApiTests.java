package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import com.fitsync.nutrition.entity.RecipeStepEntity;
import com.fitsync.nutrition.messaging.AccountDeletionProjectionService;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import com.fitsync.nutrition.repository.RecipeIngredientRepository;
import com.fitsync.nutrition.repository.RecipeRepository;
import com.fitsync.nutrition.repository.RecipeStepRepository;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Covers the recipe library: the list pages and their filters, the detail with nutrition per serving, saving and
 * copying system recipes, logging a recipe as a meal, recipes inside meal templates, soft delete and the account
 * purge. The numbers are worked out by hand from the seeded foods, which are all stated per 100 g.
 */
class RecipeLibraryApiTests extends DiaryApiTestBase {

    private static final String RECIPES = BASE + "/recipes";

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeIngredientRepository ingredientRepository;

    @Autowired
    private RecipeStepRepository stepRepository;

    @Autowired
    private AccountDeletionProjectionService accountDeletion;

    @Autowired
    private NutritionTargetRepository nutritionTargetRepository;

    private Seeded chicken;
    private Seeded rice;
    private Seeded broccoli;
    private Seeded beans;
    private Seeded milk;
    private UUID chickenBowl;
    private UUID lentilSalad;
    private UUID milkPorridge;
    private UUID unpublished;

    @BeforeEach
    void seedRecipes() {
        jdbcTemplate.update("delete from meal_template_items");
        jdbcTemplate.update("delete from meal_templates");
        jdbcTemplate.update("delete from recipe_favorites");
        jdbcTemplate.update("delete from recipe_cooks");
        jdbcTemplate.update("delete from recipe_steps");
        jdbcTemplate.update("delete from recipe_ingredients");
        jdbcTemplate.update("delete from recipes");
        chicken = seedFood("Chicken breast", "MEAT_POULTRY", false, "g", 165, "3.6", null, mapOf("POTASSIUM", "256"),
                profile -> {
                    profile.setProteinGrams(new BigDecimal("31"));
                    profile.setSodiumMg(new BigDecimal("74"));
                }).addServing("100 g", "g", "100", "100");
        rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile -> {
            profile.setProteinGrams(new BigDecimal("2.7"));
            profile.setCarbsGrams(new BigDecimal("28"));
            profile.setFiberGrams(new BigDecimal("0.4"));
            profile.setSodiumMg(new BigDecimal("1"));
        }).addServing("100 g", "g", "100", "100");
        broccoli = seedFood("Broccoli", "VEGETABLES", false, "g", 34, "0.04", null, mapOf(), profile -> {
            profile.setProteinGrams(new BigDecimal("2.8"));
            profile.setFiberGrams(new BigDecimal("2.6"));
            profile.setSugarGrams(new BigDecimal("1.7"));
            profile.setSodiumMg(new BigDecimal("33"));
        }).addServing("100 g", "g", "100", "100");
        beans = seedFood("Lentils", "LEGUMES", false, "g", 116, "0.05", null, mapOf(), profile -> {
            profile.setProteinGrams(new BigDecimal("9"));
            profile.setFiberGrams(new BigDecimal("7.9"));
        }).addServing("100 g", "g", "100", "100");
        milk = seedFood("Whole milk", "DAIRY", false, "ml", 61, "1.9", "88", mapOf(), profile ->
                profile.setProteinGrams(new BigDecimal("3.2"))).addServing("1 cup", "cup", "1", "244");
        chickenBowl = systemRecipe("Chicken rice bowl", "Chicken", "2", true, ingredient(chicken, "200"),
                ingredient(rice, "300"), ingredient(broccoli, "100"));
        lentilSalad = systemRecipe("Lentil salad", "Vegan", "4", true, ingredient(beans, "400"), ingredient(broccoli, "100"));
        milkPorridge = systemRecipe("Milk porridge", "Breakfast", "1", true, ingredient(milk, "200"), ingredient(rice, "100"));
        unpublished = systemRecipe("Hidden stew", "Beef", "1", false, ingredient(chicken, "100"));
        // A deleted system recipe must stay out of the library as well.
        UUID deleted = systemRecipe("Deleted soup", "Soup", "1", true, ingredient(broccoli, "100"));
        jdbcTemplate.update("update recipes set deleted_at = current_timestamp where recipe_id = ?", deleted);
    }

    @Test
    void libraryListsPublishedSystemRecipesWithTheFieldsOfACard() throws Exception {
        String token = tokenFor("library@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[0].scope").value("SYSTEM"))
                .andExpect(jsonPath("$.content[0].imageUrl").value("https://images.example.test/chicken.jpg"))
                .andExpect(jsonPath("$.content[0].imageCredit").value("Photo from TheMealDB"))
                .andExpect(jsonPath("$.content[0].category").value("Chicken"))
                .andExpect(jsonPath("$.content[0].servings").value(2))
                // 754 kcal over 2 servings, and 72.9 g protein over 2 servings.
                .andExpect(jsonPath("$.content[0].kcalPerServing").value(377))
                .andExpect(jsonPath("$.content[0].proteinGramsPerServing").value(36.45))
                .andExpect(jsonPath("$.content[0].fatGramsPerServing").isNumber())
                .andExpect(jsonPath("$.content[0].tags[0]").value("High protein"))
                .andExpect(jsonPath("$.content[0].tags.length()").value(1))
                .andExpect(jsonPath("$.content[0].dietFlags.length()").value(0))
                .andExpect(jsonPath("$.content[0].topFoodGroups[0]").value("GRAINS"))
                .andExpect(jsonPath("$.content[0].topFoodGroups[1]").value("PROTEIN"))
                .andExpect(jsonPath("$.content[0].topFoodGroups[2]").value("VEG"))
                .andExpect(jsonPath("$.content[0].favorite").value(false))
                .andExpect(jsonPath("$.content[1].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[1].tags[0]").value("High protein"))
                .andExpect(jsonPath("$.content[1].tags[1]").value("High fibre"))
                .andExpect(jsonPath("$.content[1].dietFlags[0]").value("VEGETARIAN"))
                .andExpect(jsonPath("$.content[1].dietFlags[1]").value("VEGAN"))
                .andExpect(jsonPath("$.content[2].name").value("Milk porridge"))
                .andExpect(jsonPath("$.content[2].dietFlags[0]").value("VEGETARIAN"))
                .andExpect(jsonPath("$.content[2].dietFlags.length()").value(1));
    }

    @Test
    void libraryFiltersByCategoryTextAndDietAndPages() throws Exception {
        String token = tokenFor("filters@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("category", "vegan"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("q", "CHICKEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
        // The percent sign is a character to look for and not a wildcard.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("q", "%"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("diet", "VEGAN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("diet", "vegetarian"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Milk porridge"));
    }

    @Test
    void listRejectsBadInputAndNeedsASignedInUser() throws Exception {
        String token = tokenFor("bad-list@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "everyone"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("diet", "KETO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("q", "x".repeat(81)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RECIPES).param("scope", "library")).andExpect(status().isUnauthorized());
    }

    @Test
    void withoutAScopeTheListKeepsItsEarlierShape() throws Exception {
        String token = tokenFor("legacy@example.com");
        createOwnRecipe(token, "Legacy bowl");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Legacy bowl"))
                .andExpect(jsonPath("$[0].totalCalories").value(330));
    }

    @Test
    void ownRecipesAreWrittenWithStepsAndIngredientTextAndStayPrivate() throws Exception {
        String token = tokenFor("owner@example.com");
        String other = tokenFor("stranger@example.com");

        String body = mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Roast chicken", 2, "200", "2 chicken breasts",
                                "[{\"text\":\"Season the chicken.\"},{\"text\":\"Roast for 25 minutes.\"}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCalories").value(330))
                .andExpect(jsonPath("$.perServingCalories").value(165))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.recipeId");

        mockMvc.perform(get(RECIPES + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("USER"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.favorite").value(false))
                .andExpect(jsonPath("$.ingredients[0].displayText").value("2 chicken breasts"))
                .andExpect(jsonPath("$.ingredients[0].foodName").value("Chicken breast"))
                // The user's own recipe shows the food's name, not the line as written.
                .andExpect(jsonPath("$.ingredients[0].name").value("Chicken breast"))
                .andExpect(jsonPath("$.ingredients[0].grams").value(200.0))
                .andExpect(jsonPath("$.ingredients[0].foodGroup").value("PROTEIN"))
                .andExpect(jsonPath("$.ingredients[0].categoryCode").value("MEAT_POULTRY"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].stepNumber").value(1))
                .andExpect(jsonPath("$.steps[1].text").value("Roast for 25 minutes."));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "mine"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].scope").value("USER"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + other).param("scope", "mine"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + other).param("scope", "library"))
                .andExpect(jsonPath("$.totalElements").value(3));
        // Somebody else's recipe looks exactly like one that does not exist.
        mockMvc.perform(get(RECIPES + "/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(RECIPES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content(recipeJson("Stolen", 1, "100", null, "[]")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(RECIPES + "/" + id).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        // A new version replaces the steps, and an empty list clears them.
        mockMvc.perform(put(RECIPES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Roast chicken", 2, "200", null, "[{\"text\":\"Just roast it.\"}]")))
                .andExpect(status().isOk());
        mockMvc.perform(get(RECIPES + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.steps.length()").value(1))
                .andExpect(jsonPath("$.steps[0].text").value("Just roast it."))
                .andExpect(jsonPath("$.ingredients[0].displayText").value("Chicken breast"));
    }

    @Test
    void detailStepsCarryATitleTheIngredientsTheyUseAndTheirTimers() throws Exception {
        String token = tokenFor("cook@example.com");
        String body = mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Roast chicken", 2, "200", "2 chicken breasts",
                                "[{\"text\":\"Season the chicken breasts with salt. Rest them.\"},"
                                + "{\"text\":\"Roast for 25 minutes, 3 mins each side.\"}]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.recipeId");
        String ingredientId = JsonPath.read(body, "$.ingredients[0].ingredientId");

        mockMvc.perform(get(RECIPES + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps[0].title").value("Season the chicken breasts with salt"))
                .andExpect(jsonPath("$.steps[0].ingredientIds.length()").value(1))
                .andExpect(jsonPath("$.steps[0].ingredientIds[0]").value(ingredientId))
                .andExpect(jsonPath("$.steps[0].timers.length()").value(0))
                .andExpect(jsonPath("$.steps[1].title").value("Roast for 25 minutes, 3 mins each side"))
                .andExpect(jsonPath("$.steps[1].ingredientIds.length()").value(0))
                .andExpect(jsonPath("$.steps[1].timers.length()").value(2))
                .andExpect(jsonPath("$.steps[1].timers[0].seconds").value(1500))
                .andExpect(jsonPath("$.steps[1].timers[0].maxSeconds").value(1500))
                .andExpect(jsonPath("$.steps[1].timers[0].eachSide").value(true))
                .andExpect(jsonPath("$.steps[1].timers[0].phrase").value("25 minutes"))
                .andExpect(jsonPath("$.steps[1].timers[1].seconds").value(180));
    }

    @Test
    void writeValidationRejectsBlankStepsAndNonPositiveGrams() throws Exception {
        String token = tokenFor("validation@example.com");

        mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Bad steps", 1, "100", null, "[{\"text\":\"  \"}]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Bad grams", 1, "0", null, "[]")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailShowsNutritionPerServingTagsAndTheShareOfTheDailyReference() throws Exception {
        String token = tokenFor("detail@example.com");

        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.scope").value("SYSTEM"))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.source").value("THEMEALDB"))
                .andExpect(jsonPath("$.sourceUrl").value("https://source.example.test/chicken"))
                .andExpect(jsonPath("$.imageCredit").value("Photo from TheMealDB"))
                .andExpect(jsonPath("$.ingredients.length()").value(3))
                .andExpect(jsonPath("$.ingredients[1].displayText").value("White rice"))
                .andExpect(jsonPath("$.ingredients[1].grams").value(300.0))
                .andExpect(jsonPath("$.perServing.kcal").value(377))
                .andExpect(jsonPath("$.perServing.proteinGrams").value(36.45))
                // 200 g chicken, 300 g rice and 100 g broccoli over two servings.
                .andExpect(jsonPath("$.perServing.carbsGrams").value(42.0))
                .andExpect(jsonPath("$.perServing.fiberGrams").value(1.9))
                .andExpect(jsonPath("$.perServing.sugarGrams").value(0.85))
                .andExpect(jsonPath("$.perServing.sodiumMg").value(92.0))
                .andExpect(jsonPath("$.perServing.saturatedFatGrams").value(3.77))
                // Potassium comes from the detail rows because no profile states it: 512 mg over two servings.
                .andExpect(jsonPath("$.perServing.potassiumMg").value(256.0))
                .andExpect(jsonPath("$.perServing.nutrients[0].code").value("POTASSIUM"))
                .andExpect(jsonPath("$.perServing.nutrients[0].amount").value(256.0))
                .andExpect(jsonPath("$.perServing.nutrients[0].unit").value("mg"))
                // The reference for a person nothing is known about is 3000 mg, so 256 mg is 0.085 of it.
                .andExpect(jsonPath("$.perServing.nutrients[0].share").value(0.085))
                .andExpect(jsonPath("$.groupsGrams.GRAINS").value(300.0))
                .andExpect(jsonPath("$.groupsGrams.PROTEIN").value(200.0))
                .andExpect(jsonPath("$.groupsGrams.VEG").value(100.0))
                .andExpect(jsonPath("$.tags[0]").value("High protein"))
                .andExpect(jsonPath("$.dietFlags.length()").value(0));
    }

    @Test
    void aSystemRecipeThatIsNotPublishedOrNotYoursCannotBeSeenOrChanged() throws Exception {
        String token = tokenFor("hidden@example.com");

        mockMvc.perform(get(RECIPES + "/" + unpublished).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(RECIPES + "/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(RECIPES + "/" + chickenBowl).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("Changed", 1, "100", null, "[]")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(RECIPES + "/" + unpublished + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/meals/from-recipe/" + unpublished).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(fromRecipeJson("1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(RECIPES + "/" + chickenBowl)).andExpect(status().isUnauthorized());
    }

    @Test
    void savingASystemRecipeIsIdempotentAndShowsInTheFavoritesList() throws Exception {
        String token = tokenFor("saver@example.com");
        String other = tokenFor("not-saver@example.com");

        mockMvc.perform(post(RECIPES + "/" + chickenBowl + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(RECIPES + "/" + chickenBowl + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "favorites"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[0].favorite").value(true));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "library"))
                .andExpect(jsonPath("$.content[?(@.name == 'Chicken rice bowl')].favorite").value(true))
                .andExpect(jsonPath("$.content[?(@.name == 'Lentil salad')].favorite").value(false));
        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.favorite").value(true));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + other).param("scope", "favorites"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(delete(RECIPES + "/" + chickenBowl + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(RECIPES + "/" + chickenBowl + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "favorites"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ownRecipesCannotBeSaved() throws Exception {
        String token = tokenFor("own-save@example.com");
        String id = createOwnRecipe(token, "Mine");

        mockMvc.perform(post(RECIPES + "/" + id + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void copyingMakesAnEditableRecipeWithIngredientsStepsCreditAndPicture() throws Exception {
        String token = tokenFor("copier@example.com");

        String body = mockMvc.perform(post(RECIPES + "/" + chickenBowl + "/copy").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("USER"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.source").value("THEMEALDB"))
                .andExpect(jsonPath("$.imageUrl").value("https://images.example.test/chicken.jpg"))
                .andExpect(jsonPath("$.imageCredit").value("Photo from TheMealDB"))
                .andExpect(jsonPath("$.ingredients.length()").value(3))
                .andExpect(jsonPath("$.ingredients[0].displayText").value("Chicken breast"))
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[0].text").value("Cook the rice."))
                .andExpect(jsonPath("$.perServing.kcal").value(377))
                .andReturn().getResponse().getContentAsString();
        String copyId = JsonPath.read(body, "$.id");
        Assertions.assertNotEquals(chickenBowl.toString(), copyId);
        Assertions.assertEquals("meal-chicken", jdbcTemplate.queryForObject(
                "select source_ref from recipes where recipe_id = ?", String.class, UUID.fromString(copyId)));

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "mine"))
                .andExpect(jsonPath("$.totalElements").value(1));
        // The system recipe is untouched, and the copy can be edited.
        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.steps.length()").value(2));
        mockMvc.perform(put(RECIPES + "/" + copyId).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson("My bowl", 1, "150", null, "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My bowl"));
        mockMvc.perform(post(RECIPES + "/" + unpublished + "/copy").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void loggingARecipeMakesOneMealOfSnapshottedIngredients() throws Exception {
        String token = tokenFor("logger@example.com");

        // One serving of a two-serving recipe is half of every ingredient: 100 g, 150 g and 50 g.
        String body = mockMvc.perform(post(BASE + "/meals/from-recipe/" + chickenBowl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(fromRecipeJson("1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.mealType").value("DINNER"))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.totalCalories").value(377))
                .andExpect(jsonPath("$.items[0].foodName").value("Chicken breast"))
                .andExpect(jsonPath("$.items[0].recipeId").value(chickenBowl.toString()))
                .andExpect(jsonPath("$.items[0].recipeServings").value(1.0))
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(100.0))
                .andExpect(jsonPath("$.items[0].calories").value(165))
                .andExpect(jsonPath("$.items[0].saturatedFatGrams").value(3.6))
                .andExpect(jsonPath("$.items[0].categoryCode").value("MEAT_POULTRY"))
                .andExpect(jsonPath("$.items[0].foodGroup").value("PROTEIN"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].nutrientCode").value("POTASSIUM"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].amount").value(256.0))
                .andExpect(jsonPath("$.items[1].gramsConsumed").value(150.0))
                .andExpect(jsonPath("$.items[1].recipeId").value(chickenBowl.toString()))
                .andExpect(jsonPath("$.items[2].gramsConsumed").value(50.0))
                .andReturn().getResponse().getContentAsString();
        String mealId = JsonPath.read(body, "$.mealId");

        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where event_type = 'MealLogged'", Integer.class));
        mockMvc.perform(get(BASE + "/meals/" + mealId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items[0].recipeId").value(chickenBowl.toString()));
    }

    @Test
    void servingsOutsideTheAllowedRangeAreRejected() throws Exception {
        String token = tokenFor("range@example.com");

        for (String servings : new String[] {"0.1", "21", "0", "-1"}) {
            mockMvc.perform(post(BASE + "/meals/from-recipe/" + chickenBowl).contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content(fromRecipeJson(servings)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(BASE + "/meals/from-recipe/" + chickenBowl).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"servings\": 1, \"consumedAt\": \"2026-06-11T18:00:00Z\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/meals/from-recipe/" + chickenBowl).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(fromRecipeJson("0.25")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(25.0));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from meal_logs where meal_type = 'LUNCH'", Integer.class));
    }

    @Test
    void aRecipeCanBeAddedToAMealThatIsAlreadyLogged() throws Exception {
        String token = tokenFor("adder@example.com");
        String mealId = logMeal(token, "LUNCH", rice, "2026-06-11T12:00:00Z");

        mockMvc.perform(post(BASE + "/meals/" + mealId + "/items/from-recipe/" + lentilSalad)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"servings\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.items[0].recipeId").doesNotExist())
                .andExpect(jsonPath("$.items[1].recipeId").value(lentilSalad.toString()))
                .andExpect(jsonPath("$.items[1].recipeServings").value(2.0))
                // Two of four servings is half of 400 g of lentils.
                .andExpect(jsonPath("$.items[1].gramsConsumed").value(200.0));
        mockMvc.perform(post(BASE + "/meals/" + mealId + "/items/from-recipe/" + lentilSalad)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + tokenFor("somebody-else@example.com"))
                        .content("{\"servings\": 2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTemplateCanHoldARecipeAndLogsItFromTheRecipeAsItIsNow() throws Exception {
        String token = tokenFor("template@example.com");
        String id = createOwnRecipe(token, "Template bowl");

        String body = mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Lunch box\", \"mealType\": \"LUNCH\", "
                                + "\"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 2}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].recipeId").value(id))
                .andExpect(jsonPath("$.items[0].recipeServings").value(2.0))
                .andExpect(jsonPath("$.items[0].foodName").value("Template bowl"))
                // Two servings of a recipe that makes two servings of 330 kcal in total.
                .andExpect(jsonPath("$.items[0].calories").value(330))
                .andExpect(jsonPath("$.totalCalories").value(330))
                .andReturn().getResponse().getContentAsString();
        String templateId = JsonPath.read(body, "$.templateId");

        // The recipe gains a second ingredient before the template is used.
        mockMvc.perform(put(RECIPES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Template bowl\", \"servings\": 2, \"ingredients\": ["
                                + "{\"foodCatalogItemId\": \"" + chicken.foodId + "\", \"quantity\": 1, \"grams\": 200},"
                                + "{\"foodCatalogItemId\": \"" + rice.foodId + "\", \"quantity\": 1, \"grams\": 100}]}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(BASE + "/meals/from-template/" + templateId).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"consumedAt\": \"2026-06-11T12:30:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.items[0].recipeId").value(id))
                .andExpect(jsonPath("$.items[0].recipeServings").value(2.0))
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(200.0))
                .andExpect(jsonPath("$.items[1].gramsConsumed").value(100.0))
                .andExpect(jsonPath("$.totalCalories").value(460));
        mockMvc.perform(get(BASE + "/meal-templates/" + templateId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items[0].recipeId").value(id));
    }

    @Test
    void aTemplateRejectsAnotherUsersRecipeAndMixedSources() throws Exception {
        String token = tokenFor("template-owner@example.com");
        String id = createOwnRecipe(token, "Private bowl");
        String other = tokenFor("template-other@example.com");

        mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content("{\"name\": \"Nope\", \"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 1}]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Bad\", \"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 50}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Empty\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Both\", \"fromMealId\": \"" + UUID.randomUUID() + "\", "
                                + "\"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 1}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importedRecipesShowTheIngredientWordWhileTheFoodNameStaysAvailable() throws Exception {
        String token = tokenFor("names@example.com");
        jdbcTemplate.update("update recipe_ingredients set display_text = '400g Chicken' "
                + "where recipe_id = ? and sort_order = 0", chickenBowl);
        jdbcTemplate.update("update recipe_ingredients set display_text = 'zest and juice of 1 Broccoli' "
                + "where recipe_id = ? and sort_order = 2", chickenBowl);

        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients[0].displayText").value("400g Chicken"))
                .andExpect(jsonPath("$.ingredients[0].name").value("Chicken"))
                .andExpect(jsonPath("$.ingredients[0].foodName").value("Chicken breast"))
                // No written line: the food name is the fallback.
                .andExpect(jsonPath("$.ingredients[1].name").value("White rice"))
                .andExpect(jsonPath("$.ingredients[2].name").value("Broccoli"));
    }

    @Test
    void aRecipeCanBeAppendedToAnExistingTemplateWithoutRecreatingIt() throws Exception {
        String token = tokenFor("append@example.com");
        String id = createOwnRecipe(token, "Append bowl");
        String created = mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Box\", \"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String templateId = JsonPath.read(created, "$.templateId");
        String firstItemId = JsonPath.read(created, "$.items[0].templateItemId");

        mockMvc.perform(post(BASE + "/meal-templates/" + templateId + "/recipe-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"recipeId\": \"" + lentilSalad + "\", \"servings\": 2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateId").value(templateId))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].templateItemId").value(firstItemId))
                .andExpect(jsonPath("$.items[1].recipeId").value(lentilSalad.toString()))
                .andExpect(jsonPath("$.items[1].recipeServings").value(2.0))
                .andExpect(jsonPath("$.items[1].foodName").value("Lentil salad"));
        mockMvc.perform(get(BASE + "/meal-templates/" + templateId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void appendingARecipeToATemplateChecksOwnershipVisibilityAndServings() throws Exception {
        String token = tokenFor("append-owner@example.com");
        String id = createOwnRecipe(token, "Owner bowl");
        String created = mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Mine\", \"recipeItems\": [{\"recipeId\": \"" + id + "\", \"servings\": 1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String templateId = JsonPath.read(created, "$.templateId");
        String other = tokenFor("append-other@example.com");
        String path = BASE + "/meal-templates/" + templateId + "/recipe-items";

        // A template that belongs to somebody else.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content("{\"recipeId\": \"" + lentilSalad + "\", \"servings\": 1}"))
                .andExpect(status().isNotFound());
        // A recipe the caller cannot see: an unpublished system recipe.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"recipeId\": \"" + unpublished + "\", \"servings\": 1}"))
                .andExpect(status().isNotFound());
        // A recipe the caller cannot see: somebody else's own recipe, added to the caller's own template.
        String otherTemplate = JsonPath.read(mockMvc.perform(post(BASE + "/meal-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content("{\"name\": \"Theirs\", \"recipeItems\": [{\"recipeId\": \"" + lentilSalad
                                + "\", \"servings\": 1}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.templateId");
        mockMvc.perform(post(BASE + "/meal-templates/" + otherTemplate + "/recipe-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + other)
                        .content("{\"recipeId\": \"" + id + "\", \"servings\": 1}"))
                .andExpect(status().isNotFound());
        // Servings out of range, and a missing recipe id.
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"recipeId\": \"" + lentilSalad + "\", \"servings\": 0.1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"recipeId\": \"" + lentilSalad + "\", \"servings\": 21}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"servings\": 1}"))
                .andExpect(status().isBadRequest());
        // Nothing was added by the rejected calls.
        mockMvc.perform(get(BASE + "/meal-templates/" + templateId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void theTagFilterKeepsTheMatchingRecipesAndCountsTheFilteredSet() throws Exception {
        String token = tokenFor("tags@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "HIGH_FIBRE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        // Two recipes are high in protein; with a page size of one the total still counts both.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "HIGH_PROTEIN").param("size", "1").param("page", "0"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "HIGH_PROTEIN").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "HIGH_PROTEIN").param("size", "1").param("page", "5"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "HIGH_SALT"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("tag", "TASTY"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingARecipeMarksItAndAccountDeletionRemovesTheRows() throws Exception {
        String token = tokenFor("deleter@example.com");
        String userId = userIdOf(token);
        String id = createOwnRecipe(token, "Doomed bowl");
        String mealBody = mockMvc.perform(post(BASE + "/meals/from-recipe/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(fromRecipeJson("1")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mealId = JsonPath.read(mealBody, "$.mealId");

        mockMvc.perform(delete(RECIPES + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RECIPES + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "mine"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
        // The row is kept with its ingredients and steps, and a meal logged from it still reads as before.
        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from recipes where recipe_id = ? and deleted_at is not null",
                Integer.class, UUID.fromString(id)));
        Assertions.assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from recipe_steps where recipe_id = ?", Integer.class, UUID.fromString(id)));
        mockMvc.perform(get(BASE + "/meals/" + mealId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items[0].recipeId").value(id));
        mockMvc.perform(post(BASE + "/meals/from-recipe/" + id).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(fromRecipeJson("1")))
                .andExpect(status().isNotFound());

        accountDeletion.deleteUserData(UUID.fromString(userId));

        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from recipes where user_id = ?", Integer.class, UUID.fromString(userId)));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from recipe_steps where recipe_id = ?", Integer.class, UUID.fromString(id)));
        Assertions.assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from recipe_ingredients where recipe_id = ?", Integer.class, UUID.fromString(id)));
        // The shared library is not touched.
        Assertions.assertEquals(5, jdbcTemplate.queryForObject("select count(*) from recipes", Integer.class));
    }

    @Test
    void aNormalMealItemHasNoRecipe() throws Exception {
        String token = tokenFor("plain@example.com");
        String mealId = logMeal(token, rice, "2026-06-11T12:00:00Z");

        mockMvc.perform(get(BASE + "/meals/" + mealId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items[0].recipeId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].recipeServings").value(Matchers.nullValue()));
    }

    private String createOwnRecipe(String token, String name) throws Exception {
        String body = mockMvc.perform(post(RECIPES).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(recipeJson(name, 2, "200", null,
                                "[{\"text\":\"Cook it.\"},{\"text\":\"Serve it.\"}]")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.recipeId");
    }

    private String recipeJson(String name, int servings, String chickenGrams, String displayText, String steps) {
        String text = displayText == null ? "" : ", \"displayText\": \"" + displayText + "\"";
        return "{\"name\": \"" + name + "\", \"servings\": " + servings + ", \"ingredients\": ["
                + "{\"foodCatalogItemId\": \"" + chicken.foodId + "\", \"quantity\": 1, \"grams\": " + chickenGrams
                + text + "}], \"steps\": " + steps + "}";
    }

    private void targetIs(String token, int calories, String protein) {
        NutritionTargetEntity target = new NutritionTargetEntity();
        target.setUserId(UUID.fromString(userIdOf(token)));
        target.setCalorieTarget(calories);
        target.setProteinTarget(new BigDecimal(protein));
        target.setProjectionUpdatedAt(Instant.now());
        nutritionTargetRepository.save(target);
    }

    private String logRecipeNow(String token, UUID recipeId, Instant at) throws Exception {
        String body = mockMvc.perform(post(BASE + "/meals/from-recipe/" + recipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"servings\": 1, \"mealType\": \"DINNER\", \"consumedAt\": \"" + at
                                + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.mealId");
    }

    @Test
    void fitSortPutsServingsUnderTheCaloriesLeftFirstAndClosestFirst() throws Exception {
        String token = tokenFor("fit-a@example.com");
        targetIs(token, 1200, "100");

        // Chicken bowl 377 kcal, milk porridge 252 kcal and lentil salad 125 kcal per serving. With 1,200 kcal left
        // one meal is a third of the target, 400 kcal, and the bowl is closest to it.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                // The porridge and the salad are both more than 30 % away from 400 kcal and score zero for the
                // budget, so the 9.7 g of protein in the salad puts it ahead of the 9.1 g in the porridge.
                .andExpect(jsonPath("$.content[1].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[2].name").value("Milk porridge"))
                .andExpect(jsonPath("$.fit.kcalLeft").value(1200))
                .andExpect(jsonPath("$.fit.proteinShortGrams").value(100.0))
                .andExpect(jsonPath("$.fit.lowestNutrient").doesNotExist());

        // With a 900 kcal target one meal is 300 kcal, so the porridge is closest and the bowl comes next.
        String second = tokenFor("fit-b@example.com");
        targetIs(second, 900, "100");
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + second)
                        .param("scope", "library").param("sort", "fit"))
                .andExpect(jsonPath("$.content[0].name").value("Milk porridge"))
                .andExpect(jsonPath("$.content[1].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[2].name").value("Lentil salad"))
                .andExpect(jsonPath("$.fit.kcalLeft").value(900));
    }

    @Test
    void fitSortPagesOverTheRankedSetAndKeepsTheOtherFilters() throws Exception {
        String token = tokenFor("fit-c@example.com");
        targetIs(token, 1200, "100");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Milk porridge"))
                .andExpect(jsonPath("$.fit.kcalLeft").value(1200));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit").param("diet", "VEGAN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit").param("tag", "HIGH_FIBRE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void fitSortCountsWhatWasEatenTodayAndNamesTheLowestNutrient() throws Exception {
        String token = tokenFor("fit-d@example.com");
        personIs("MALE", 30, "70", null);
        targetIs(token, 2000, "100");
        Seeded tofu = seedFood("Tofu", "LEGUMES", false, "g", 100, "0.5", null,
                mapOf("CALCIUM", "300", "IRON", "2"), profile -> profile.setProteinGrams(new BigDecimal("10")))
                .addServing("100 g", "g", "100", "100");
        UUID tofuPlate = systemRecipe("Tofu plate", "Vegan", "1", true, ingredient(tofu, "200"));
        logRecipeNow(token, tofuPlate, Instant.now());

        // One serving of the tofu plate was eaten: 200 kcal, 20 g protein, 600 mg calcium and 4 mg iron.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fit.kcalLeft").value(1800))
                .andExpect(jsonPath("$.fit.proteinShortGrams").value(80.0))
                // Calcium is 600 mg of about 1000 and iron 4 mg of about 8 to 11, so iron is the lowest.
                .andExpect(jsonPath("$.fit.lowestNutrient.code").value("IRON"))
                .andExpect(jsonPath("$.fit.lowestNutrient.displayName").value("Iron"));
    }

    @Test
    void fitSortWithoutATargetFallsBackToTheNameOrderAndHasNoFigures() throws Exception {
        String token = tokenFor("fit-e@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[1].name").value("Lentil salad"))
                .andExpect(jsonPath("$.fit.kcalLeft").doesNotExist())
                .andExpect(jsonPath("$.fit.proteinShortGrams").doesNotExist());
    }

    @Test
    void proteinSortRanksByProteinPerServingHighestFirstWithTagsPagingAndNoFit() throws Exception {
        String token = tokenFor("protein-a@example.com");

        // Per serving: chicken bowl 36.45 g, lentil salad 9.7 g, milk porridge 9.1 g.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "protein"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[1].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[2].name").value("Milk porridge"))
                .andExpect(jsonPath("$.fit").doesNotExist());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "protein").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "protein").param("tag", "HIGH_FIBRE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "newest"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("sort must be name, fit or protein"));
    }

    @Test
    void anUnknownSortIsRejectedAndTheDefaultOrderHasNoFit() throws Exception {
        String token = tokenFor("fit-f@example.com");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "newest"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "name"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fit").doesNotExist());
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token)
                        .param("scope", "library").param("sort", "fit").header("X-Timezone", "Not/AZone"))
                .andExpect(status().isOk());
    }

    @Test
    void recentListsTheCallersCookedRecipesNewestFirstWithTheTimeOfTheLastMeal() throws Exception {
        String token = tokenFor("recent-a@example.com");
        Instant older = Instant.parse("2026-06-10T18:00:00Z");
        Instant newer = Instant.parse("2026-06-11T18:00:00Z");
        logRecipeNow(token, lentilSalad, older);
        logRecipeNow(token, chickenBowl, newer.minusSeconds(3600));
        logRecipeNow(token, lentilSalad, newer);
        // A meal that did not come from a recipe is not a cooked recipe.
        logMeal(token, chicken, "2026-06-12T12:00:00Z");

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[0].lastCookedAt").value("2026-06-11T18:00:00Z"))
                .andExpect(jsonPath("$.content[1].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[1].lastCookedAt").value("2026-06-11T17:00:00Z"));
        // Cards of the other scopes carry no cooking time.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "library"))
                .andExpect(jsonPath("$.content[0].lastCookedAt").doesNotExist());
    }

    @Test
    void recentSaysWhetherTheTimeCameFromACookOrFromAMealLoggedFromTheRecipe() throws Exception {
        String token = tokenFor("recent-d@example.com");
        // The salad was only logged, the bowl was cooked after it was logged, the porridge was cooked and
        // then logged later.
        logRecipeNow(token, lentilSalad, Instant.parse("2026-06-11T18:00:00Z"));
        logRecipeNow(token, chickenBowl, Instant.parse("2026-06-10T18:00:00Z"));
        cook(token, chickenBowl, cookJson("1", "2026-06-10T19:00:00Z")).andExpect(status().isCreated());
        cook(token, milkPorridge, cookJson("1", "2026-06-09T19:00:00Z")).andExpect(status().isCreated());
        logRecipeNow(token, milkPorridge, Instant.parse("2026-06-09T20:00:00Z"));

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "recent"))
                .andExpect(jsonPath("$.content[0].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[0].lastActivity").value("LOGGED"))
                .andExpect(jsonPath("$.content[1].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[1].lastActivity").value("COOKED"))
                .andExpect(jsonPath("$.content[1].lastCookedAt").value("2026-06-10T19:00:00Z"))
                .andExpect(jsonPath("$.content[2].name").value("Milk porridge"))
                .andExpect(jsonPath("$.content[2].lastActivity").value("LOGGED"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "library"))
                .andExpect(jsonPath("$.content[0].lastActivity").doesNotExist());
    }

    @Test
    void recentIsPrivateLeavesOutDeletedMealsAndRecipesAndStopsAtTen() throws Exception {
        String token = tokenFor("recent-b@example.com");
        String other = tokenFor("recent-c@example.com");
        String removed = logRecipeNow(token, milkPorridge, Instant.parse("2026-06-11T08:00:00Z"));
        logRecipeNow(token, chickenBowl, Instant.parse("2026-06-11T12:00:00Z"));
        logRecipeNow(token, lentilSalad, Instant.parse("2026-06-11T13:00:00Z"));
        mockMvc.perform(delete(BASE + "/meals/{id}", removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        jdbcTemplate.update("update recipes set deleted_at = current_timestamp where recipe_id = ?", lentilSalad);

        // Only the chicken bowl is left: the porridge meal is deleted and the lentil salad recipe is deleted.
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "recent"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + other).param("scope", "recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(RECIPES).param("scope", "recent")).andExpect(status().isUnauthorized());
        for (int i = 0; i < 12; i++) {
            UUID extra = systemRecipe("Extra dish " + i, "Misc", "1", true, ingredient(rice, "100"));
            logRecipeNow(other, extra, Instant.parse("2026-06-11T12:00:00Z").plusSeconds(i));
        }
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + other).param("scope", "recent"))
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.content[0].name").value("Extra dish 11"));
    }

    private org.springframework.test.web.servlet.ResultActions cook(String token, UUID recipeId, String body)
            throws Exception {
        return mockMvc.perform(post(RECIPES + "/" + recipeId + "/cooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(body));
    }

    private static String cookJson(String servings, String finishedAt) {
        return "{\"clientCookId\": \"" + UUID.randomUUID() + "\", \"servings\": " + servings
                + (finishedAt == null ? "" : ", \"finishedAt\": \"" + finishedAt + "\"") + "}";
    }

    @Test
    void aCookIsRecordedOnceAndARetryWithTheSameClientIdAnswersWithTheSameRecord() throws Exception {
        String token = tokenFor("cook-a@example.com");
        UUID clientId = UUID.randomUUID();
        String body = "{\"clientCookId\": \"" + clientId + "\", \"servings\": 2, "
                + "\"startedAt\": \"2026-06-11T17:00:00Z\", \"finishedAt\": \"2026-06-11T18:00:00Z\"}";

        String first = cook(token, chickenBowl, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipeId").value(chickenBowl.toString()))
                .andExpect(jsonPath("$.servings").value(2.0))
                .andExpect(jsonPath("$.startedAt").value("2026-06-11T17:00:00Z"))
                .andExpect(jsonPath("$.finishedAt").value("2026-06-11T18:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        String second = cook(token, chickenBowl, body).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Assertions.assertEquals(JsonPath.<String>read(first, "$.cookId"), JsonPath.<String>read(second, "$.cookId"));
        Assertions.assertEquals(1, jdbcTemplate.queryForObject("select count(*) from recipe_cooks", Integer.class));
        // Without a finishedAt the cook ends now.
        cook(token, lentilSalad, cookJson("1", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.startedAt").doesNotExist());
    }

    @Test
    void cookServingsMustBeBetweenAQuarterAndFifty() throws Exception {
        String token = tokenFor("cook-b@example.com");

        cook(token, chickenBowl, cookJson("0.24", null)).andExpect(status().isBadRequest());
        cook(token, chickenBowl, cookJson("50.01", null)).andExpect(status().isBadRequest());
        cook(token, chickenBowl, cookJson("0", null)).andExpect(status().isBadRequest());
        cook(token, chickenBowl, "{\"clientCookId\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest());
        cook(token, chickenBowl, cookJson("0.25", null)).andExpect(status().isCreated());
        cook(token, chickenBowl, cookJson("50", null)).andExpect(status().isCreated());
    }

    @Test
    void aCookCannotFinishInTheFutureBeyondFiveMinutesOfSkew() throws Exception {
        String token = tokenFor("cook-c@example.com");

        cook(token, chickenBowl, cookJson("1", Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isBadRequest());
        cook(token, chickenBowl, cookJson("1", Instant.now().plusSeconds(120).toString()))
                .andExpect(status().isCreated());
        cook(token, chickenBowl, "{\"clientCookId\": \"" + UUID.randomUUID() + "\", \"servings\": 1, "
                + "\"startedAt\": \"2026-06-11T19:00:00Z\", \"finishedAt\": \"2026-06-11T18:00:00Z\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void aCookNeedsARecipeTheCallerCanSee() throws Exception {
        String token = tokenFor("cook-d@example.com");

        cook(token, unpublished, cookJson("1", null)).andExpect(status().isNotFound());
        cook(token, UUID.randomUUID(), cookJson("1", null)).andExpect(status().isNotFound());
        mockMvc.perform(post(RECIPES + "/" + chickenBowl + "/cooks").contentType(MediaType.APPLICATION_JSON)
                .content(cookJson("1", null))).andExpect(status().isUnauthorized());
        Assertions.assertEquals(0, jdbcTemplate.queryForObject("select count(*) from recipe_cooks", Integer.class));
    }

    @Test
    void onlyTheOwnerCanDeleteACookAndItIsMarkedNotRemoved() throws Exception {
        String token = tokenFor("cook-e@example.com");
        String other = tokenFor("cook-f@example.com");
        String cookId = JsonPath.read(cook(token, chickenBowl, cookJson("1", null)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.cookId");

        mockMvc.perform(delete(RECIPES + "/cooks/" + cookId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(RECIPES + "/cooks/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(RECIPES + "/cooks/" + cookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(RECIPES + "/cooks/" + cookId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        Assertions.assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from recipe_cooks where deleted_at is not null", Integer.class));
    }

    @Test
    void theDetailCountsTheCallersCooksAndSoftDeleteTakesThemBackOutOfCountAndRecent() throws Exception {
        String token = tokenFor("cook-g@example.com");
        String other = tokenFor("cook-h@example.com");
        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.cookCount").value(0))
                .andExpect(jsonPath("$.lastCookedAt").doesNotExist());
        cook(token, chickenBowl, cookJson("1", "2026-06-10T18:00:00Z")).andExpect(status().isCreated());
        String newest = JsonPath.read(cook(token, chickenBowl, cookJson("2", "2026-06-11T18:00:00Z"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.cookId");
        cook(other, chickenBowl, cookJson("1", "2026-06-12T18:00:00Z")).andExpect(status().isCreated());

        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.cookCount").value(2))
                .andExpect(jsonPath("$.lastCookedAt").value("2026-06-11T18:00:00Z"));
        mockMvc.perform(delete(RECIPES + "/cooks/" + newest).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(RECIPES + "/" + chickenBowl).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.cookCount").value(1))
                .andExpect(jsonPath("$.lastCookedAt").value("2026-06-10T18:00:00Z"));
        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "recent"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].lastCookedAt").value("2026-06-10T18:00:00Z"));
    }

    @Test
    void recentMergesRecordedCooksWithMealsFromARecipeAndShowsTheNewestOfBoth() throws Exception {
        String token = tokenFor("cook-i@example.com");
        // The lentil salad was only logged as a meal, the chicken bowl was logged and cooked, and the milk
        // porridge was cooked before the meal of the chicken bowl.
        logRecipeNow(token, lentilSalad, Instant.parse("2026-06-09T12:00:00Z"));
        logRecipeNow(token, chickenBowl, Instant.parse("2026-06-10T12:00:00Z"));
        cook(token, chickenBowl, cookJson("1", "2026-06-12T12:00:00Z")).andExpect(status().isCreated());
        cook(token, milkPorridge, cookJson("1", "2026-06-11T12:00:00Z")).andExpect(status().isCreated());

        mockMvc.perform(get(RECIPES).header("Authorization", "Bearer " + token).param("scope", "recent"))
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].name").value("Chicken rice bowl"))
                .andExpect(jsonPath("$.content[0].lastCookedAt").value("2026-06-12T12:00:00Z"))
                .andExpect(jsonPath("$.content[1].name").value("Milk porridge"))
                .andExpect(jsonPath("$.content[2].name").value("Lentil salad"))
                .andExpect(jsonPath("$.content[2].lastCookedAt").value("2026-06-09T12:00:00Z"));
    }

    private String fromRecipeJson(String servings) {
        return "{\"servings\": " + servings + ", \"mealType\": \"DINNER\", "
                + "\"consumedAt\": \"2026-06-11T18:00:00Z\"}";
    }

    private RecipeIngredientEntity ingredient(Seeded food, String grams) {
        RecipeIngredientEntity ingredient = new RecipeIngredientEntity();
        ingredient.setIngredientId(UUID.randomUUID());
        ingredient.setFoodCatalogItemId(food.foodId);
        ingredient.setQuantity(BigDecimal.ONE);
        ingredient.setGrams(new BigDecimal(grams));
        return ingredient;
    }

    private UUID systemRecipe(String name, String category, String servings, boolean published,
                              RecipeIngredientEntity... ingredients) {
        RecipeEntity recipe = new RecipeEntity();
        recipe.setRecipeId(UUID.randomUUID());
        recipe.setScope(RecipeScope.SYSTEM);
        recipe.setName(name);
        recipe.setServings(new BigDecimal(servings));
        recipe.setSource("THEMEALDB");
        recipe.setSourceRef("meal-" + name.split(" ")[0].toLowerCase());
        recipe.setSourceUrl("https://source.example.test/" + name.split(" ")[0].toLowerCase());
        recipe.setImageUrl("https://images.example.test/" + name.split(" ")[0].toLowerCase() + ".jpg");
        recipe.setImageCredit("Photo from TheMealDB");
        recipe.setCategory(category);
        recipe.setCuisine("British");
        recipe.setPublished(published);
        recipeRepository.save(recipe);
        int order = 0;
        for (RecipeIngredientEntity ingredient : ingredients) {
            ingredient.setRecipeId(recipe.getRecipeId());
            ingredient.setSortOrder(order++);
            ingredient.setDisplayText(null);
            ingredientRepository.save(ingredient);
        }
        for (int number = 1; number <= 2; number++) {
            RecipeStepEntity step = new RecipeStepEntity();
            step.setStepId(UUID.randomUUID());
            step.setRecipeId(recipe.getRecipeId());
            step.setStepNumber(number);
            step.setText(number == 1 ? "Cook the rice." : "Serve it hot.");
            stepRepository.save(step);
        }
        return recipe.getRecipeId();
    }
}
