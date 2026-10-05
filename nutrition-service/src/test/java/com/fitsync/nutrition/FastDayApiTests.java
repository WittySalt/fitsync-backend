package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Covers fasted days: a FAST meal holds no foods, cannot join a day that has meals, is replaced when a meal is
 * logged on its day, and counts as a logged day with no energy in the day and range summaries.
 */
class FastDayApiTests extends DiaryApiTestBase {

    private static final String DAY = "2026-06-11";
    private static final String NOON = DAY + "T12:00:00Z";

    private ResultActions postMeal(String token, String type, String consumedAt, String items) throws Exception {
        return mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"mealType\": \"" + type + "\", \"source\": \"MANUAL\", \"consumedAt\": \"" + consumedAt
                        + "\", \"items\": " + items + "}"));
    }

    private String fast(String token, String consumedAt) throws Exception {
        String body = postMeal(token, "FAST", consumedAt, "[]")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.mealId");
    }

    private long outboxCount(String eventType) {
        Long count = jdbcTemplate.queryForObject("select count(*) from outbox_events where event_type = ?",
                Long.class, eventType);
        return count == null ? 0 : count;
    }

    @Test
    void aFastHasNoItemsAndAFastWithFoodsIsRefused() throws Exception {
        String token = tokenFor("fast-a@example.com");
        Seeded rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile -> { })
                .addServing("100 g", "g", "100", "100");

        postMeal(token, "FAST", NOON, "[]")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("FAST"))
                .andExpect(jsonPath("$.items.length()").value(0));

        String item = "[{\"foodCatalogItemId\": \"" + rice.foodId + "\", \"foodServingId\": \""
                + rice.servingIds.get("100 g") + "\", \"quantity\": 1}]";
        postMeal(token, "FAST", "2026-06-12T12:00:00Z", item)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A fasted day has no foods"));
    }

    @Test
    void everyOtherMealStillNeedsFoods() throws Exception {
        String token = tokenFor("fast-b@example.com");
        postMeal(token, "LUNCH", NOON, "[]")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void aFastOnADayThatAlreadyHasMealsIsAConflict() throws Exception {
        String token = tokenFor("fast-c@example.com");
        Seeded rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile -> { })
                .addServing("100 g", "g", "100", "100");
        logMeal(token, "LUNCH", rice, DAY + "T05:00:00Z");

        postMeal(token, "FAST", DAY + "T20:00:00Z", "[]")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Meals are already logged on this day"));
        // The neighbouring day is free.
        postMeal(token, "FAST", "2026-06-12T12:00:00Z", "[]").andExpect(status().isCreated());
        // And so is the same day for somebody else.
        postMeal(tokenFor("fast-c-other@example.com"), "FAST", NOON, "[]").andExpect(status().isCreated());
    }

    @Test
    void loggingAMealOnAFastedDayDeletesTheFastInTheSameTransactionAndPublishesMealDeleted() throws Exception {
        String token = tokenFor("fast-d@example.com");
        Seeded rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile -> { })
                .addServing("100 g", "g", "100", "100");
        String fastId = fast(token, NOON);
        String neighbour = fast(token, "2026-06-12T12:00:00Z");
        long deletedBefore = outboxCount("MealDeleted");

        logMeal(token, "DINNER", rice, DAY + "T19:00:00Z");

        mockMvc.perform(get(BASE + "/meals/{id}", fastId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/meals/{id}", neighbour).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        Assertions.assertEquals(deletedBefore + 1, outboxCount("MealDeleted"));
        // The row stays in the table, marked as deleted.
        Assertions.assertEquals(1L, jdbcTemplate.queryForObject(
                "select count(*) from meal_logs where meal_id = ? and deleted_at is not null", Long.class,
                java.util.UUID.fromString(fastId)));
        mockMvc.perform(get(BASE + "/summary/daily").param("date", DAY).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.mealCount").value(1))
                .andExpect(jsonPath("$.totalCalories").value(130));
    }

    @Test
    void aFastedDayCountsAsLoggedWithNoEnergyInTheDayAndRangeSummaries() throws Exception {
        String token = tokenFor("fast-e@example.com");
        fast(token, NOON);

        mockMvc.perform(get(BASE + "/summary/daily").param("date", DAY).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealCount").value(1))
                .andExpect(jsonPath("$.totalCalories").value(0))
                .andExpect(jsonPath("$.totalProteinGrams").value(0));
        mockMvc.perform(get(BASE + "/summary/range").param("from", "2026-06-10").param("to", "2026-06-12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.days[0].logged").value(false))
                .andExpect(jsonPath("$.days[1].logged").value(true))
                .andExpect(jsonPath("$.days[1].mealCount").value(1))
                .andExpect(jsonPath("$.days[1].totalCalories").value(0))
                .andExpect(jsonPath("$.days[2].logged").value(false));
        mockMvc.perform(get(BASE + "/nutrients/summary").param("from", "2026-06-10").param("to", "2026-06-12")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.daysLogged").value(1));
    }

    @Test
    void aMealCannotBeEditedIntoAFastOnADayWithOtherMealsOrWithFoods() throws Exception {
        String token = tokenFor("fast-f@example.com");
        Seeded rice = seedFood("White rice", "GRAINS", false, "g", 130, "0.1", null, mapOf(), profile -> { })
                .addServing("100 g", "g", "100", "100");
        String first = logMeal(token, "LUNCH", rice, DAY + "T05:00:00Z");
        logMeal(token, "DINNER", rice, DAY + "T11:00:00Z");

        mockMvc.perform(put(BASE + "/meals/{id}", first).contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"mealType\": \"FAST\", \"source\": \"MANUAL\", \"consumedAt\": \"" + NOON
                                + "\", \"items\": []}"))
                .andExpect(status().isConflict());
    }
}
