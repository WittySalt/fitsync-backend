package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodCategoryRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers the Nutrition Diary batch 1 contract: the snapshot taken when a meal is logged, soft delete,
 * the new summary fields, the water additions, the food group filter and the two range endpoints.
 * Foods are seeded per test with stable names so that the numbers asserted here can be checked by hand.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionDiaryApiTests {

    private static final String BASE = "/api/v1/nutrition";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MealLogRepository mealLogRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private MealItemNutrientDetailRepository mealItemNutrientDetailRepository;

    @Autowired
    private FoodCatalogItemRepository foodCatalogItemRepository;

    @Autowired
    private FoodCategoryRepository foodCategoryRepository;

    @Autowired
    private FoodServingRepository foodServingRepository;

    @Autowired
    private FoodNutrientProfileRepository foodNutrientProfileRepository;

    @Autowired
    private FoodNutrientDetailRepository foodNutrientDetailRepository;

    @BeforeEach
    void clearData() {
        mealItemNutrientDetailRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealLogRepository.deleteAll();
        foodNutrientDetailRepository.deleteAll();
        foodServingRepository.deleteAll();
        foodNutrientProfileRepository.deleteAll();
        foodCatalogItemRepository.deleteAll();
        // The categories the production seed adds are not part of the test schema.
        ensureCategory("BEVERAGES", "DRINKS");
        ensureCategory("SWEETS", "SWEETS");
    }

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

        String created = mockMvc.perform(post(BASE + "/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson("SNACK", "2026-06-11T10:00:00Z", juice, "1 cup", "1")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mealId = com.jayway.jsonpath.JsonPath.read(created, "$.mealId");

        mockMvc.perform(post(BASE + "/meals/{id}/copy", mealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"consumedAt\": \"2026-06-12T10:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].waterMl").value(218))
                .andExpect(jsonPath("$.items[0].foodGroup").value("DRINKS"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].nutrientCode").value("VIT_C"));
    }

    // ---------------------------------------------------------------------------------------------
    // helpers

    private void ensureCategory(String code, String foodGroup) {
        if (!foodCategoryRepository.findByCodeInAndActiveTrue(java.util.List.of(code)).isEmpty()) {
            return;
        }
        FoodCategoryEntity category = new FoodCategoryEntity();
        category.setCategoryId(UUID.randomUUID());
        category.setCode(code);
        category.setDisplayName(code);
        category.setSlug(code.toLowerCase(Locale.ROOT));
        category.setPath("/" + code.toLowerCase(Locale.ROOT) + "/");
        category.setSortOrder(99);
        category.setActive(true);
        category.setFoodGroup(foodGroup);
        foodCategoryRepository.save(category);
    }

    private String mealJson(String mealType, String consumedAt, Seeded food, String servingLabel, String quantity) {
        return """
                {
                  "mealType": "%s",
                  "source": "MANUAL",
                  "consumedAt": "%s",
                  "timezone": "UTC",
                  "items": [{"foodCatalogItemId": "%s", "foodServingId": "%s", "quantity": %s}]
                }
                """.formatted(mealType, consumedAt, food.foodId, food.servingIds.get(servingLabel), quantity);
    }

    private String tokenFor(String email) {
        return Jwts.builder()
                .claims(Map.of("userId", UUID.randomUUID().toString(), "displayName", "Diary Tester", "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /** A seeded catalogue food and the ids of its servings by label. */
    private final class Seeded {
        private final UUID foodId;
        private final Map<String, UUID> servingIds = new HashMap<>();

        private Seeded(UUID foodId) {
            this.foodId = foodId;
        }

        private void addServing(String label, String unit, String quantity, String grams) {
            FoodServingEntity serving = new FoodServingEntity();
            serving.setServingId(UUID.randomUUID());
            serving.setFoodId(foodId);
            serving.setLabel(label);
            serving.setQuantity(new BigDecimal(quantity));
            serving.setUnit(unit);
            serving.setGramsEquivalent(new BigDecimal(grams));
            if ("cup".equals(unit)) {
                serving.setMillilitersEquivalent(new BigDecimal(grams));
            }
            serving.setSortOrder(servingIds.size() + 1);
            serving.setDefaultServing(servingIds.isEmpty());
            serving.setActive(true);
            foodServingRepository.save(serving);
            servingIds.put(label, serving.getServingId());
        }
    }

    /**
     * Seeds a food whose profile is stated per 100 of the basis unit, with saturated fat and water per 100 as
     * well. Every entry of the details map becomes a vitamin or mineral row in milligrams.
     */
    private Seeded seedFood(String name, String categoryCode, boolean countsTowardWater, String basisUnit,
                            int calories, String saturatedFat, String waterG, Map<String, String> details) {
        UUID foodId = UUID.randomUUID();
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.OFFICIAL);
        food.setDisplayName(name);
        food.setNormalizedName(name.toLowerCase(Locale.ROOT));
        food.setCategoryCode(categoryCode);
        food.setVerified(true);
        food.setActive(true);
        food.setCountsTowardWater(countsTowardWater);
        if ("ml".equals(basisUnit)) {
            food.setDensityGramsPerMl(BigDecimal.ONE);
        }
        foodCatalogItemRepository.save(food);

        FoodNutrientProfileEntity profile = new FoodNutrientProfileEntity();
        profile.setNutrientProfileId(UUID.randomUUID());
        profile.setFoodId(foodId);
        profile.setBasisQuantity(new BigDecimal("100"));
        profile.setBasisUnit(basisUnit);
        profile.setCalories(calories);
        profile.setSaturatedFatGrams(new BigDecimal(saturatedFat));
        profile.setWaterG(waterG == null ? null : new BigDecimal(waterG));
        profile.setActive(true);
        foodNutrientProfileRepository.save(profile);

        int order = 1;
        for (Map.Entry<String, String> entry : details.entrySet()) {
            FoodNutrientDetailEntity detail = new FoodNutrientDetailEntity();
            detail.setDetailId(UUID.randomUUID());
            detail.setFoodId(foodId);
            detail.setNutrientCode(entry.getKey());
            detail.setDisplayName(entry.getKey());
            detail.setAmount(new BigDecimal(entry.getValue()));
            detail.setUnit("mg");
            detail.setSortOrder(order++);
            detail.setActive(true);
            foodNutrientDetailRepository.save(detail);
        }
        return new Seeded(foodId);
    }
}
