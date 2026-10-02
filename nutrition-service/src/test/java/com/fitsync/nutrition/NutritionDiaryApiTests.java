package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import com.fitsync.nutrition.messaging.AccountDeletionProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Covers the logging side of the Nutrition Diary batch 1 contract: the snapshot taken when a meal is logged and
 * the soft delete of meals.
 */
class NutritionDiaryApiTests extends DiaryApiTestBase {

    @Autowired
    private AccountDeletionProjectionService accountDeletionProjectionService;

    @Test
    void loggingACatalogueFoodSnapshotsGramsFoodGroupSaturatedFatAndNutrientDetails() throws Exception {
        String token = tokenFor("snap@example.com");
        Seeded banana = seedFood("Banana", "FRUITS", false, "g", 89, "0.1", null, Map.of("VIT_C", "8.7"));
        banana.addServing("1 medium", "medium", "1", "118");

        mockMvc.perform(post(BASE + "/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("BREAKFAST", "2026-06-11T08:00:00Z", banana, "1 medium", "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].categoryCode").value("FRUITS"))
                .andExpect(jsonPath("$.items[0].foodGroup").value("FRUIT"))
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(236.0))
                .andExpect(jsonPath("$.items[0].saturatedFatGrams").value(0.24))
                .andExpect(jsonPath("$.items[0].waterMl").doesNotExist())
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].nutrientCode").value("VIT_C"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].amount").value(20.53))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].unit").value("mg"));
    }

    @Test
    void aDrinkThatCountsTowardWaterRecordsItsWaterInMillilitres() throws Exception {
        String token = tokenFor("drink@example.com");
        Seeded juice = seedFood("Orange juice", "BEVERAGES", true, "ml", 45, "0", "88.3", Map.of());
        juice.addServing("1 cup", "cup", "1", "248");

        mockMvc.perform(post(BASE + "/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("SNACK", "2026-06-11T10:00:00Z", juice, "1 cup", "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].waterMl").value(219))
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(248.0))
                .andExpect(jsonPath("$.items[0].foodGroup").value("DRINKS"));
    }

    @Test
    void aFoodThatDoesNotCountTowardWaterHasNoWaterMlEvenWithWaterContent() throws Exception {
        String token = tokenFor("nowater@example.com");
        Seeded rice = seedFood("Cooked rice", "GRAINS", false, "g", 130, "0.1", "68.4", Map.of());
        rice.addServing("100 g", "g", "100", "100");

        mockMvc.perform(post(BASE + "/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("LUNCH", "2026-06-11T12:00:00Z", rice, "100 g", "1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].waterMl").doesNotExist())
                .andExpect(jsonPath("$.items[0].foodGroup").value("GRAINS"));
    }

    @Test
    void aQuickAddHasNoSnapshotBeyondWhatTheUserTyped() throws Exception {
        String token = tokenFor("quick@example.com");

        mockMvc.perform(post(BASE + "/meals/quick-add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Mystery snack\", \"calories\": 200, "
                                + "\"consumedAt\": \"2026-06-11T15:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].gramsConsumed").doesNotExist())
                .andExpect(jsonPath("$.items[0].foodGroup").doesNotExist())
                .andExpect(jsonPath("$.items[0].nutrientDetails.length()").value(0));
    }

    @Test
    void copyingAMealKeepsTheSnapshot() throws Exception {
        String token = tokenFor("copy@example.com");
        Seeded juice = seedFood("Apple juice", "BEVERAGES", true, "ml", 46, "0", "87.9", Map.of("VIT_C", "0.9"));
        juice.addServing("1 cup", "cup", "1", "248");
        String mealId = logMeal(token, juice, "2026-06-11T10:00:00Z");

        mockMvc.perform(post(BASE + "/meals/{id}/copy", mealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"consumedAt\": \"2026-06-12T10:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].waterMl").value(218))
                .andExpect(jsonPath("$.items[0].foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].nutrientCode").value("VIT_C"));
    }

    @Test
    void deletingAMealMarksItAndEveryReadLeavesItOut() throws Exception {
        String token = tokenFor("softdelete@example.com");
        Seeded banana = seedFood("Banana", "FRUITS", false, "g", 89, "0.1", null, Map.of());
        banana.addServing("100 g", "g", "100", "100");
        String kept = logMeal(token, banana, "2026-06-11T08:00:00Z");
        String removed = logMeal(token, banana, "2026-06-11T12:00:00Z");

        mockMvc.perform(delete(BASE + "/meals/{id}", removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/meals/{id}", removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/meals").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11T00:00:00Z").param("to", "2026-06-11T23:59:59Z"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].mealId").value(kept));
        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.mealCount").value(1))
                .andExpect(jsonPath("$.totalCalories").value(89));
        mockMvc.perform(get(BASE + "/foods/recent").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].timesLogged").value(1));
        mockMvc.perform(get(BASE + "/export").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11T00:00:00Z").param("to", "2026-06-11T23:59:59Z"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString().trim().split("\n"))
                        .hasSize(2));
        // Deleting it again is a not-found, like deleting any meal that is gone.
        mockMvc.perform(delete(BASE + "/meals/{id}", removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletingAMealKeepsTheRowAndStillPublishesMealDeleted() throws Exception {
        String token = tokenFor("softdelete-row@example.com");
        Seeded banana = seedFood("Banana", "FRUITS", false, "g", 89, "0.1", null, Map.of());
        banana.addServing("100 g", "g", "100", "100");
        String mealId = logMeal(token, banana, "2026-06-11T08:00:00Z");

        mockMvc.perform(delete(BASE + "/meals/{id}", mealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        Integer markedRows = jdbcTemplate.queryForObject(
                "select count(*) from meal_logs where meal_id = ? and deleted_at is not null", Integer.class,
                UUID.fromString(mealId));
        Integer events = jdbcTemplate.queryForObject(
                "select count(*) from outbox_events where event_type = 'MealDeleted'", Integer.class);
        assertThat(markedRows).isEqualTo(1);
        assertThat(events).isEqualTo(1);
    }

    @Test
    void anotherUserCannotDeleteAMeal() throws Exception {
        String owner = tokenFor("owner@example.com");
        String stranger = tokenFor("stranger@example.com");
        Seeded banana = seedFood("Banana", "FRUITS", false, "g", 89, "0.1", null, Map.of());
        banana.addServing("100 g", "g", "100", "100");
        String mealId = logMeal(owner, banana, "2026-06-11T08:00:00Z");

        mockMvc.perform(delete(BASE + "/meals/{id}", mealId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/meals/{id}", mealId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    void anAccountPurgeAlsoRemovesMealsThatWereAlreadyDeleted() throws Exception {
        String token = tokenFor("purge@example.com");
        UUID userId = UUID.fromString(userIdOf(token));
        Seeded banana = seedFood("Banana", "FRUITS", false, "g", 89, "0.1", null, Map.of("VIT_C", "8.7"));
        banana.addServing("100 g", "g", "100", "100");
        logMeal(token, banana, "2026-06-11T08:00:00Z");
        String removed = logMeal(token, banana, "2026-06-11T12:00:00Z");
        mockMvc.perform(delete(BASE + "/meals/{id}", removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        accountDeletionProjectionService.deleteUserData(userId);

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from meal_logs where user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from meal_items", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from meal_item_nutrient_details", Integer.class)).isZero();
    }
}
