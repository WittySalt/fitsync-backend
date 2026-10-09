package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import com.fitsync.nutrition.repository.RecipeFavoriteRepository;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Covers GET /me/export, the nutrition part of the personal data export: it holds the caller's whole history
 * of meals with their items, recipes with ingredients and steps, cooks, templates, favourites, custom foods,
 * owned foods and water logs, never another person's rows, and an empty account gets empty lists.
 */
class NutritionDataExportApiTests extends DiaryApiTestBase {

    private static final String EXPORT = BASE + "/me/export";

    @Autowired
    private RecipeFavoriteRepository recipeFavoriteRepository;

    private Seeded chicken;
    private Seeded rice;

    @BeforeEach
    void seedFoods() {
        chicken = seedFood("Chicken breast", "MEAT_POULTRY", false, "g", 165, "1", null, mapOf(), profile ->
                profile.setProteinGrams(new BigDecimal("31"))).addServing("100 g", "g", "100", "100");
        rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf())
                .addServing("100 g", "g", "100", "100");
    }

    @Test
    void exportHoldsTheCallersOwnDataAndNothingOfAnotherPerson() throws Exception {
        String alice = tokenFor("export-alice@example.com");
        String bob = tokenFor("export-bob@example.com");

        String aliceLunch = logMeal(alice, "LUNCH", chicken, "2026-06-10T12:00:00Z");
        String aliceDinner = logMeal(alice, "DINNER", rice, "2026-06-11T19:00:00Z");
        String aliceDeleted = logMeal(alice, "SNACK", rice, "2026-06-12T15:00:00Z");
        mockMvc.perform(delete(BASE + "/meals/" + aliceDeleted).header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        String bobMeal = logMeal(bob, "LUNCH", chicken, "2026-06-10T12:30:00Z");

        String aliceRecipe = createRecipe(alice, "Alice chicken rice");
        String bobRecipe = createRecipe(bob, "Bob chicken rice");
        String aliceCook = cook(alice, aliceRecipe, "2026-06-11T18:00:00Z");
        String bobCook = cook(bob, bobRecipe, "2026-06-11T18:30:00Z");

        mockMvc.perform(post(BASE + "/water").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + alice)
                        .content("{ \"amountMl\": 250, \"loggedAt\": \"2026-06-10T09:00:00Z\" }"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/water").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + bob)
                        .content("{ \"amountMl\": 500, \"loggedAt\": \"2026-06-10T09:00:00Z\" }"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/custom-foods").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + alice)
                        .content("""
                                {"name": "Alice oats", "servingSize": 250, "servingUnit": "g", "calories": 410}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/foods/" + rice.foodId + "/favorite").header("Authorization", "Bearer " + alice))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post(BASE + "/foods/" + chicken.foodId + "/favorite").header("Authorization", "Bearer " + bob))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(post(BASE + "/meal-templates").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + alice)
                        .content("{\"name\": \"Alice breakfast\", \"mealType\": \"BREAKFAST\", \"fromMealId\": \""
                                + aliceLunch + "\"}"))
                .andExpect(status().isCreated());
        String clone = JsonPath.read(mockMvc.perform(post(BASE + "/foods/" + chicken.foodId + "/clone")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.foodId");
        saveRecipe(userIdOf(alice), aliceRecipe);
        saveRecipe(userIdOf(bob), bobRecipe);

        String body = mockMvc.perform(get(EXPORT).header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("nutrition-service"))
                .andExpect(jsonPath("$.exportedAt").isNotEmpty())
                .andExpect(jsonPath("$.meals.length()").value(2))
                .andExpect(jsonPath("$.meals[*].mealId", Matchers.containsInAnyOrder(aliceLunch, aliceDinner)))
                .andExpect(jsonPath("$.meals[?(@.mealId == '" + aliceLunch + "')].items[0].foodName")
                        .value("Chicken breast"))
                .andExpect(jsonPath("$.meals[?(@.mealId == '" + aliceDinner + "')].items[0].foodName")
                        .value("White rice"))
                .andExpect(jsonPath("$.recipes.length()").value(1))
                .andExpect(jsonPath("$.recipes[0].id").value(aliceRecipe))
                .andExpect(jsonPath("$.recipes[0].name").value("Alice chicken rice"))
                .andExpect(jsonPath("$.recipes[0].ingredients.length()").value(2))
                .andExpect(jsonPath("$.recipes[0].steps.length()").value(1))
                .andExpect(jsonPath("$.recipeCooks.length()").value(1))
                .andExpect(jsonPath("$.recipeCooks[0].cookId").value(aliceCook))
                .andExpect(jsonPath("$.recipeCooks[0].recipeId").value(aliceRecipe))
                .andExpect(jsonPath("$.waterLogs.length()").value(1))
                .andExpect(jsonPath("$.waterLogs[0].amountMl").value(250))
                .andExpect(jsonPath("$.customFoods.length()").value(1))
                .andExpect(jsonPath("$.customFoods[0].name").value("Alice oats"))
                .andExpect(jsonPath("$.foodFavorites.length()").value(1))
                .andExpect(jsonPath("$.foodFavorites[0].foodId").value(rice.foodId.toString()))
                .andExpect(jsonPath("$.mealTemplates.length()").value(1))
                .andExpect(jsonPath("$.mealTemplates[0].name").value("Alice breakfast"))
                .andExpect(jsonPath("$.mealTemplates[0].items[0].foodName").value("Chicken breast"))
                .andExpect(jsonPath("$.recipeFavorites.length()").value(1))
                .andExpect(jsonPath("$.recipeFavorites[0].recipeId").value(aliceRecipe))
                .andExpect(jsonPath("$.ownedFoods.length()").value(1))
                .andExpect(jsonPath("$.ownedFoods[0].foodId").value(clone))
                .andReturn().getResponse().getContentAsString();

        // None of Bob's ids may appear anywhere in Alice's export, and neither may her deleted meal.
        for (String foreign : List.of(bobMeal, bobRecipe, bobCook, aliceDeleted, userIdOf(bob))) {
            Assertions.assertFalse(body.contains(foreign), "export leaked " + foreign);
        }
        List<Integer> waterAmounts = JsonPath.read(body, "$.waterLogs[*].amountMl");
        Assertions.assertEquals(List.of(250), waterAmounts);
    }

    @Test
    void anEmptyAccountGetsEmptyLists() throws Exception {
        String token = tokenFor("export-empty@example.com");
        // Another person's data must not show up in an empty account either.
        logMeal(tokenFor("export-someone@example.com"), chicken, "2026-06-10T12:00:00Z");

        mockMvc.perform(get(EXPORT).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("nutrition-service"))
                .andExpect(jsonPath("$.meals.length()").value(0))
                .andExpect(jsonPath("$.recipes.length()").value(0))
                .andExpect(jsonPath("$.recipeCooks.length()").value(0))
                .andExpect(jsonPath("$.mealTemplates.length()").value(0))
                .andExpect(jsonPath("$.foodFavorites.length()").value(0))
                .andExpect(jsonPath("$.recipeFavorites.length()").value(0))
                .andExpect(jsonPath("$.customFoods.length()").value(0))
                .andExpect(jsonPath("$.ownedFoods.length()").value(0))
                .andExpect(jsonPath("$.waterLogs.length()").value(0));
    }

    @Test
    void exportWithoutATokenIsRefused() throws Exception {
        mockMvc.perform(get(EXPORT)).andExpect(status().isUnauthorized());
    }

    /** Saves a recipe for a person directly, because the API only saves shared library recipes. */
    private void saveRecipe(String userId, String recipeId) {
        RecipeFavoriteEntity favorite = new RecipeFavoriteEntity();
        favorite.setFavoriteId(UUID.randomUUID());
        favorite.setUserId(UUID.fromString(userId));
        favorite.setRecipeId(UUID.fromString(recipeId));
        favorite.setSavedAt(Instant.parse("2026-06-12T08:00:00Z"));
        recipeFavoriteRepository.save(favorite);
    }

    private String createRecipe(String token, String name) throws Exception {
        String request = """
                {
                  "name": "%s",
                  "servings": 2,
                  "ingredients": [
                    {"foodCatalogItemId": "%s", "quantity": 2},
                    {"foodCatalogItemId": "%s", "quantity": 3}
                  ],
                  "steps": [{"text": "Cook everything together."}]
                }
                """.formatted(name, chicken.foodId, rice.foodId);
        String body = mockMvc.perform(post(BASE + "/recipes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.recipeId");
    }

    private String cook(String token, String recipeId, String finishedAt) throws Exception {
        ResultActions result = mockMvc.perform(post(BASE + "/recipes/" + recipeId + "/cooks")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"clientCookId\": \"" + UUID.randomUUID() + "\", \"servings\": 2, \"finishedAt\": \""
                        + finishedAt + "\"}"));
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.cookId");
    }
}
