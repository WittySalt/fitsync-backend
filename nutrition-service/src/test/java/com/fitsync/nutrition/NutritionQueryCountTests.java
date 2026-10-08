package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Guards the number of reads the busiest paths make, so a per-item query does not creep back in: a search page
 * reads the nutrient profiles of all its foods at once, a single food or meal reads only the categories it shows,
 * copying a day reads the category table once, and picking a food's serving reads its servings once.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NutritionQueryCountTests extends DiaryApiTestBase {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void startCounting() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        ensureCategory("PREPARED", "OTHER");
    }

    @Test
    void aSearchPageReadsTheProfilesOfAllItsFoodsInOneQuery() throws Exception {
        String token = tokenFor("search-profiles@example.com");
        for (String name : new String[] {"Apple pie", "Apple juice", "Apple crumble", "Apple sauce"}) {
            seedFood(name, "PREPARED", false, "g", 200, "1", null, mapOf()).addServing("100 g", "g", "100", "100");
        }

        statistics.clear();
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token).param("q", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.content[0].calories").value(200));

        assertThat(executions("FoodNutrientProfileEntity", "food_nutrient_profiles")).isEqualTo(1);
    }

    @Test
    void aFoodDetailReadsOnlyItsOwnCategory() throws Exception {
        String token = tokenFor("detail-category@example.com");
        Seeded pie = seedFood("Cherry pie", "PREPARED", false, "g", 250, "2", null, mapOf())
                .addServing("100 g", "g", "100", "100");

        statistics.clear();
        mockMvc.perform(get(BASE + "/foods/{id}", pie.foodId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodGroup").value("OTHER"));

        assertThat(rowsRead("FoodCategoryEntity", "food_categories")).isEqualTo(1);
    }

    @Test
    void aMealReadsOnlyTheCategoriesOfItsItems() throws Exception {
        String token = tokenFor("meal-category@example.com");
        Seeded cola = seedFood("Cola", "BEVERAGES", true, "ml", 42, "0", "89", mapOf())
                .addServing("1 can", "ml", "330", "330");
        String mealId = logMeal(token, cola, "2026-06-11T12:00:00Z");

        statistics.clear();
        mockMvc.perform(get(BASE + "/meals/{id}", mealId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].foodGroup").value("DRINKS"));

        assertThat(rowsRead("FoodCategoryEntity", "food_categories")).isEqualTo(1);
    }

    @Test
    void copyingADayReadsTheCategoryTableOnce() throws Exception {
        String token = tokenFor("copy-day@example.com");
        Seeded cola = seedFood("Cola", "BEVERAGES", true, "ml", 42, "0", "89", mapOf())
                .addServing("1 can", "ml", "330", "330");
        logMeal(token, "BREAKFAST", cola, "2026-06-11T08:00:00Z");
        logMeal(token, "LUNCH", cola, "2026-06-11T12:00:00Z");
        logMeal(token, "DINNER", cola, "2026-06-11T19:00:00Z");

        statistics.clear();
        mockMvc.perform(post(BASE + "/days/{date}/copy-to", "2026-06-11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"targetDate\": \"2026-06-12\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].items[0].foodGroup").value("DRINKS"));

        assertThat(executions("FoodCategoryEntity", "food_categories")).isEqualTo(1);
    }

    @Test
    void aFoodWithNoDefaultServingHasItsServingsReadOnce() throws Exception {
        String token = tokenFor("no-default@example.com");
        Seeded soup = seedFood("Tomato soup", "PREPARED", false, "g", 30, "0", null, mapOf())
                .addServing("1 bowl", "bowl", "1", "250");
        jdbcTemplate.update("update food_servings set default_serving = false where food_id = ?", soup.foodId);
        String body = """
                {"mealType": "LUNCH", "source": "MANUAL", "consumedAt": "2026-06-11T12:00:00Z",
                 "items": [{"foodCatalogItemId": "%s", "quantity": 1}]}
                """.formatted(soup.foodId);

        statistics.clear();
        mockMvc.perform(post(BASE + "/meals").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].snapshotServingLabel").value("1 bowl"))
                .andExpect(jsonPath("$.items[0].gramsConsumed").value(250.0));

        assertThat(executions("FoodServingEntity", "food_servings")).isEqualTo(1);
    }

    /**
     * How many times the queries that read the given entity ran since the counters were cleared. A JPQL query is
     * known by its text, which names the entity; a derived or criteria query by its SQL, which names the table.
     */
    private long executions(String entityName, String tableName) {
        return Arrays.stream(statistics.getQueries())
                .filter(query -> reads(query, entityName, tableName))
                .mapToLong(query -> statistics.getQueryStatistics(query).getExecutionCount())
                .sum();
    }

    /** How many rows the queries that read the given entity returned since the counters were cleared. */
    private long rowsRead(String entityName, String tableName) {
        return Arrays.stream(statistics.getQueries())
                .filter(query -> reads(query, entityName, tableName))
                .mapToLong(query -> statistics.getQueryStatistics(query).getExecutionRowCount())
                .sum();
    }

    private static boolean reads(String query, String entityName, String tableName) {
        return query.contains("from " + entityName + " ") || query.contains("from nutrition." + tableName + " ");
    }
}
