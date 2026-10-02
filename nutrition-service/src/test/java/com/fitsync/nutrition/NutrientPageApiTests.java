package com.fitsync.nutrition;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The all-nutrients summary and the page of one nutrient. Vitamin D comes from a few foods whose amounts are chosen
 * so that every number below can be checked by hand: salmon 10, eggs 2, milk 1 per 100 g.
 */
class NutrientPageApiTests extends DiaryApiTestBase {

    private Seeded salmon;
    private Seeded eggs;
    private Seeded milk;
    private Seeded oats;
    private Seeded ham;

    @BeforeEach
    void seedFoods() {
        ensureCategory("MISC", "OTHER");
        // The seven richest, one of them under a second entry with the same name, and one food without a plate group.
        salmon = seedFood("Salmon", "MEAT_POULTRY", false, "g", 200, "2", null, mapOf("VIT_D", "10"))
                .addServing("100 g", "g", "100", "100");
        seedFood("Salmon", "MEAT_POULTRY", false, "g", 210, "2", null, mapOf("VIT_D", "9.5"))
                .addServing("100 g", "g", "100", "100");
        eggs = seedFood("Eggs", "DAIRY", false, "g", 140, "3", null, mapOf("VIT_D", "2"))
                .addServing("100 g", "g", "100", "100");
        milk = seedFood("Milk", "DAIRY", false, "g", 60, "1", null, mapOf("VIT_D", "1"))
                .addServing("100 g", "g", "100", "100");
        seedFood("Mushrooms", "VEGETABLES", false, "g", 30, "0", null, mapOf("VIT_D", "7"));
        seedFood("Tuna", "MEAT_POULTRY", false, "g", 130, "1", null, mapOf("VIT_D", "6"));
        seedFood("Herring", "MEAT_POULTRY", false, "g", 160, "2", null, mapOf("VIT_D", "5"));
        seedFood("Trout", "MEAT_POULTRY", false, "g", 150, "2", null, mapOf("VIT_D", "4"));
        seedFood("Yogurt", "DAIRY", false, "g", 60, "1", null, mapOf("VIT_D", "3"));
        seedFood("Cod liver oil", "MISC", false, "g", 900, "20", null, mapOf("VIT_D", "250"));
        oats = seedFood("Rolled oats", "GRAINS", false, "g", 380, "1.2", null, mapOf(), profile -> {
            profile.setFiberGrams(new BigDecimal("10"));
        }).addServing("100 g", "g", "100", "100");
        ham = seedFood("Cured ham", "MEAT_POULTRY", false, "g", 145, "3.0", null, mapOf(), profile -> {
            profile.setSodiumMg(new BigDecimal("2400"));
        }).addServing("100 g", "g", "100", "100");
    }

    @AfterEach
    void removeGuidesAndExtraCategories() {
        jdbcTemplate.update("delete from nutrient_guides");
        // This class runs before NutritionApiTests, which counts the categories, so the ones the base class and this
        // class add (they are not part of the test schema) are removed again.
        jdbcTemplate.update("delete from food_categories where code in ('BEVERAGES', 'SWEETS', 'MISC')");
    }

    private String logVitaminDDays(String token) throws Exception {
        logMeal(token, "BREAKFAST", salmon, "2026-06-11T08:00:00Z");
        logMeal(token, "LUNCH", eggs, "2026-06-11T12:00:00Z");
        logMeal(token, "LUNCH", salmon, "2026-06-12T12:00:00Z");
        return token;
    }

    // ---------------------------------------------------------------------------------------------
    // GET /nutrients/summary?scope=all

    @Test
    void scopeAllListsEveryNutrientWithAGroupInTableOrder() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("all@example.com");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients", hasSize(22)))
                .andExpect(jsonPath("$.nutrients[0].code").value("VIT_D"))
                .andExpect(jsonPath("$.nutrients[0].groupCode").value("FAT_SOLUBLE"))
                .andExpect(jsonPath("$.nutrients[0].symbol").value("D"))
                .andExpect(jsonPath("$.nutrients[0].atomicNumber").value(nullValue()))
                .andExpect(jsonPath("$.nutrients[3].symbol").value("K1"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'CALCIUM')].groupCode").value("MAJOR_MINERAL"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'CALCIUM')].atomicNumber").value(20))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'IRON')].groupCode").value("TRACE_MINERAL"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'FOLATE')].symbol").value("B9"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].groupCode").value("LIMIT"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SUGAR')].symbol").value("Su"))
                .andExpect(jsonPath("$.nutrients[21].code").value("SAT_FAT"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'FIBER')]", hasSize(0)))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'CHOLESTEROL')]", hasSize(0)))
                .andExpect(jsonPath("$.nutrients[0].days", hasSize(3)));
    }

    @Test
    void scopeAllReadsLimitsFromTheMealItemColumns() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("limits@example.com");
        logMeal(token, "LUNCH", ham, "2026-06-11T12:00:00Z");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11").param("scope", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].averageAmount").value(2400.0))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].reference").value(2300.0))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].met").value(false))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].aboveUpperLimit").value(true))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'SODIUM')].days[0].amount").value(2400.0));
    }

    @Test
    void theDefaultScopeStaysTheDiaryList() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("diary@example.com");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients", hasSize(7)))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'VIT_D')].groupCode").value("FAT_SOLUBLE"));
        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11").param("scope", "diary"))
                .andExpect(jsonPath("$.nutrients", hasSize(7)));
    }

    @Test
    void anUnknownScopeIsABadRequest() throws Exception {
        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + tokenFor("scope@example.com"))
                        .param("from", "2026-06-09").param("to", "2026-06-11").param("scope", "everything"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------------------------
    // GET /nutrients/{code}

    @Test
    void theNutrientPageAnswersForAKnownCode() throws Exception {
        personIs("MALE", 25, null, null);
        String token = logVitaminDDays(tokenFor("page@example.com"));
        insertGuide("VIT_D");

        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-10").param("to", "2026-06-12").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VIT_D"))
                .andExpect(jsonPath("$.displayName").value("Vitamin D"))
                .andExpect(jsonPath("$.unit").value("µg"))
                .andExpect(jsonPath("$.groupCode").value("FAT_SOLUBLE"))
                .andExpect(jsonPath("$.symbol").value("D"))
                .andExpect(jsonPath("$.atomicNumber").value(nullValue()))
                .andExpect(jsonPath("$.from").value("2026-06-10"))
                .andExpect(jsonPath("$.to").value("2026-06-12"))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.daysLogged").value(2))
                .andExpect(jsonPath("$.averageAmount").value(11.0))
                .andExpect(jsonPath("$.reference").value(15.0))
                .andExpect(jsonPath("$.share").value(0.733))
                .andExpect(jsonPath("$.met").value(false))
                .andExpect(jsonPath("$.upperLimit").value(100.0))
                .andExpect(jsonPath("$.aboveUpperLimit").value(false))
                .andExpect(jsonPath("$.referenceLabel").value("Male ranges, aged 19 to 30"))
                .andExpect(jsonPath("$.dayValues", hasSize(3)))
                .andExpect(jsonPath("$.dayValues[0].logged").value(false))
                .andExpect(jsonPath("$.dayValues[1].amount").value(12.0))
                .andExpect(jsonPath("$.dayValues[2].amount").value(10.0))
                .andExpect(jsonPath("$.sources", hasSize(2)))
                .andExpect(jsonPath("$.sources[0].name").value("Salmon"))
                .andExpect(jsonPath("$.sources[0].foodGroup").value("PROTEIN"))
                .andExpect(jsonPath("$.sources[0].categoryCode").value("MEAT_POULTRY"))
                .andExpect(jsonPath("$.sources[0].amount").value(10.0))
                .andExpect(jsonPath("$.sources[0].share").value(0.909))
                .andExpect(jsonPath("$.sources[1].name").value("Eggs"))
                .andExpect(jsonPath("$.sources[1].amount").value(1.0))
                .andExpect(jsonPath("$.sources[1].share").value(0.091))
                .andExpect(jsonPath("$.guide.whatItDoes").value("It does things."))
                .andExpect(jsonPath("$.guide.tips[1]").value("Second tip."))
                .andExpect(jsonPath("$.guide.sourceLabel").value("NIH Office of Dietary Supplements"));
    }

    @Test
    void theAmountsListEveryAgeBandAndMarkTheCallersRow() throws Exception {
        personIs("FEMALE", 55, null, null);
        String token = tokenFor("bands@example.com");

        mockMvc.perform(get(BASE + "/nutrients/IRON").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceLabel").value("Female ranges, aged 51 to 70"))
                .andExpect(jsonPath("$.amounts", hasSize(4)))
                .andExpect(jsonPath("$.amounts[0].ageBand").value("19-30"))
                .andExpect(jsonPath("$.amounts[0].label").value("19 to 30"))
                .andExpect(jsonPath("$.amounts[0].female").value(18.0))
                .andExpect(jsonPath("$.amounts[0].male").value(8.0))
                .andExpect(jsonPath("$.amounts[0].mine").value(false))
                .andExpect(jsonPath("$.amounts[2].ageBand").value("51-70"))
                .andExpect(jsonPath("$.amounts[2].female").value(8.0))
                .andExpect(jsonPath("$.amounts[2].mine").value(true))
                .andExpect(jsonPath("$.amounts[3].ageBand").value("71+"))
                .andExpect(jsonPath("$.amounts[3].label").value("71 and over"))
                .andExpect(jsonPath("$.amounts[3].mine").value(false));
    }

    @Test
    void theDefaultRangeIsTheLastSevenDays() throws Exception {
        String token = tokenFor("defaults@example.com");

        mockMvc.perform(get(BASE + "/nutrients/VIT_C").header("Authorization", "Bearer " + token)
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(7))
                .andExpect(jsonPath("$.dayValues", hasSize(7)))
                .andExpect(jsonPath("$.averageAmount").value(nullValue()))
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.guide").value(nullValue()))
                .andExpect(jsonPath("$.referenceLabel").value("An adult"));
    }

    @Test
    void anUnknownCodeIs404AndALongRangeIs400() throws Exception {
        String token = tokenFor("errors@example.com");

        mockMvc.perform(get(BASE + "/nutrients/NOT_A_NUTRIENT").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token)
                        .param("from", "2026-05-01").param("to", "2026-06-12"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-12").param("to", "2026-06-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sourcesComeOnlyFromTheCallersOwnMeals() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("mine@example.com");
        String other = tokenFor("someone-else@example.com");
        logMeal(token, "LUNCH", eggs, "2026-06-11T12:00:00Z");
        logMeal(other, "LUNCH", salmon, "2026-06-11T12:00:00Z");

        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageAmount").value(2.0))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].name").value("Eggs"))
                .andExpect(jsonPath("$.sources[0].share").value(1.0));
    }

    @Test
    void aDeletedMealGivesNoSourceAndNoAmount() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("deleted@example.com");
        logMeal(token, "LUNCH", eggs, "2026-06-11T12:00:00Z");
        String salmonMeal = logMeal(token, "DINNER", salmon, "2026-06-11T19:00:00Z");
        mockMvc.perform(delete(BASE + "/meals/" + salmonMeal).header("Authorization", "Bearer " + token))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageAmount").value(2.0))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].name").value("Eggs"));
    }

    @Test
    void fibreSourcesComeFromTheMealItemColumnsAndHaveNoRichestList() throws Exception {
        personIs("MALE", 25, null, null);
        String token = tokenFor("fibre@example.com");
        logMeal(token, "BREAKFAST", oats, "2026-06-11T08:00:00Z");
        logMeal(token, "LUNCH", ham, "2026-06-11T12:00:00Z");

        mockMvc.perform(get(BASE + "/nutrients/FIBER").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupCode").value(nullValue()))
                .andExpect(jsonPath("$.averageAmount").value(10.0))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].name").value("Rolled oats"))
                .andExpect(jsonPath("$.richest", hasSize(0)));
    }

    @Test
    void theRichestFoodsAreTheCatalogueTopSixOnePerNameWithAGroup() throws Exception {
        String token = tokenFor("richest@example.com");

        mockMvc.perform(get(BASE + "/nutrients/VIT_D").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.richest", hasSize(6)))
                .andExpect(jsonPath("$.richest[0].name").value("Salmon"))
                .andExpect(jsonPath("$.richest[0].amountPer100g").value(10.0))
                .andExpect(jsonPath("$.richest[0].foodGroup").value("PROTEIN"))
                .andExpect(jsonPath("$.richest[0].categoryCode").value("MEAT_POULTRY"))
                .andExpect(jsonPath("$.richest[0].foodCatalogItemId").value(salmon.foodId.toString()))
                .andExpect(jsonPath("$.richest[1].name").value("Mushrooms"))
                .andExpect(jsonPath("$.richest[2].name").value("Tuna"))
                .andExpect(jsonPath("$.richest[3].name").value("Herring"))
                .andExpect(jsonPath("$.richest[4].name").value("Trout"))
                .andExpect(jsonPath("$.richest[5].name").value("Yogurt"))
                .andExpect(jsonPath("$.richest[*].name", not(hasItem("Cod liver oil"))))
                .andExpect(jsonPath("$.richest[*].name", not(hasItem("Milk"))));
    }

    @Test
    void theNutrientEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/nutrients/VIT_D"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/nutrients/summary").param("from", "2026-06-10").param("to", "2026-06-11")
                        .param("scope", "all"))
                .andExpect(status().isUnauthorized());
    }

    private void insertGuide(String code) {
        jdbcTemplate.update("""
                insert into nutrient_guides (code, what_it_does, too_little_too_much, who_needs_more, tips, source_label, updated_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, code, "It does things.", "Too little, too much.", "Some people.",
                "[\"First tip.\",\"Second tip.\"]", "NIH Office of Dietary Supplements",
                Timestamp.from(Instant.parse("2026-10-04T08:00:00Z")));
    }
}
