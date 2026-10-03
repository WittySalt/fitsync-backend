package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** GET /api/v1/nutrition/progress/food, the nutrition half of the Progress Food page (contract section 3.1). */
class NutritionProgressApiTests extends DiaryApiTestBase {

    private static final String PATH = BASE + "/progress/food";

    @BeforeEach
    @AfterEach
    void clearRecipes() {
        jdbcTemplate.update("delete from recipe_cooks");
        jdbcTemplate.update("delete from recipe_steps");
        jdbcTemplate.update("delete from recipe_ingredients");
        jdbcTemplate.update("delete from recipes");
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(PATH).param("from", "2026-06-01").param("to", "2026-06-30"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsFromAfterToPeriodsOver370DaysAndMissingDates() throws Exception {
        String token = tokenFor("limits-progress@example.com");
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("from", "2026-07-01").param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must not be after to"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("from", "2025-06-25").param("to", "2026-06-30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The period must not exceed 370 days"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token).param("from", "2026-06-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("from", "2025-06-26").param("to", "2026-06-30"))
                .andExpect(status().isOk());
    }

    @Test
    void mealTimesAreTheMedianAndQuartilesInTheCallersZone() throws Exception {
        String token = tokenFor("times@example.com");
        Seeded oats = seedFood("Oats", "GRAINS", false, "g", 380, "1", null, Map.of())
                .addServing("40 g", "g", "40", "40");
        // Breakfasts at 07:00, 07:30, 08:00 and 09:00 in Singapore (UTC+8).
        logMeal(token, "BREAKFAST", oats, "2026-06-01T23:00:00Z");
        logMeal(token, "BREAKFAST", oats, "2026-06-02T23:30:00Z");
        logMeal(token, "BREAKFAST", oats, "2026-06-04T00:00:00Z");
        logMeal(token, "BREAKFAST", oats, "2026-06-05T01:00:00Z");
        logMeal(token, "DINNER", oats, "2026-06-05T11:15:00Z");

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("X-Timezone", "Asia/Singapore")
                        .param("from", "2026-06-01").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealTimes.length()").value(2))
                .andExpect(jsonPath("$.mealTimes[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.mealTimes[0].count").value(4))
                .andExpect(jsonPath("$.mealTimes[0].medianMinuteOfDay").value(465))
                .andExpect(jsonPath("$.mealTimes[0].p25").value(443))
                .andExpect(jsonPath("$.mealTimes[0].p75").value(495))
                .andExpect(jsonPath("$.mealTimes[1].mealType").value("DINNER"))
                .andExpect(jsonPath("$.mealTimes[1].medianMinuteOfDay").value(19 * 60 + 15));

        // Read in UTC the same breakfasts sit at 23:00, 23:30, 00:00 and 01:00, and the first falls on 1 June.
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-02").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealTimes[0].count").value(3))
                .andExpect(jsonPath("$.mealTimes[0].medianMinuteOfDay").value(60));
    }

    @Test
    void topFoodsCooksAndOnlyTheCallersRows() throws Exception {
        String token = tokenFor("foods@example.com");
        Seeded oats = seedFood("Oats", "GRAINS", false, "g", 380, "1", null, Map.of())
                .addServing("40 g", "g", "40", "40");
        Seeded milk = seedFood("Milk", "DAIRY", false, "ml", 60, "1", "88", Map.of())
                .addServing("1 cup", "cup", "1", "240");
        logMeal(token, "BREAKFAST", oats, "2026-06-01T07:00:00Z");
        logMeal(token, "BREAKFAST", oats, "2026-06-02T07:00:00Z");
        logMeal(token, "BREAKFAST", oats, "2026-06-03T07:00:00Z");
        logMeal(token, "BREAKFAST", milk, "2026-06-03T07:05:00Z");
        // Outside the period, and another person's meal, both left out.
        logMeal(token, "BREAKFAST", milk, "2026-05-20T07:00:00Z");
        String other = tokenFor("someone-else@example.com");
        logMeal(other, "BREAKFAST", milk, "2026-06-04T07:00:00Z");
        logMeal(other, "BREAKFAST", milk, "2026-06-05T07:00:00Z");

        UUID userId = UUID.fromString(userIdOf(token));
        String created = mockMvc.perform(post(BASE + "/recipes").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Overnight oats\", \"servings\": 1, \"ingredients\": [{\"foodCatalogItemId\": \""
                                + oats.foodId + "\", \"quantity\": 1, \"grams\": 40}], \"steps\": [{\"text\": \"Soak the oats.\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> recipe = Map.of("recipe_id", UUID.fromString(JsonPath.read(created, "$.recipeId")),
                "name", "Overnight oats");
        cook(userId, (UUID) recipe.get("recipe_id"), "2026-06-10T18:00:00Z", false);
        cook(userId, (UUID) recipe.get("recipe_id"), "2026-06-20T18:00:00Z", false);
        cook(userId, (UUID) recipe.get("recipe_id"), "2026-06-25T18:00:00Z", true);
        cook(UUID.fromString(userIdOf(other)), (UUID) recipe.get("recipe_id"), "2026-06-26T18:00:00Z", false);

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-01").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topFoods.length()").value(2))
                .andExpect(jsonPath("$.topFoods[0].name").value("Oats"))
                .andExpect(jsonPath("$.topFoods[0].times").value(3))
                .andExpect(jsonPath("$.topFoods[0].foodCatalogItemId").value(oats.foodId.toString()))
                .andExpect(jsonPath("$.topFoods[0].categoryCode").value("GRAINS"))
                .andExpect(jsonPath("$.topFoods[1].name").value("Milk"))
                .andExpect(jsonPath("$.topFoods[1].times").value(1))
                .andExpect(jsonPath("$.cooks.count").value(2))
                .andExpect(jsonPath("$.cooks.last.name").value(recipe.get("name")))
                .andExpect(jsonPath("$.cooks.last.finishedAt").value("2026-06-20T18:00:00Z"))
                .andExpect(jsonPath("$.recentWindow.from").value("2026-06-01"))
                .andExpect(jsonPath("$.habits.loggedDays").value(3));
    }

    @Test
    void lowestNutrientsComeFromTheNutrientSummaryOfTheLast31Days() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("lowest@example.com");
        Seeded spinach = seedFood("Spinach", "VEGETABLES", false, "g", 23, "0.1", null,
                mapOf("VIT_C", "28", "IRON", "2.7", "CALCIUM", "99", "MAGNESIUM", "79"))
                .addServing("100 g", "g", "100", "100");
        logMeal(token, "LUNCH", spinach, "2026-06-10T12:00:00Z");

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("X-Timezone", "UTC")
                        .param("from", "2026-03-01").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentWindow.from").value("2026-05-12"))
                .andExpect(jsonPath("$.recentWindow.to").value("2026-06-11"))
                .andExpect(jsonPath("$.lowestNutrients.length()").value(3))
                .andExpect(jsonPath("$.lowestNutrients[0].code").value("CALCIUM"))
                .andExpect(jsonPath("$.lowestNutrients[0].share").value(0.099))
                .andExpect(jsonPath("$.lowestNutrients[1].code").value("MAGNESIUM"));

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + tokenFor("nobody@example.com"))
                        .param("from", "2026-06-01").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowestNutrients.length()").value(0))
                .andExpect(jsonPath("$.mealTimes.length()").value(0))
                .andExpect(jsonPath("$.topFoods.length()").value(0))
                .andExpect(jsonPath("$.cooks.count").value(0))
                .andExpect(jsonPath("$.habits.fibreMetDays").value(0));
    }

    private void cook(UUID userId, UUID recipeId, String finishedAt, boolean deleted) {
        Timestamp finished = Timestamp.from(Instant.parse(finishedAt));
        jdbcTemplate.update("""
                insert into recipe_cooks (cook_id, user_id, recipe_id, client_cook_id, servings, finished_at, created_at,
                                          deleted_at)
                values (?, ?, ?, ?, 1, ?, ?, ?)
                """, UUID.randomUUID(), userId, recipeId, UUID.randomUUID(), finished, finished,
                deleted ? finished : null);
    }
}
