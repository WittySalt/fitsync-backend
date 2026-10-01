package com.fitsync.nutrition;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Covers the reading side of the Diary: the day summary with its balance and protein spread, the water additions,
 * the range of days and the vitamin and mineral averages. The seeded foods are chosen so that every expected
 * number below is a sum that can be checked by hand.
 */
class NutritionDiaryReadApiTests extends DiaryApiTestBase {

    @Autowired
    private NutritionTargetRepository nutritionTargetRepository;

    private Seeded oats;
    private Seeded cola;
    private Seeded ham;

    @BeforeEach
    void seedFoods() {
        oats = seedFood("Rolled oats", "GRAINS", false, "g", 380, "1.2", null, mapOf("POTASSIUM", "429"), profile -> {
            profile.setProteinGrams(new java.math.BigDecimal("13"));
            profile.setFiberGrams(new java.math.BigDecimal("10"));
            profile.setSugarGrams(new java.math.BigDecimal("1"));
            profile.setSodiumMg(new java.math.BigDecimal("5"));
        }).addServing("100 g", "g", "100", "100");
        cola = seedFood("Cola", "BEVERAGES", true, "ml", 42, "0", "89", mapOf(), profile -> {
            profile.setSugarGrams(new java.math.BigDecimal("10.6"));
            profile.setSodiumMg(new java.math.BigDecimal("4"));
        }).addServing("1 cup", "cup", "1", "248");
        ham = seedFood("Cured ham", "MEAT_POULTRY", false, "g", 145, "3.0", null, mapOf("POTASSIUM", "300"), profile -> {
            profile.setProteinGrams(new java.math.BigDecimal("21"));
            profile.setSugarGrams(new java.math.BigDecimal("1"));
            profile.setSodiumMg(new java.math.BigDecimal("2400"));
            profile.setFiberGrams(java.math.BigDecimal.ZERO);
        }).addServing("100 g", "g", "100", "100");
    }

    /** Oats for breakfast, a cola as a snack and ham for lunch, all on 11 June. */
    private String logTheDay(String token) throws Exception {
        logMeal(token, "BREAKFAST", oats, "2026-06-11T08:00:00Z");
        logMeal(token, "SNACK", cola, "2026-06-11T10:00:00Z");
        logMeal(token, "LUNCH", ham, "2026-06-11T12:00:00Z");
        return token;
    }

    // ---------------------------------------------------------------------------------------------
    // GET /summary/daily

    @Test
    void dailySummaryAddsSaturatedFatPotassiumWaterAndDrinkSugar() throws Exception {
        personIs("MALE", 30, "80", null);
        String token = logTheDay(tokenFor("daily@example.com"));
        mockMvc.perform(post(BASE + "/water").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"amountMl\": 300, \"loggedAt\": \"2026-06-11T09:00:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealCount").value(3))
                .andExpect(jsonPath("$.totalCalories").value(629))
                .andExpect(jsonPath("$.totalProteinGrams").value(34.0))
                .andExpect(jsonPath("$.totalFiberGrams").value(10.0))
                .andExpect(jsonPath("$.totalSugarGrams").value(28.29))
                .andExpect(jsonPath("$.totalSodiumMg").value(2414.92))
                .andExpect(jsonPath("$.totalSaturatedFatGrams").value(4.2))
                .andExpect(jsonPath("$.totalPotassiumMg").value(729.0))
                .andExpect(jsonPath("$.waterMl").value(300))
                .andExpect(jsonPath("$.waterFromDrinksMl").value(221))
                .andExpect(jsonPath("$.sugarFromDrinksGrams").value(26.29));
    }

    @Test
    void dailySummaryKeepsTheOldFlagsWithReferencesReadFromTheTable() throws Exception {
        personIs("MALE", 30, null, null);
        String token = logTheDay(tokenFor("flags@example.com"));

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.fiberTargetGrams").value(28.0))
                .andExpect(jsonPath("$.sugarLimitGrams").value(50.0))
                .andExpect(jsonPath("$.sodiumLimitMg").value(2300.0))
                .andExpect(jsonPath("$.fiberTargetMet").value(false))
                .andExpect(jsonPath("$.sugarWithinLimit").value(true))
                .andExpect(jsonPath("$.sodiumWithinLimit").value(false));
    }

    @Test
    void balanceListsFiveLinesWithSharesFlagsAndTopSources() throws Exception {
        personIs("MALE", 30, null, null);
        String token = logTheDay(tokenFor("balance@example.com"));

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.balance.length()").value(5))
                .andExpect(jsonPath("$.balance[0].code").value("FIBER"))
                .andExpect(jsonPath("$.balance[0].displayName").value("Fibre"))
                .andExpect(jsonPath("$.balance[0].kind").value("TARGET"))
                .andExpect(jsonPath("$.balance[0].amount").value(10.0))
                .andExpect(jsonPath("$.balance[0].unit").value("g"))
                .andExpect(jsonPath("$.balance[0].reference").value(28.0))
                .andExpect(jsonPath("$.balance[0].share").value(0.357))
                .andExpect(jsonPath("$.balance[0].met").value(false))
                .andExpect(jsonPath("$.balance[0].over").value(false))
                .andExpect(jsonPath("$.balance[1].code").value("POTASSIUM"))
                .andExpect(jsonPath("$.balance[1].reference").value(3400.0))
                .andExpect(jsonPath("$.balance[1].share").value(0.214))
                .andExpect(jsonPath("$.balance[1].topSources.length()").value(2))
                .andExpect(jsonPath("$.balance[1].topSources[0].foodName").value("Rolled oats"))
                .andExpect(jsonPath("$.balance[1].topSources[0].amount").value(429.0))
                .andExpect(jsonPath("$.balance[1].topSources[1].foodName").value("Cured ham"))
                .andExpect(jsonPath("$.balance[2].code").value("SUGAR"))
                .andExpect(jsonPath("$.balance[2].kind").value("LIMIT"))
                .andExpect(jsonPath("$.balance[2].over").value(false))
                .andExpect(jsonPath("$.balance[2].met").value(false))
                .andExpect(jsonPath("$.balance[3].code").value("SAT_FAT"))
                .andExpect(jsonPath("$.balance[3].reference").value(22.0))
                .andExpect(jsonPath("$.balance[3].share").value(0.191))
                .andExpect(jsonPath("$.balance[4].code").value("SODIUM"))
                .andExpect(jsonPath("$.balance[4].over").value(true))
                .andExpect(jsonPath("$.balance[4].topSources.length()").value(3))
                .andExpect(jsonPath("$.balance[4].topSources[0].foodName").value("Cured ham"))
                .andExpect(jsonPath("$.balance[4].topSources[0].amount").value(2400.0))
                .andExpect(jsonPath("$.balance[4].topSources[1].foodName").value("Cola"))
                .andExpect(jsonPath("$.balance[4].topSources[2].foodName").value("Rolled oats"));
    }

    @Test
    void balanceMergesItemsOfTheSameFoodIntoOneSource() throws Exception {
        personIs("MALE", 30, null, null);
        String token = tokenFor("merge@example.com");
        logMeal(token, "BREAKFAST", oats, "2026-06-11T08:00:00Z");
        logMeal(token, "DINNER", oats, "2026-06-11T19:00:00Z");

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.balance[1].topSources.length()").value(1))
                .andExpect(jsonPath("$.balance[1].topSources[0].amount").value(858.0));
    }

    @Test
    void referencesFollowTheSexAndAgeOfThePerson() throws Exception {
        personIs("FEMALE", 55, null, null);
        String token = logTheDay(tokenFor("woman@example.com"));

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.balance[1].reference").value(2600.0));
    }

    @Test
    void anUnknownPersonGetsTheAdultReferencesAndTheDefaultProteinRange() throws Exception {
        personIs(null, null, null, null);
        String token = logTheDay(tokenFor("unknown@example.com"));

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance[0].reference").value(28.0))
                .andExpect(jsonPath("$.balance[1].reference").value(3000.0))
                .andExpect(jsonPath("$.proteinByMeal.weightKg").value(nullValue()))
                .andExpect(jsonPath("$.proteinByMeal.lowGrams").value(20))
                .andExpect(jsonPath("$.proteinByMeal.highGrams").value(40));
    }

    @Test
    void proteinByMealUsesTheWeightWhenKnownAndListsEveryMealSlot() throws Exception {
        personIs("MALE", 30, "80", null);
        String token = logTheDay(tokenFor("protein@example.com"));

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.proteinByMeal.weightKg").value(80.0))
                .andExpect(jsonPath("$.proteinByMeal.lowGrams").value(24))
                .andExpect(jsonPath("$.proteinByMeal.highGrams").value(32))
                .andExpect(jsonPath("$.proteinByMeal.meals.length()").value(4))
                .andExpect(jsonPath("$.proteinByMeal.meals[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.proteinByMeal.meals[0].grams").value(13.0))
                .andExpect(jsonPath("$.proteinByMeal.meals[1].mealType").value("LUNCH"))
                .andExpect(jsonPath("$.proteinByMeal.meals[1].grams").value(21.0))
                .andExpect(jsonPath("$.proteinByMeal.meals[2].mealType").value("DINNER"))
                .andExpect(jsonPath("$.proteinByMeal.meals[2].grams").value(0.0))
                .andExpect(jsonPath("$.proteinByMeal.meals[3].mealType").value("SNACK"))
                .andExpect(jsonPath("$.proteinByMeal.meals[3].grams").value(0.0));
    }

    @Test
    void anEmptyDayStillReturnsAFullBalanceAndTheSummaryIsPerUser() throws Exception {
        personIs("MALE", 30, null, null);
        logTheDay(tokenFor("busy@example.com"));
        String other = tokenFor("quiet@example.com");

        mockMvc.perform(get(BASE + "/summary/daily").header("Authorization", "Bearer " + other)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.mealCount").value(0))
                .andExpect(jsonPath("$.totalCalories").value(0))
                .andExpect(jsonPath("$.balance.length()").value(5))
                .andExpect(jsonPath("$.balance[0].amount").value(0.0))
                .andExpect(jsonPath("$.balance[0].topSources.length()").value(0))
                .andExpect(jsonPath("$.waterFromDrinksMl").value(0));
    }

    // ---------------------------------------------------------------------------------------------
    // GET /water

    @Test
    void waterDayAddsTheDrinksThatCountTowardWater() throws Exception {
        String token = logTheDay(tokenFor("water@example.com"));
        mockMvc.perform(post(BASE + "/water").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"amountMl\": 500, \"loggedAt\": \"2026-06-11T09:00:00Z\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get(BASE + "/water").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMl").value(500))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.fromDrinksMl").value(221))
                .andExpect(jsonPath("$.drinkEntries.length()").value(1))
                .andExpect(jsonPath("$.drinkEntries[0].foodName").value("Cola"))
                .andExpect(jsonPath("$.drinkEntries[0].waterMl").value(221))
                .andExpect(jsonPath("$.drinkEntries[0].consumedAt").value("2026-06-11T10:00:00Z"))
                .andExpect(jsonPath("$.drinkEntries[0].mealItemId").isNotEmpty())
                .andExpect(jsonPath("$.drinkEntries[0].mealId").isNotEmpty());
    }

    @Test
    void waterDayWithoutDrinksHasAnEmptyDrinkList() throws Exception {
        String token = tokenFor("nodrinks@example.com");
        logMeal(token, "BREAKFAST", oats, "2026-06-11T08:00:00Z");

        mockMvc.perform(get(BASE + "/water").header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.fromDrinksMl").value(0))
                .andExpect(jsonPath("$.drinkEntries.length()").value(0));
    }

    // ---------------------------------------------------------------------------------------------
    // GET /summary/range

    @Test
    void rangeSummaryReturnsOneEntryPerDayWithFlagsOnLoggedDays() throws Exception {
        personIs("MALE", 30, null, null);
        String token = tokenFor("range@example.com");
        UUID userId = UUID.fromString(userIdOf(token));
        NutritionTargetEntity target = new NutritionTargetEntity();
        target.setUserId(userId);
        target.setCalorieTarget(2000);
        target.setProjectionUpdatedAt(Instant.parse("2026-06-01T00:00:00Z"));
        nutritionTargetRepository.save(target);
        logMeal(token, "BREAKFAST", oats, "2026-06-10T08:00:00Z");
        logTheDay(token);

        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-06-09"))
                .andExpect(jsonPath("$.to").value("2026-06-11"))
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].date").value("2026-06-09"))
                .andExpect(jsonPath("$.days[0].logged").value(false))
                .andExpect(jsonPath("$.days[0].mealCount").value(0))
                .andExpect(jsonPath("$.days[0].totalCalories").value(0))
                .andExpect(jsonPath("$.days[0].targetCalories").value(2000))
                .andExpect(jsonPath("$.days[0].fiberMet").value(nullValue()))
                .andExpect(jsonPath("$.days[0].sodiumWithin").value(nullValue()))
                .andExpect(jsonPath("$.days[1].logged").value(true))
                .andExpect(jsonPath("$.days[1].mealCount").value(1))
                .andExpect(jsonPath("$.days[1].totalCalories").value(380))
                .andExpect(jsonPath("$.days[1].fiberGrams").value(10.0))
                .andExpect(jsonPath("$.days[1].potassiumMg").value(429.0))
                .andExpect(jsonPath("$.days[1].sugarGrams").value(1.0))
                .andExpect(jsonPath("$.days[1].saturatedFatGrams").value(1.2))
                .andExpect(jsonPath("$.days[1].sodiumMg").value(5.0))
                .andExpect(jsonPath("$.days[1].fiberMet").value(false))
                .andExpect(jsonPath("$.days[1].potassiumMet").value(false))
                .andExpect(jsonPath("$.days[1].sugarWithin").value(true))
                .andExpect(jsonPath("$.days[1].saturatedFatWithin").value(true))
                .andExpect(jsonPath("$.days[1].sodiumWithin").value(true))
                .andExpect(jsonPath("$.days[2].mealCount").value(3))
                .andExpect(jsonPath("$.days[2].totalCalories").value(629))
                .andExpect(jsonPath("$.days[2].potassiumMg").value(729.0))
                .andExpect(jsonPath("$.days[2].sodiumMg").value(2414.92))
                .andExpect(jsonPath("$.days[2].sodiumWithin").value(false));
    }

    @Test
    void rangeSummaryBucketsDaysInTheRequestedTimezone() throws Exception {
        String token = tokenFor("tz@example.com");
        // 20:00 UTC on 10 June is already 04:00 on 11 June in Singapore.
        logMeal(token, "SNACK", oats, "2026-06-10T20:00:00Z");

        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11").param("timezone", "Asia/Singapore"))
                .andExpect(jsonPath("$.days[0].logged").value(true));
        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11").param("timezone", "UTC"))
                .andExpect(jsonPath("$.days[0].logged").value(false));
    }

    @Test
    void rangeSummaryOnlyCountsTheCallersOwnMeals() throws Exception {
        logTheDay(tokenFor("someone@example.com"));
        String other = tokenFor("someone-else@example.com");

        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + other)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.days[0].logged").value(false))
                .andExpect(jsonPath("$.days[0].mealCount").value(0));
    }

    @Test
    void rangeSummaryAcceptsThirtyOneDaysAndRejectsMoreOrABackwardRange() throws Exception {
        String token = tokenFor("limits@example.com");

        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-05-12").param("to", "2026-06-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(31));
        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-05-11").param("to", "2026-06-11"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-10"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/summary/range").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-10").param("to", "2026-06-11").param("timezone", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rangeAndNutrientEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/summary/range").param("from", "2026-06-10").param("to", "2026-06-11"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/nutrients/summary").param("from", "2026-06-10").param("to", "2026-06-11"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------------
    // GET /nutrients/summary

    /** Spinach on 10 June, spinach and beef on 11 June, and one quick add on 10 June that carries no vitamins. */
    private String logVitaminDays(String token) throws Exception {
        Seeded spinach = seedFood("Spinach", "VEGETABLES", false, "g", 23, "0.1", null,
                mapOf("VIT_C", "28", "IRON", "2.7", "CALCIUM", "99", "MAGNESIUM", "79"))
                .addServing("100 g", "g", "100", "100");
        Seeded beef = seedFood("Beef", "MEAT_POULTRY", false, "g", 250, "6", null,
                mapOf("VIT_B12", "2.6", "ZINC", "4.8", "IRON", "3.0"))
                .addServing("100 g", "g", "100", "100");
        logMeal(token, "LUNCH", spinach, "2026-06-10T12:00:00Z");
        logMeal(token, "LUNCH", spinach, "2026-06-11T12:00:00Z");
        logMeal(token, "DINNER", beef, "2026-06-11T19:00:00Z");
        mockMvc.perform(post(BASE + "/meals/quick-add").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"name\": \"Mystery\", \"calories\": 100, \"consumedAt\": \"2026-06-10T15:00:00Z\"}"))
                .andExpect(status().isCreated());
        return token;
    }

    @Test
    void nutrientSummaryAveragesOverLoggedDaysAndSortsByShare() throws Exception {
        personIs("MALE", 25, null, null);
        String token = logVitaminDays(tokenFor("vitamins@example.com"));

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-06-09"))
                .andExpect(jsonPath("$.to").value("2026-06-11"))
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.daysLogged").value(2))
                .andExpect(jsonPath("$.referenceLabel").value("for a man aged 19 to 30"))
                .andExpect(jsonPath("$.focusCodes.length()").value(0))
                .andExpect(jsonPath("$.coverage.itemsLogged").value(4))
                .andExpect(jsonPath("$.coverage.itemsWithoutNutrients").value(1))
                .andExpect(jsonPath("$.nutrients.length()").value(7))
                .andExpect(jsonPath("$.nutrients[0].code").value("VIT_D"))
                .andExpect(jsonPath("$.nutrients[0].averageAmount").value(0.0))
                .andExpect(jsonPath("$.nutrients[0].richestUsualFood").value(nullValue()))
                .andExpect(jsonPath("$.nutrients[3].code").value("CALCIUM"))
                .andExpect(jsonPath("$.nutrients[3].averageAmount").value(99.0))
                .andExpect(jsonPath("$.nutrients[3].share").value(0.099))
                .andExpect(jsonPath("$.nutrients[4].code").value("MAGNESIUM"))
                .andExpect(jsonPath("$.nutrients[4].share").value(0.198))
                .andExpect(jsonPath("$.nutrients[5].code").value("VIT_C"))
                .andExpect(jsonPath("$.nutrients[5].averageAmount").value(28.0))
                .andExpect(jsonPath("$.nutrients[5].reference").value(90.0))
                .andExpect(jsonPath("$.nutrients[5].share").value(0.311))
                .andExpect(jsonPath("$.nutrients[5].met").value(false))
                .andExpect(jsonPath("$.nutrients[5].upperLimit").value(2000.0))
                .andExpect(jsonPath("$.nutrients[5].aboveUpperLimit").value(false))
                .andExpect(jsonPath("$.nutrients[5].richestUsualFood").value("Spinach"))
                .andExpect(jsonPath("$.nutrients[6].code").value("IRON"))
                .andExpect(jsonPath("$.nutrients[6].kind").value("MINERAL"))
                .andExpect(jsonPath("$.nutrients[6].unit").value("mg"))
                .andExpect(jsonPath("$.nutrients[6].averageAmount").value(4.2))
                .andExpect(jsonPath("$.nutrients[6].reference").value(8.0))
                .andExpect(jsonPath("$.nutrients[6].share").value(0.525))
                .andExpect(jsonPath("$.nutrients[6].richestUsualFood").value("Beef"));
    }

    @Test
    void nutrientSummaryPutsFocusNutrientsFirstForAVegetarian() throws Exception {
        personIs("FEMALE", 30, null, "VEGETARIAN");
        String token = logVitaminDays(tokenFor("veggie@example.com"));

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-09").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.referenceLabel").value("for a woman aged 19 to 30"))
                .andExpect(jsonPath("$.focusCodes[0]").value("VIT_B12"))
                .andExpect(jsonPath("$.focusCodes[1]").value("IRON"))
                .andExpect(jsonPath("$.focusCodes[2]").value("ZINC"))
                .andExpect(jsonPath("$.nutrients.length()").value(9))
                .andExpect(jsonPath("$.nutrients[0].code").value("VIT_B12"))
                .andExpect(jsonPath("$.nutrients[0].averageAmount").value(1.3))
                .andExpect(jsonPath("$.nutrients[0].reference").value(2.4))
                .andExpect(jsonPath("$.nutrients[0].richestUsualFood").value("Beef"))
                .andExpect(jsonPath("$.nutrients[1].code").value("IRON"))
                .andExpect(jsonPath("$.nutrients[1].reference").value(18.0))
                .andExpect(jsonPath("$.nutrients[2].code").value("ZINC"))
                .andExpect(jsonPath("$.nutrients[2].averageAmount").value(2.4))
                .andExpect(jsonPath("$.nutrients[2].reference").value(8.0));
    }

    @Test
    void nutrientSummaryForOneDayAndForADayWithNothingLogged() throws Exception {
        personIs(null, null, null, null);
        String token = logVitaminDays(tokenFor("oneday@example.com"));

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.days").value(1))
                .andExpect(jsonPath("$.daysLogged").value(1))
                .andExpect(jsonPath("$.referenceLabel").value("for an adult"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'VIT_C')].averageAmount").value(28.0));
        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-05-01").param("to", "2026-05-03"))
                .andExpect(jsonPath("$.daysLogged").value(0))
                .andExpect(jsonPath("$.coverage.itemsLogged").value(0))
                .andExpect(jsonPath("$.nutrients[0].averageAmount").value(0.0))
                .andExpect(jsonPath("$.nutrients[0].met").value(false));
    }

    @Test
    void nutrientSummaryFlagsAnAverageAboveTheUpperLimit() throws Exception {
        personIs("MALE", 40, null, null);
        String token = tokenFor("tonic@example.com");
        Seeded tonic = seedFood("Iron tonic", "PREPARED", false, "g", 10, "0", null, mapOf("IRON", "60"))
                .addServing("100 g", "g", "100", "100");
        logMeal(token, "SNACK", tonic, "2026-06-11T08:00:00Z");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'IRON')].averageAmount").value(60.0))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'IRON')].upperLimit").value(45.0))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'IRON')].aboveUpperLimit").value(true))
                .andExpect(jsonPath("$.nutrients[?(@.code == 'IRON')].met").value(true));
    }

    @Test
    void nutrientSummaryLabelsOlderPeopleAndRejectsTooLongRanges() throws Exception {
        personIs("MALE", 75, null, null);
        String token = tokenFor("older@example.com");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-11").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.referenceLabel").value("for a man aged 71 and over"));
        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-05-11").param("to", "2026-06-11"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-12").param("to", "2026-06-11"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nutrientSummaryDoesNotMixInAnotherUsersMeals() throws Exception {
        personIs("MALE", 25, null, null);
        logVitaminDays(tokenFor("first@example.com"));
        String other = tokenFor("second@example.com");

        mockMvc.perform(get(BASE + "/nutrients/summary").header("Authorization", "Bearer " + other)
                        .param("from", "2026-06-09").param("to", "2026-06-11"))
                .andExpect(jsonPath("$.daysLogged").value(0))
                .andExpect(jsonPath("$.coverage.itemsLogged").value(0))
                .andExpect(jsonPath("$.nutrients[0].richestUsualFood").value(nullValue()));
    }
}
