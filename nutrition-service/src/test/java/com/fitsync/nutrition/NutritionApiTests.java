package com.fitsync.nutrition;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.entity.FoodBarcodeEntity;
import com.fitsync.nutrition.entity.FoodBrandEntity;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.entity.MealLogEntity;
import com.fitsync.nutrition.entity.NutritionTargetEntity;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodBrandRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.CustomFoodRepository;
import com.fitsync.nutrition.repository.MealItemNutrientDetailRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import com.fitsync.nutrition.repository.MealLogRepository;
import com.fitsync.nutrition.repository.NutritionTargetRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fitsync.nutrition.messaging.OutboxEventStatus;
import com.fitsync.nutrition.messaging.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NutritionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DomainEventConsumer<GoalUpdatedPayload> goalUpdatedEventConsumer;

    @Autowired
    private CustomFoodRepository customFoodRepository;

    @Autowired
    private MealLogRepository mealLogRepository;

    @Autowired
    private MealItemRepository mealItemRepository;

    @Autowired
    private MealItemNutrientDetailRepository mealItemNutrientDetailRepository;

    @Autowired
    private NutritionTargetRepository nutritionTargetRepository;

    @Autowired
    private FoodBrandRepository foodBrandRepository;

    @Autowired
    private FoodCatalogItemRepository foodCatalogItemRepository;

    @Autowired
    private FoodServingRepository foodServingRepository;

    @Autowired
    private FoodBarcodeRepository foodBarcodeRepository;

    @Autowired
    private FoodNutrientProfileRepository foodNutrientProfileRepository;

    @Autowired
    private FoodNutrientDetailRepository foodNutrientDetailRepository;

    @BeforeEach
    void clearData() {
        outboxEventRepository.deleteAll();
        mealItemNutrientDetailRepository.deleteAll();
        mealItemRepository.deleteAll();
        mealLogRepository.deleteAll();
        customFoodRepository.deleteAll();
        nutritionTargetRepository.deleteAll();
        foodNutrientDetailRepository.deleteAll();
        foodBarcodeRepository.deleteAll();
        foodServingRepository.deleteAll();
        foodNutrientProfileRepository.deleteAll();
        foodCatalogItemRepository.deleteAll();
        foodBrandRepository.deleteAll();
    }

    @Test
    void protectedNutritionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/nutrition/meals"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void dailySummaryUsesUserGoalEventsAsNutritionTargetSourceOfTruth() throws Exception {
        String token = tokenFor("target-sync@example.com", "Target Sync");
        UUID userId = UUID.fromString(userIdFromToken(token));

        NutritionTargetEntity staleTarget = new NutritionTargetEntity();
        staleTarget.setUserId(userId);
        staleTarget.setCalorieTarget(2500);
        staleTarget.setProteinTarget(new BigDecimal("155.0"));
        staleTarget.setProjectionUpdatedAt(Instant.parse("2026-06-11T09:00:00Z"));
        nutritionTargetRepository.save(staleTarget);

        goalUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T10:00:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new BigDecimal("1800"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));
        goalUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T10:05:00Z"),
                "user-service",
                userId,
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "PROTEIN",
                        new BigDecimal("140.0"),
                        "g",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        mockMvc.perform(get("/api/v1/nutrition/summary/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11")
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCalories").value(1800))
                .andExpect(jsonPath("$.targetProteinGrams").value(140.0));
    }

    @Test
    void canCreateReadUpdateDeleteMealAndReturnDailySummary() throws Exception {
        String token = tokenFor("alice@example.com", "Alice");
        String userId = userIdFromToken(token);

        goalUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T10:00:00Z"),
                "user-service",
                UUID.fromString(userId),
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "CALORIES",
                        new java.math.BigDecimal("1800"),
                        "kcal",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));
        goalUpdatedEventConsumer.consume(new FitSyncDomainEvent<>(
                UUID.randomUUID(),
                "GoalUpdated",
                1,
                Instant.parse("2026-06-11T10:05:00Z"),
                "user-service",
                UUID.fromString(userId),
                new GoalUpdatedPayload(
                        UUID.randomUUID(),
                        "PROTEIN",
                        new java.math.BigDecimal("130.0"),
                        "g",
                        LocalDate.parse("2026-09-01"),
                        "ACTIVE")));

        String mealResponse = mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "name": "Chicken rice",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T12:45:00Z",
                                  "timezone": "Asia/Singapore",
                                  "note": "Post-workout meal",
                                  "items": [
                                    {
                                      "foodName": "Chicken breast",
                                      "quantity": 1.0,
                                      "unit": "serving",
                                      "calories": 320,
                                      "proteinGrams": 35.0,
                                      "carbsGrams": 0.0,
                                      "fatGrams": 12.0
                                    },
                                    {
                                      "foodName": "Rice",
                                      "quantity": 1.0,
                                      "unit": "bowl",
                                      "calories": 280,
                                      "proteinGrams": 5.0,
                                      "carbsGrams": 52.0,
                                      "fatGrams": 2.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("DINNER"))
                .andExpect(jsonPath("$.totalCalories").value(600))
                .andExpect(jsonPath("$.totalProteinGrams").value(40.0))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String mealId = com.jayway.jsonpath.JsonPath.read(mealResponse, "$.mealId");

        mockMvc.perform(get("/api/v1/nutrition/meals/latest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealId").value(mealId));

        mockMvc.perform(get("/api/v1/nutrition/summary/daily")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-11")
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCalories").value(600))
                .andExpect(jsonPath("$.totalCarbsGrams").value(52.0))
                .andExpect(jsonPath("$.targetCalories").value(1800))
                .andExpect(jsonPath("$.targetProteinGrams").value(130.0))
                .andExpect(jsonPath("$.remainingCalories").value(1200))
                .andExpect(jsonPath("$.remainingProteinGrams").value(90.0))
                .andExpect(jsonPath("$.mealCount").value(1));

        mockMvc.perform(put("/api/v1/nutrition/meals/{mealId}", mealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "name": "Chicken rice updated",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T13:00:00Z",
                                  "timezone": "Asia/Singapore",
                                  "items": [
                                    {
                                      "foodName": "Chicken breast",
                                      "quantity": 1.0,
                                      "unit": "serving",
                                      "calories": 300,
                                      "proteinGrams": 34.0,
                                      "carbsGrams": 0.0,
                                      "fatGrams": 10.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chicken rice updated"))
                .andExpect(jsonPath("$.totalCalories").value(300))
                .andExpect(jsonPath("$.itemCount").value(1));

        mockMvc.perform(delete("/api/v1/nutrition/meals/{mealId}", mealId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nutrition/meals/{mealId}", mealId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void dailySummaryExposesMicronutrientTotalsAndLimitFlags() throws Exception {
        String token = tokenFor("rita@example.com", "Rita");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "LUNCH",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-15T12:00:00Z",
                                  "timezone": "UTC",
                                  "items": [
                                    {
                                      "foodName": "Salty fibrous bowl",
                                      "quantity": 1.0,
                                      "unit": "bowl",
                                      "calories": 500,
                                      "fiberGrams": 30.0,
                                      "sugarGrams": 60.0,
                                      "sodiumMg": 2500.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/nutrition/summary/daily")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-15")
                        .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFiberGrams").value(30.00))
                .andExpect(jsonPath("$.totalSugarGrams").value(60.00))
                .andExpect(jsonPath("$.totalSodiumMg").value(2500.00))
                .andExpect(jsonPath("$.fiberTargetGrams").value(28))
                .andExpect(jsonPath("$.sugarLimitGrams").value(50))
                .andExpect(jsonPath("$.sodiumLimitMg").value(2300))
                .andExpect(jsonPath("$.fiberTargetMet").value(true))
                .andExpect(jsonPath("$.sugarWithinLimit").value(false))
                .andExpect(jsonPath("$.sodiumWithinLimit").value(false));
    }

    @Test
    void canManageCustomFoods() throws Exception {
        String token = tokenFor("bob@example.com", "Bob");

        String customFoodResponse = mockMvc.perform(post("/api/v1/nutrition/custom-foods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "name": "Greek Yogurt",
                                  "brandName": "FitBrand",
                                  "servingSize": 170.0,
                                  "servingUnit": "g",
                                  "calories": 120,
                                  "proteinGrams": 17.0,
                                  "carbsGrams": 6.0,
                                  "fatGrams": 2.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Greek Yogurt"))
                .andExpect(jsonPath("$.verified").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String customFoodId = com.jayway.jsonpath.JsonPath.read(customFoodResponse, "$.customFoodId");

        mockMvc.perform(get("/api/v1/nutrition/custom-foods")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Greek Yogurt"));

        mockMvc.perform(put("/api/v1/nutrition/custom-foods/{customFoodId}", customFoodId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "name": "Greek Yogurt Light",
                                  "brandName": "FitBrand",
                                  "servingSize": 170.0,
                                  "servingUnit": "g",
                                  "calories": 100,
                                  "proteinGrams": 16.0,
                                  "carbsGrams": 5.0,
                                  "fatGrams": 1.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Greek Yogurt Light"))
                .andExpect(jsonPath("$.calories").value(100));
    }

    @Test
    void quickAddCreatesSingleItemMealWithDefaults() throws Exception {
        String token = tokenFor("sam@example.com", "Sam");

        // Only calories supplied -> defaults: SNACK, "Quick add" item, now.
        mockMvc.perform(post("/api/v1/nutrition/meals/quick-add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "calories": 450 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("SNACK"))
                .andExpect(jsonPath("$.totalCalories").value(450))
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.items[0].foodName").value("Quick add"))
                .andExpect(jsonPath("$.items[0].unit").value("serving"));

        // With macros + explicit fields.
        mockMvc.perform(post("/api/v1/nutrition/meals/quick-add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "LUNCH",
                                  "name": "Food court plate",
                                  "consumedAt": "2026-06-15T05:00:00Z",
                                  "calories": 700,
                                  "proteinGrams": 35.0,
                                  "carbsGrams": 80.0,
                                  "fatGrams": 22.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("LUNCH"))
                .andExpect(jsonPath("$.name").value("Food court plate"))
                .andExpect(jsonPath("$.totalProteinGrams").value(35.0))
                .andExpect(jsonPath("$.items[0].foodName").value("Food court plate"));

        // calories is required.
        mockMvc.perform(post("/api/v1/nutrition/meals/quick-add")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "name": "No calories" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canCopyMealAndCopyEntireDay() throws Exception {
        String token = tokenFor("tina@example.com", "Tina");

        String mealResponse = mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "BREAKFAST",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-14T08:00:00Z",
                                  "timezone": "UTC",
                                  "items": [
                                    { "foodName": "Oats", "quantity": 1.0, "unit": "bowl", "calories": 300 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mealId = com.jayway.jsonpath.JsonPath.read(mealResponse, "$.mealId");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "LUNCH",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-14T12:00:00Z",
                                  "timezone": "UTC",
                                  "items": [
                                    { "foodName": "Salad", "quantity": 1.0, "unit": "bowl", "calories": 400 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        // Copy a single meal to a new day, overriding the meal type.
        mockMvc.perform(post("/api/v1/nutrition/meals/{mealId}/copy", mealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "consumedAt": "2026-06-20T08:00:00Z", "mealType": "SNACK" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("SNACK"))
                .andExpect(jsonPath("$.totalCalories").value(300))
                .andExpect(jsonPath("$.items[0].foodName").value("Oats"));

        // Copy the whole 2026-06-14 day (2 meals) to 2026-06-21.
        mockMvc.perform(post("/api/v1/nutrition/days/{date}/copy-to", "2026-06-14")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "targetDate": "2026-06-21", "timezone": "UTC" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/nutrition/summary/daily")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-21").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mealCount").value(2))
                .andExpect(jsonPath("$.totalCalories").value(700));
    }

    @Test
    void canLogWaterAndGetTimezoneAwareDailyTotal() throws Exception {
        String token = tokenFor("wendy@example.com", "Wendy");

        String first = mockMvc.perform(post("/api/v1/nutrition/water")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "amountMl": 250, "loggedAt": "2026-06-16T09:00:00Z", "timezone": "UTC" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountMl").value(250))
                .andReturn().getResponse().getContentAsString();
        String waterId = com.jayway.jsonpath.JsonPath.read(first, "$.waterId");

        mockMvc.perform(post("/api/v1/nutrition/water")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "amountMl": 500, "loggedAt": "2026-06-16T15:00:00Z" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/nutrition/water")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-16").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMl").value(750))
                .andExpect(jsonPath("$.entries.length()").value(2));

        mockMvc.perform(delete("/api/v1/nutrition/water/{waterId}", waterId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nutrition/water")
                        .header("Authorization", "Bearer " + token)
                        .param("date", "2026-06-16").param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMl").value(500))
                .andExpect(jsonPath("$.entries.length()").value(1));

        mockMvc.perform(post("/api/v1/nutrition/water")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "amountMl": 0 }
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/nutrition/water/{waterId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void canExportFoodDiaryAsCsv() throws Exception {
        String token = tokenFor("xena@example.com", "Xena");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "BREAKFAST",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-16T08:00:00Z",
                                  "items": [
                                    { "foodName": "Oats", "quantity": 1.0, "unit": "bowl", "calories": 300, "proteinGrams": 10.0 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        var response = mockMvc.perform(get("/api/v1/nutrition/export")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-16T00:00:00Z")
                        .param("to", "2026-06-17T00:00:00Z"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        org.assertj.core.api.Assertions.assertThat(response.getContentType()).contains("text/csv");
        org.assertj.core.api.Assertions.assertThat(response.getContentAsString())
                .contains("consumed_at,meal_type")
                .contains("Oats")
                .contains("BREAKFAST")
                .contains("300");

        // Over-range -> 400.
        mockMvc.perform(get("/api/v1/nutrition/export")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2020-01-01T00:00:00Z")
                        .param("to", "2026-06-16T00:00:00Z"))
                .andExpect(status().isBadRequest());

        // Unsupported format -> 400.
        mockMvc.perform(get("/api/v1/nutrition/export")
                        .header("Authorization", "Bearer " + token)
                        .param("from", "2026-06-16T00:00:00Z")
                        .param("to", "2026-06-17T00:00:00Z")
                        .param("format", "pdf"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownBarcodeIs404WhenExternalFallbackDisabled() throws Exception {
        String token = tokenFor("yan@example.com", "Yan");
        // External food source defaults to disabled, so a local miss stays a 404.
        mockMvc.perform(get("/api/v1/nutrition/barcodes/{barcode}", "0000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void mealValidationRejectsEmptyItems() throws Exception {
        String token = tokenFor("carol@example.com", "Carol");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "BREAKFAST",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T08:00:00Z",
                                  "items": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void mealCanPersistAndReturnDetailedNutrients() throws Exception {
        String token = tokenFor("erin@example.com", "Erin");

        String mealResponse = mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "BREAKFAST",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T08:00:00Z",
                                  "items": [
                                    {
                                      "foodName": "Oatmeal",
                                      "quantity": 1.0,
                                      "unit": "bowl",
                                      "calories": 260,
                                      "proteinGrams": 9.0,
                                      "carbsGrams": 44.0,
                                      "fatGrams": 5.0,
                                      "nutrientDetails": [
                                        {
                                          "nutrientCode": "FIBER",
                                          "displayName": "Fiber",
                                          "amount": 6.0,
                                          "unit": "g",
                                          "sortOrder": 1
                                        },
                                        {
                                          "nutrientCode": "SODIUM",
                                          "displayName": "Sodium",
                                          "amount": 140.0,
                                          "unit": "mg",
                                          "sortOrder": 2
                                        }
                                      ]
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].nutrientCode").value("FIBER"))
                .andExpect(jsonPath("$.items[0].nutrientDetails[1].amount").value(140.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String mealId = com.jayway.jsonpath.JsonPath.read(mealResponse, "$.mealId");

        mockMvc.perform(get("/api/v1/nutrition/meals/{mealId}", mealId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nutrientDetails.length()").value(2))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].displayName").value("Fiber"));
    }

    @Test
    void brandlessFoodDetailAndMealLogDoNotError() throws Exception {
        String token = tokenFor("zoe@example.com", "Zoe");

        // Brandless food (like the USDA seed) — GET /foods/{id} must not 500 (brand NPE).
        seedGlobalFood("b0000001-0000-0000-0000-000000000000", "Generic Oats", "GRAINS", true);
        mockMvc.perform(get("/api/v1/nutrition/foods/{id}", "b0000001-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Generic Oats"));

        // Logging a brandless catalog food into a meal must not 500 (brand NPE in resolution).
        seedBrandlessFoodWithNutrition("b0000002-0000-0000-0000-000000000000",
                "b0000003-0000-0000-0000-000000000000", "Generic Chicken", "MEAT_POULTRY");
        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "LUNCH",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-16T12:00:00Z",
                                  "items": [
                                    {
                                      "foodCatalogItemId": "b0000002-0000-0000-0000-000000000000",
                                      "foodServingId": "b0000003-0000-0000-0000-000000000000",
                                      "quantity": 1.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].foodName").value("Generic Chicken"))
                .andExpect(jsonPath("$.items[0].calories").value(165));
    }

    @Test
    void canSearchFoodsLookupBarcodeAndViewNutrientDetails() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("frank@example.com", "Frank");

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "greek"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].foodId").value(foodId.toString()))
                .andExpect(jsonPath("$.content[0].displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Dairy Products"))
                .andExpect(jsonPath("$.content[0].basisLabel").value("170 g"))
                .andExpect(jsonPath("$.content[0].primaryBarcode").value("8880001112223"));

        mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.creationMode").value("MANUAL"))
                .andExpect(jsonPath("$.servings.length()").value(2))
                .andExpect(jsonPath("$.nutrientDetails.length()").value(3))
                .andExpect(jsonPath("$.nutrientDetails[0].nutrientCode").value("PROTEIN"))
                .andExpect(jsonPath("$.calories").value(120));

        mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}/servings", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("1 container"));

        mockMvc.perform(get("/api/v1/nutrition/barcodes/{barcode}", "8880001112223")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodId").value(foodId.toString()))
                .andExpect(jsonPath("$.primaryBarcode").value("8880001112223"));

        mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriesEndpointReturnsTwoLevelTaxonomyTree() throws Exception {
        String token = tokenFor("nora@example.com", "Nora");

        mockMvc.perform(get("/api/v1/nutrition/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("PROTEINS"))
                .andExpect(jsonPath("$[0].parentId").doesNotExist())
                .andExpect(jsonPath("$[0].children.length()").value(5))
                .andExpect(jsonPath("$[0].children[0].code").value("MEAT_POULTRY"))
                .andExpect(jsonPath("$[2].code").value("PRODUCE"))
                .andExpect(jsonPath("$[2].children.length()").value(2));
    }

    @Test
    void searchCanFilterByCategorySubtreeAndExactCategory() throws Exception {
        // Greek Yogurt is seeded under category_code DAIRY (subcategory of DAIRY_GROUP).
        seedFoodCatalog();
        String token = tokenFor("oscar@example.com", "Oscar");

        // Parent group, subtree filter → finds the DAIRY food.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", "ca700004-0000-0000-0000-000000000000")) // DAIRY_GROUP
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Dairy Products"));

        // Unrelated group → no results.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", "ca700001-0000-0000-0000-000000000000")) // PROTEINS
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));

        // Exact leaf category (no subcategories) → still finds it.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", "ca710009-0000-0000-0000-000000000000") // DAIRY leaf
                        .param("includeSubcategories", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Dairy Products"));

        // Unknown category id → 404.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchRanksByRelevanceAndSupportsPaginationAndFilters() throws Exception {
        String token = tokenFor("paul@example.com", "Paul");
        seedGlobalFood("a0000001-0000-0000-0000-000000000000", "Rice", "GRAINS", true);
        seedGlobalFood("a0000002-0000-0000-0000-000000000000", "Rice Cake", "GRAINS", true);
        seedGlobalFood("a0000003-0000-0000-0000-000000000000", "Brown Rice", "GRAINS", false);

        // Relevance: exact ("Rice") > prefix ("Rice Cake") > substring ("Brown Rice").
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "rice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Rice"))
                .andExpect(jsonPath("$.content[1].displayName").value("Rice Cake"))
                .andExpect(jsonPath("$.content[2].displayName").value("Brown Rice"));

        // Pagination: page 0 size 2, then page 1 size 2.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "rice").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].displayName").value("Rice"));
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "rice").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].displayName").value("Brown Rice"));

        // verified filter.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "rice").param("verified", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].displayName").value("Brown Rice"));

        // scope filter: all seeded foods are GLOBAL, so USER scope returns none.
        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "rice").param("scope", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void searchPageMetadataReportsMultiplePagesAndDifferentSecondPage() throws Exception {
        String token = tokenFor("page-search@example.com", "Page Search");
        seedGlobalFood("a0000004-0000-0000-0000-000000000000", "Apple", "FRUITS", true);
        seedGlobalFood("a0000005-0000-0000-0000-000000000000", "Apple Butter", "FRUITS", true);
        seedGlobalFood("a0000006-0000-0000-0000-000000000000", "Apple Cider", "FRUITS", true);

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "apple").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.content[0].displayName").value("Apple"));

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "apple").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.content[0].displayName").value("Apple Cider"));
    }

    @Test
    void canManageRecipeAndAggregateNutritionFromIngredients() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("quinn@example.com", "Quinn");

        String foodResponse = mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String servingId = com.jayway.jsonpath.JsonPath.read(foodResponse, "$.servings[0].servingId");

        // 1 container (170 g) x 2 of Greek Yogurt (120 kcal / 170 g) -> 240 kcal, 34 g protein.
        String recipeResponse = mockMvc.perform(post("/api/v1/nutrition/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "name": "Yogurt Bowl",
                                  "servings": 2,
                                  "note": "Snack",
                                  "ingredients": [
                                    { "foodCatalogItemId": "%s", "foodServingId": "%s", "quantity": 2.0 }
                                  ]
                                }
                                """.formatted(foodId, servingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Yogurt Bowl"))
                .andExpect(jsonPath("$.totalCalories").value(240))
                .andExpect(jsonPath("$.totalProteinGrams").value(34.00))
                .andExpect(jsonPath("$.perServingCalories").value(120))
                .andExpect(jsonPath("$.perServingProteinGrams").value(17.00))
                .andExpect(jsonPath("$.ingredients[0].foodName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.ingredients[0].calories").value(240))
                .andReturn().getResponse().getContentAsString();

        String recipeId = com.jayway.jsonpath.JsonPath.read(recipeResponse, "$.recipeId");

        mockMvc.perform(get("/api/v1/nutrition/recipes")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Yogurt Bowl"));

        // Scaling servings to 4 halves the per-serving values (total unchanged).
        mockMvc.perform(put("/api/v1/nutrition/recipes/{recipeId}", recipeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "name": "Yogurt Bowl XL",
                                  "servings": 4,
                                  "ingredients": [
                                    { "foodCatalogItemId": "%s", "foodServingId": "%s", "quantity": 2.0 }
                                  ]
                                }
                                """.formatted(foodId, servingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yogurt Bowl XL"))
                .andExpect(jsonPath("$.totalCalories").value(240))
                .andExpect(jsonPath("$.perServingCalories").value(60));

        mockMvc.perform(delete("/api/v1/nutrition/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nutrition/recipes/{recipeId}", recipeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void canCreateMealTemplateAndMaterializeAndSaveFromMeal() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("victor@example.com", "Victor");

        String foodResponse = mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String servingId = com.jayway.jsonpath.JsonPath.read(foodResponse, "$.servings[0].servingId");

        // Inline template: 2 containers of Greek Yogurt -> 240 kcal.
        String templateResponse = mockMvc.perform(post("/api/v1/nutrition/meal-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "name": "Yogurt breakfast",
                                  "mealType": "BREAKFAST",
                                  "items": [
                                    { "foodCatalogItemId": "%s", "foodServingId": "%s", "quantity": 2.0 }
                                  ]
                                }
                                """.formatted(foodId, servingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Yogurt breakfast"))
                .andExpect(jsonPath("$.totalCalories").value(240))
                .andExpect(jsonPath("$.items[0].foodName").value("Greek Yogurt"))
                .andReturn().getResponse().getContentAsString();
        String templateId = com.jayway.jsonpath.JsonPath.read(templateResponse, "$.templateId");

        mockMvc.perform(get("/api/v1/nutrition/meal-templates")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Materialize into a real meal (inherits the template's BREAKFAST type).
        mockMvc.perform(post("/api/v1/nutrition/meals/from-template/{templateId}", templateId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "consumedAt": "2026-06-16T07:00:00Z" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.totalCalories").value(240))
                .andExpect(jsonPath("$.items[0].foodName").value("Greek Yogurt"));

        // Save a template from an existing meal.
        String mealResponse = mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "DINNER",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-16T18:00:00Z",
                                  "items": [
                                    { "foodName": "Steak", "quantity": 1.0, "unit": "piece", "calories": 500 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mealId = com.jayway.jsonpath.JsonPath.read(mealResponse, "$.mealId");

        mockMvc.perform(post("/api/v1/nutrition/meal-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                { "name": "Steak dinner", "fromMealId": "%s" }
                                """.formatted(mealId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCalories").value(500))
                .andExpect(jsonPath("$.items[0].foodName").value("Steak"));

        // Delete the first template.
        mockMvc.perform(delete("/api/v1/nutrition/meal-templates/{templateId}", templateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/nutrition/meal-templates/{templateId}", templateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void canFavoriteUnfavoriteAndListFavorites() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("uma@example.com", "Uma");

        // Favorite is idempotent.
        mockMvc.perform(post("/api/v1/nutrition/foods/{foodId}/favorite", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/nutrition/foods/{foodId}/favorite", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nutrition/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].foodId").value(foodId.toString()))
                .andExpect(jsonPath("$[0].displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$[0].categoryName").value("Dairy Products"));

        mockMvc.perform(delete("/api/v1/nutrition/foods/{foodId}/favorite", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/nutrition/favorites")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Favoriting an unknown food is a 404.
        mockMvc.perform(post("/api/v1/nutrition/foods/{foodId}/favorite", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void canCloneGlobalFoodIntoUserFood() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("gina@example.com", "Gina");

        mockMvc.perform(post("/api/v1/nutrition/foods/{foodId}/clone", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scope").value("USER"))
                .andExpect(jsonPath("$.baseFoodId").value(foodId.toString()))
                .andExpect(jsonPath("$.creationMode").value("CLONED"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.servings.length()").value(2))
                .andExpect(jsonPath("$.nutrientDetails.length()").value(3));
    }

    @Test
    void canCreateMealFromFoodTemplateWithoutClientSuppliedMacros() throws Exception {
        UUID foodId = seedFoodCatalog();
        String token = tokenFor("henry@example.com", "Henry");

        String foodResponse = mockMvc.perform(get("/api/v1/nutrition/foods/{foodId}", foodId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String servingId = com.jayway.jsonpath.JsonPath.read(foodResponse, "$.servings[0].servingId");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "SNACK",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T16:30:00Z",
                                  "items": [
                                    {
                                      "foodCatalogItemId": "%s",
                                      "foodServingId": "%s",
                                      "quantity": 2.0
                                    }
                                  ]
                                }
                                """.formatted(foodId, servingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalCalories").value(240))
                .andExpect(jsonPath("$.items[0].foodName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.items[0].unit").value("container"))
                .andExpect(jsonPath("$.items[0].snapshotServingLabel").value("1 container"))
                .andExpect(jsonPath("$.items[0].proteinGrams").value(34.00))
                .andExpect(jsonPath("$.items[0].nutrientDetails.length()").value(3))
                .andExpect(jsonPath("$.items[0].nutrientDetails[0].amount").value(34.00));
    }

    @Test
    void recentAndFrequentFoodsReflectLoggingHistory() throws Exception {
        String token = tokenFor("ivan@example.com", "Ivan");

        // Banana logged in three older meals; Coffee once in the latest meal.
        logSingleItemMeal(token, "Banana", "2026-06-11T08:00:00Z", 105, 1.0);
        logSingleItemMeal(token, "Banana", "2026-06-11T09:00:00Z", 105, 2.0);
        logSingleItemMeal(token, "Banana", "2026-06-11T10:00:00Z", 105, 3.0);
        logSingleItemMeal(token, "Coffee", "2026-06-11T11:00:00Z", 5, 1.0);

        // Recent is recency-ordered, deduped to the latest occurrence (Banana carries qty 3.0).
        mockMvc.perform(get("/api/v1/nutrition/foods/recent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].foodName").value("Coffee"))
                .andExpect(jsonPath("$[0].lastLoggedAt").value("2026-06-11T11:00:00Z"))
                .andExpect(jsonPath("$[1].foodName").value("Banana"))
                .andExpect(jsonPath("$[1].quantity").value(3.0))
                .andExpect(jsonPath("$[1].timesLogged").value(3))
                .andExpect(jsonPath("$[1].lastLoggedAt").value("2026-06-11T10:00:00Z"));

        // Frequent is count-ordered, so the thrice-logged Banana comes first.
        mockMvc.perform(get("/api/v1/nutrition/foods/frequent")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].foodName").value("Banana"))
                .andExpect(jsonPath("$[0].timesLogged").value(3))
                .andExpect(jsonPath("$[0].lastLoggedAt").value("2026-06-11T10:00:00Z"))
                .andExpect(jsonPath("$[1].foodName").value("Coffee"));
    }

    @Test
    void recentFoodsAreScopedToTheCaller() throws Exception {
        String ownerToken = tokenFor("judy@example.com", "Judy");
        logSingleItemMeal(ownerToken, "Almonds", "2026-06-11T08:00:00Z", 160, 1.0);

        String otherToken = tokenFor("kyle@example.com", "Kyle");
        mockMvc.perform(get("/api/v1/nutrition/foods/recent")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private void logSingleItemMeal(String token, String foodName, String consumedAt, int calories, double quantity)
            throws Exception {
        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "SNACK",
                                  "source": "MANUAL",
                                  "consumedAt": "%s",
                                  "items": [
                                    {
                                      "foodName": "%s",
                                      "quantity": %s,
                                      "unit": "serving",
                                      "calories": %d
                                    }
                                  ]
                                }
                                """.formatted(consumedAt, foodName, quantity, calories)))
                .andExpect(status().isCreated());
    }

    @Test
    void adminCanRelayPendingNutritionOutboxEvents() throws Exception {
        String token = tokenFor("dave@example.com", "Dave");

        mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "SNACK",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T16:00:00Z",
                                  "items": [
                                    {
                                      "foodName": "Protein bar",
                                      "quantity": 1.0,
                                      "unit": "bar",
                                      "calories": 210,
                                      "proteinGrams": 20.0,
                                      "carbsGrams": 18.0,
                                      "fatGrams": 7.0
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(user("admin").roles("ADMIN"))
                        .param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBatchSize").value(10))
                .andExpect(jsonPath("$.publishedCount").value(1));

        org.assertj.core.api.Assertions.assertThat(
                        outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PUBLISHED))
                .hasSize(1);
    }

    @Test
    void nutritionPageInsightRequiresAuthenticationAndRejectsAnInvalidTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/nutrition/insights/page"))
                .andExpect(status().isUnauthorized());

        String token = tokenFor("insight-zone@example.com", "Insight Zone");
        mockMvc.perform(get("/api/v1/nutrition/insights/page")
                        .header("Authorization", "Bearer " + token)
                        .param("timezone", "Mars/Olympus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nutritionPageInsightDescribesTheLastFullWeekOfTheSignedInUser() throws Exception {
        String token = tokenFor("insight-week@example.com", "Insight Week");
        UUID userId = UUID.fromString(userIdFromToken(token));
        LocalDate lastMonday = LocalDate.now(java.time.ZoneOffset.UTC)
                .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                .minusWeeks(1);

        MealLogEntity meal = new MealLogEntity();
        meal.setMealId(UUID.randomUUID());
        meal.setUserId(userId);
        meal.setMealType(MealType.BREAKFAST);
        meal.setSource(MealSource.MANUAL);
        meal.setConsumedAt(lastMonday.plusDays(2).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC));
        mealLogRepository.save(meal);
        MealItemEntity item = new MealItemEntity();
        item.setMealItemId(UUID.randomUUID());
        item.setMealId(meal.getMealId());
        item.setFoodName("Oats");
        item.setQuantity(BigDecimal.ONE);
        item.setUnit("bowl");
        item.setCalories(350);
        item.setProteinGrams(new BigDecimal("12.50"));
        mealItemRepository.save(item);

        mockMvc.perform(get("/api/v1/nutrition/insights/page")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.period").value("PREVIOUS_WEEK"))
                .andExpect(jsonPath("$.periodStart").value(lastMonday.toString()))
                .andExpect(jsonPath("$.periodEnd").value(lastMonday.plusDays(6).toString()))
                .andExpect(jsonPath("$.daysLogged").value(1))
                .andExpect(jsonPath("$.proteinPerDayGrams").value(12.5))
                .andExpect(jsonPath("$.caloriesPerDay").value(350))
                .andExpect(jsonPath("$.earlierProteinPerDayGrams").isEmpty())
                .andExpect(jsonPath("$.proteinTargetGrams").isEmpty())
                .andExpect(jsonPath("$.calorieTargetKcal").isEmpty())
                .andExpect(jsonPath("$.proteinByMeal[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.proteinByMeal[0].label").value("Breakfast"))
                .andExpect(jsonPath("$.proteinByMeal[0].gramsPerDay").value(12.5));
    }

    private String tokenFor(String email, String displayName) {
        String userId = UUID.randomUUID().toString();
        return Jwts.builder()
                .claims(Map.of(
                        "userId", userId,
                        "displayName", displayName,
                        "role", "USER"))
                .subject(email)
                .signWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String userIdFromToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor("test-secret-test-secret-test-secret-123456".getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", String.class);
    }

    private void seedGlobalFood(String id, String displayName, String categoryCode, boolean verified) {
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(UUID.fromString(id));
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.OFFICIAL);
        food.setDisplayName(displayName);
        food.setNormalizedName(displayName.toLowerCase(java.util.Locale.ROOT));
        food.setCategoryCode(categoryCode);
        food.setVerified(verified);
        food.setActive(true);
        foodCatalogItemRepository.save(food);
    }

    private void seedBrandlessFoodWithNutrition(String foodId, String servingId, String name, String categoryCode) {
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(UUID.fromString(foodId));
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.IMPORTED);
        food.setDisplayName(name);
        food.setNormalizedName(name.toLowerCase(java.util.Locale.ROOT));
        food.setCategoryCode(categoryCode);
        food.setVerified(false);
        food.setActive(true);
        food.setDefaultServingId(UUID.fromString(servingId));
        foodCatalogItemRepository.save(food);

        FoodServingEntity serving = new FoodServingEntity();
        serving.setServingId(UUID.fromString(servingId));
        serving.setFoodId(UUID.fromString(foodId));
        serving.setLabel("100 g");
        serving.setQuantity(new BigDecimal("100.00"));
        serving.setUnit("g");
        serving.setGramsEquivalent(new BigDecimal("100.00"));
        serving.setSortOrder(1);
        serving.setDefaultServing(true);
        serving.setActive(true);
        foodServingRepository.save(serving);

        FoodNutrientProfileEntity profile = new FoodNutrientProfileEntity();
        profile.setNutrientProfileId(UUID.randomUUID());
        profile.setFoodId(UUID.fromString(foodId));
        profile.setBasisQuantity(new BigDecimal("100.00"));
        profile.setBasisUnit("g");
        profile.setCalories(165);
        profile.setProteinGrams(new BigDecimal("31.00"));
        profile.setActive(true);
        foodNutrientProfileRepository.save(profile);
    }

    private UUID seedFoodCatalog() {
        UUID brandId = UUID.randomUUID();
        FoodBrandEntity brand = new FoodBrandEntity();
        brand.setBrandId(brandId);
        brand.setName("FitBrand");
        brand.setNormalizedName("fitbrand");
        brand.setCountryCode("SG");
        brand.setActive(true);
        foodBrandRepository.save(brand);

        UUID foodId = UUID.randomUUID();
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.OFFICIAL);
        food.setDisplayName("Greek Yogurt");
        food.setNormalizedName("greek yogurt");
        food.setCategoryCode("DAIRY");
        food.setBrandId(brandId);
        food.setVerified(true);
        food.setActive(true);
        foodCatalogItemRepository.save(food);

        FoodServingEntity serving1 = new FoodServingEntity();
        serving1.setServingId(UUID.randomUUID());
        serving1.setFoodId(foodId);
        serving1.setLabel("1 container");
        serving1.setQuantity(new BigDecimal("1.00"));
        serving1.setUnit("container");
        serving1.setGramsEquivalent(new BigDecimal("170.00"));
        serving1.setSortOrder(1);
        serving1.setDefaultServing(true);
        serving1.setActive(true);
        foodServingRepository.save(serving1);

        FoodServingEntity serving2 = new FoodServingEntity();
        serving2.setServingId(UUID.randomUUID());
        serving2.setFoodId(foodId);
        serving2.setLabel("100 g");
        serving2.setQuantity(new BigDecimal("100.00"));
        serving2.setUnit("g");
        serving2.setGramsEquivalent(new BigDecimal("100.00"));
        serving2.setSortOrder(2);
        serving2.setDefaultServing(false);
        serving2.setActive(true);
        foodServingRepository.save(serving2);

        food.setDefaultServingId(serving1.getServingId());
        foodCatalogItemRepository.save(food);

        FoodBarcodeEntity barcode = new FoodBarcodeEntity();
        barcode.setBarcodeId(UUID.randomUUID());
        barcode.setFoodId(foodId);
        barcode.setBarcode("8880001112223");
        barcode.setPrimaryBarcode(true);
        barcode.setActive(true);
        foodBarcodeRepository.save(barcode);

        FoodNutrientProfileEntity profile = new FoodNutrientProfileEntity();
        profile.setNutrientProfileId(UUID.randomUUID());
        profile.setFoodId(foodId);
        profile.setBasisQuantity(new BigDecimal("170.00"));
        profile.setBasisUnit("g");
        profile.setCalories(120);
        profile.setProteinGrams(new BigDecimal("17.00"));
        profile.setCarbsGrams(new BigDecimal("6.00"));
        profile.setFatGrams(new BigDecimal("2.00"));
        profile.setSodiumMg(new BigDecimal("65.00"));
        profile.setSugarGrams(new BigDecimal("5.00"));
        profile.setActive(true);
        foodNutrientProfileRepository.save(profile);

        foodNutrientDetailRepository.save(foodNutrientDetail(foodId, "PROTEIN", "Protein", "17.00", "g", 1));
        foodNutrientDetailRepository.save(foodNutrientDetail(foodId, "SODIUM", "Sodium", "65.00", "mg", 2));
        foodNutrientDetailRepository.save(foodNutrientDetail(foodId, "CALCIUM", "Calcium", "180.00", "mg", 3));
        return foodId;
    }

    private FoodNutrientDetailEntity foodNutrientDetail(UUID foodId,
                                                        String nutrientCode,
                                                        String displayName,
                                                        String amount,
                                                        String unit,
                                                        int sortOrder) {
        FoodNutrientDetailEntity detail = new FoodNutrientDetailEntity();
        detail.setDetailId(UUID.randomUUID());
        detail.setFoodId(foodId);
        detail.setNutrientCode(nutrientCode);
        detail.setDisplayName(displayName);
        detail.setAmount(new BigDecimal(amount));
        detail.setUnit(unit);
        detail.setSortOrder(sortOrder);
        detail.setActive(true);
        return detail;
    }
}
