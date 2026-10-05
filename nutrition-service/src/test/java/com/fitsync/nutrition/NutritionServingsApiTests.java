package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * A food with USDA household measures: the search item carries the default serving, the detail lists every
 * serving with the "100 g" row as fallback, and logging a serving times a quantity stores the grams it weighs.
 */
class NutritionServingsApiTests extends DiaryApiTestBase {

    private Seeded sandwich;
    private Seeded plain;

    @BeforeEach
    void seedFoods() {
        ensureCategory("PREPARED", "OTHER");
        // The household measure comes first and is the default; the 100 g row stays as the fallback.
        sandwich = seedFood("Big Mac", "PREPARED", false, "g", 250, "4", null, mapOf())
                .addServing("1 sandwich", "sandwich", "1", "219")
                .addServing("3 oz", "oz", "3", "85")
                .addServing("100 g", "g", "100", "100");
        plain = seedFood("Plain rice", "PREPARED", false, "g", 130, "0.1", null, mapOf())
                .addServing("100 g", "g", "100", "100");
    }

    @Test
    void theSearchItemCarriesItsDefaultServing() throws Exception {
        String token = tokenFor("default-serving@example.com");

        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token).param("q", "big mac"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Big Mac"))
                .andExpect(jsonPath("$.content[0].defaultServing.servingId")
                        .value(sandwich.servingIds.get("1 sandwich").toString()))
                .andExpect(jsonPath("$.content[0].defaultServing.label").value("1 sandwich"))
                .andExpect(jsonPath("$.content[0].defaultServing.gramsEquivalent").value(219.0));
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token).param("q", "rice"))
                .andExpect(jsonPath("$.content[0].defaultServing.label").value("100 g"))
                .andExpect(jsonPath("$.content[0].defaultServing.gramsEquivalent").value(100.0));
    }

    @Test
    void theDetailListsEveryServingWithTheDefaultMarked() throws Exception {
        String token = tokenFor("servings-detail@example.com");

        mockMvc.perform(get(BASE + "/foods/{id}", sandwich.foodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servings.length()").value(3))
                .andExpect(jsonPath("$.servings[0].label").value("1 sandwich"))
                .andExpect(jsonPath("$.servings[0].defaultServing").value(true))
                .andExpect(jsonPath("$.servings[2].label").value("100 g"))
                .andExpect(jsonPath("$.servings[2].defaultServing").value(false));
    }

    @Test
    void aServingTimesAQuantityIsStoredAsGramsAndScalesTheNutrition() throws Exception {
        String token = tokenFor("servings-log@example.com");

        // Two sandwiches of 219 g at 250 kcal per 100 g.
        mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("LUNCH", "2026-06-11T12:00:00Z", sandwich, "1 sandwich", "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(438.0))
                .andExpect(jsonPath("$.items[0].calories").value(1095))
                .andExpect(jsonPath("$.items[0].quantity").value(2.0));
        // The 3 oz serving weighs 85 g, whatever its amount: two of them are 170 g, not 6 x 85 g.
        mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("DINNER", "2026-06-11T19:00:00Z", sandwich, "3 oz", "2")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(170.0))
                .andExpect(jsonPath("$.items[0].calories").value(425));
    }

    @Test
    void aServingOfAnotherFoodIsRejected() throws Exception {
        String token = tokenFor("servings-other@example.com");
        String body = """
                {"mealType": "LUNCH", "source": "MANUAL", "consumedAt": "2026-06-11T12:00:00Z",
                 "items": [{"foodCatalogItemId": "%s", "foodServingId": "%s", "quantity": 1}]}
                """.formatted(plain.foodId, sandwich.servingIds.get("1 sandwich"));

        mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body))
                .andExpect(status().isBadRequest());
    }
}
