package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers the food side of the Diary contract: the food group filter on search, recent and frequent foods, and the
 * plate group and water fields on search results, logged foods and food detail.
 */
class NutritionDiaryFoodsApiTests extends DiaryApiTestBase {

    private Seeded oats;
    private Seeded cola;
    private Seeded ham;
    private Seeded gravy;
    private Seeded beer;

    @BeforeEach
    void seedFoods() {
        oats = seedFood("Rolled oats", "GRAINS", false, "g", 380, "1.2", null, mapOf())
                .addServing("100 g", "g", "100", "100");
        cola = seedFood("Cola", "BEVERAGES", true, "ml", 42, "0", "89", mapOf())
                .addServing("1 cup", "cup", "1", "248");
        ham = seedFood("Cured ham", "MEAT_POULTRY", false, "g", 145, "3.0", null, mapOf())
                .addServing("100 g", "g", "100", "100");
        // PREPARED is a category the production seed adds; the test schema has no row for it.
        gravy = seedFood("Gravy", "PREPARED", false, "g", 60, "1", null, mapOf())
                .addServing("100 g", "g", "100", "100");
        beer = seedFood("Lager", "BEVERAGES", false, "ml", 43, "0", "92", mapOf("ALCOHOL", "3.9"))
                .addServing("1 cup", "cup", "1", "248");
    }

    @Test
    void searchCanKeepOnlyOnePlateGroup() throws Exception {
        String token = tokenFor("search-group@example.com");

        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "DRINKS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.displayName == 'Cola')].foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$.content[?(@.displayName == 'Cola')].countsTowardWater").value(true))
                .andExpect(jsonPath("$.content[?(@.displayName == 'Lager')].countsTowardWater").value(false));
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "protein"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Cured ham"))
                .andExpect(jsonPath("$.content[0].foodGroup").value("PROTEIN"));
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "OTHER"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Gravy"))
                .andExpect(jsonPath("$.content[0].foodGroup").value("OTHER"));
    }

    @Test
    void searchWithoutAFilterReturnsEveryGroupAndAnUnknownGroupIsRejected() throws Exception {
        String token = tokenFor("search-all@example.com");

        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.content.length()").value(5));
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "BANANA"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/foods/search").param("foodGroup", "DRINKS"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recentAndFrequentFoodsCanBeFilteredByGroupAndCarryTheNewFields() throws Exception {
        String token = tokenFor("recent-group@example.com");
        logMeal(token, "BREAKFAST", oats, "2026-06-11T08:00:00Z");
        logMeal(token, "SNACK", cola, "2026-06-11T10:00:00Z");
        logMeal(token, "SNACK", cola, "2026-06-11T15:00:00Z");

        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "DRINKS"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].foodName").value("Cola"))
                .andExpect(jsonPath("$[0].timesLogged").value(2))
                .andExpect(jsonPath("$[0].foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$[0].countsTowardWater").value(true));
        mockMvc.perform(get(BASE + "/foods/frequent").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "drinks"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].foodName").value("Cola"));
        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.foodName == 'Rolled oats')].foodGroup").value("GRAINS"))
                .andExpect(jsonPath("$[?(@.foodName == 'Rolled oats')].countsTowardWater").value(false));
        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "OTHER"))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + token)
                        .param("foodGroup", "BANANA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void recentFoodsByGroupAreScopedToTheCaller() throws Exception {
        logMeal(tokenFor("drinker@example.com"), "SNACK", cola, "2026-06-11T10:00:00Z");
        String other = tokenFor("non-drinker@example.com");

        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + other)
                        .param("foodGroup", "DRINKS"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void foodDetailShowsGroupWaterAndAlcoholPerBasis() throws Exception {
        String token = tokenFor("detail@example.com");

        mockMvc.perform(get(BASE + "/foods/{id}", cola.foodId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$.countsTowardWater").value(true))
                .andExpect(jsonPath("$.waterGrams").value(89.0))
                .andExpect(jsonPath("$.alcoholGrams").value(Matchers.nullValue()));
        mockMvc.perform(get(BASE + "/foods/{id}", beer.foodId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.countsTowardWater").value(false))
                .andExpect(jsonPath("$.alcoholGrams").value(3.9));
        mockMvc.perform(get(BASE + "/foods/{id}", oats.foodId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.foodGroup").value("GRAINS"))
                .andExpect(jsonPath("$.waterGrams").value(Matchers.nullValue()));
    }

    @Test
    void aClonedFoodKeepsItsWaterFlagAndWaterContent() throws Exception {
        String token = tokenFor("clone@example.com");

        mockMvc.perform(post(BASE + "/foods/{id}/clone", cola.foodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.countsTowardWater").value(true))
                .andExpect(jsonPath("$.waterGrams").value(89.0))
                .andExpect(jsonPath("$.foodGroup").value("DRINKS"));
    }

    @Test
    void favoritesCarryTheGroupAndWaterFields() throws Exception {
        String token = tokenFor("favorites@example.com");
        mockMvc.perform(post(BASE + "/foods/{id}/favorite", cola.foodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/favorites").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$[0].countsTowardWater").value(true));
    }
}
