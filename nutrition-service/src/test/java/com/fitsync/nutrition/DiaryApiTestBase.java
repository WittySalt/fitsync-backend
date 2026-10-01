package com.fitsync.nutrition;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.fitsync.nutrition.client.PersonBasics;
import com.fitsync.nutrition.client.PersonFacts;
import com.fitsync.nutrition.client.PersonLookup;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.messaging.repository.OutboxEventRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the Diary API tests share: an authenticated MockMvc, a clean database before each test, foods seeded with
 * numbers that can be checked by hand, and a stand-in for the user-service lookup so that sex, age, weight and
 * dietary pattern can be set per test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class DiaryApiTestBase {

    protected static final String BASE = "/api/v1/nutrition";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

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

    @MockitoBean
    protected PersonLookup personLookup;

    @BeforeEach
    void clearData() {
        outboxEventRepository.deleteAll();
        // Meals marked as deleted are hidden from the repositories, so the tables are emptied directly.
        jdbcTemplate.update("delete from meal_item_nutrient_details");
        jdbcTemplate.update("delete from meal_items");
        jdbcTemplate.update("delete from meal_logs");
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
        personIs(null, null, null, null);
    }

    /** Sets what the projection would say about the person: sex, age, latest weight in kg and dietary pattern. */
    protected void personIs(String sex, Integer age, String weightKg, String dietaryPattern) {
        PersonBasics basics = sex == null && age == null ? PersonBasics.unknown() : new PersonBasics(sex, age);
        org.mockito.Mockito.when(personLookup.facts(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PersonFacts(basics, weightKg == null ? null : new BigDecimal(weightKg), dietaryPattern));
    }

    protected void ensureCategory(String code, String foodGroup) {
        if (!foodCategoryRepository.findByCodeInAndActiveTrue(List.of(code)).isEmpty()) {
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

    protected String mealJson(String mealType, String consumedAt, Seeded food, String servingLabel, String quantity) {
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

    /** Logs one portion of the food's first serving and returns the new meal id. */
    protected String logMeal(String token, Seeded food, String consumedAt) throws Exception {
        return logMeal(token, "SNACK", food, consumedAt);
    }

    protected String logMeal(String token, String mealType, Seeded food, String consumedAt) throws Exception {
        String body = mockMvc.perform(post(BASE + "/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(mealJson(mealType, consumedAt, food, food.servingIds.keySet().iterator().next(), "1")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.mealId");
    }

    protected String tokenFor(String email) {
        return Jwts.builder()
                .claims(Map.of("userId", UUID.randomUUID().toString(), "displayName", "Diary Tester", "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    protected String userIdOf(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", String.class);
    }

    /** A seeded catalogue food and the ids of its servings by label, in the order they were added. */
    protected final class Seeded {
        protected final UUID foodId;
        protected final Map<String, UUID> servingIds = new java.util.LinkedHashMap<>();

        private Seeded(UUID foodId) {
            this.foodId = foodId;
        }

        protected Seeded addServing(String label, String unit, String quantity, String grams) {
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
            return this;
        }
    }

    protected Seeded seedFood(String name, String categoryCode, boolean countsTowardWater, String basisUnit,
                              int calories, String saturatedFat, String waterG, Map<String, String> details) {
        return seedFood(name, categoryCode, countsTowardWater, basisUnit, calories, saturatedFat, waterG, details,
                profile -> { });
    }

    /**
     * Seeds a food whose profile is stated per 100 of the basis unit, with saturated fat and water per 100 as
     * well. Every entry of the details map becomes a vitamin or mineral row, and the customiser can set the
     * remaining profile columns such as protein, fibre, sugar and sodium.
     */
    protected Seeded seedFood(String name, String categoryCode, boolean countsTowardWater, String basisUnit,
                              int calories, String saturatedFat, String waterG, Map<String, String> details,
                              Consumer<FoodNutrientProfileEntity> customiser) {
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
        customiser.accept(profile);
        foodNutrientProfileRepository.save(profile);

        int order = 1;
        for (Map.Entry<String, String> entry : new java.util.TreeMap<>(details).entrySet()) {
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

    protected static Map<String, String> mapOf(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
