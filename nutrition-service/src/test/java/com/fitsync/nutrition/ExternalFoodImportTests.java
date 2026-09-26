package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import com.fitsync.nutrition.entity.FoodBarcodeEntity;
import com.fitsync.nutrition.entity.FoodBrandEntity;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import com.fitsync.nutrition.entity.FoodServingEntity;
import com.fitsync.nutrition.entity.MealItemEntity;
import com.fitsync.nutrition.external.ExternalFoodSource;
import com.fitsync.nutrition.repository.FoodBarcodeRepository;
import com.fitsync.nutrition.repository.FoodBrandRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.repository.FoodNutrientDetailRepository;
import com.fitsync.nutrition.repository.FoodNutrientProfileRepository;
import com.fitsync.nutrition.repository.FoodServingRepository;
import com.fitsync.nutrition.repository.MealItemRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExternalFoodImportTests {

    private static final String BARCODE = "737628064502";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestExternalFoodSource externalFoodSource;

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

    @Autowired
    private MealItemRepository mealItemRepository;

    @BeforeEach
    void clearData() {
        externalFoodSource.reset();
        foodNutrientDetailRepository.deleteAll();
        foodBarcodeRepository.deleteAll();
        foodServingRepository.deleteAll();
        foodNutrientProfileRepository.deleteAll();
        foodCatalogItemRepository.deleteAll();
        foodBrandRepository.deleteAll();
    }

    @Test
    void importBarcodePersistsExternalFoodAndReimportReturnsExistingFood() throws Exception {
        externalFoodSource.addBarcodeResult(BARCODE, externalFood(BARCODE, "Greek Yogurt", "FAGE", "DAIRY", 59));
        String token = tokenFor("off-import@example.com", "OFF Import");

        String importResponse = mockMvc.perform(post("/api/v1/nutrition/foods/import/barcode/{barcode}", BARCODE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodId").exists())
                .andExpect(jsonPath("$.displayName").value("Greek Yogurt"))
                .andExpect(jsonPath("$.brandName").value("FAGE"))
                .andExpect(jsonPath("$.categoryCode").value("DAIRY"))
                .andExpect(jsonPath("$.source").value("IMPORTED"))
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.calories").value(59))
                .andExpect(jsonPath("$.primaryBarcode").value(BARCODE))
                .andExpect(jsonPath("$.novaGroup").value(1))
                .andExpect(jsonPath("$.nutriScore").value("A"))
                .andExpect(jsonPath("$.servings.length()").value(1))
                .andExpect(jsonPath("$.servings[0].label").value("100 g"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String foodId = com.jayway.jsonpath.JsonPath.read(importResponse, "$.foodId");

        assertThat(foodCatalogItemRepository.count()).isEqualTo(1);
        // FD-4: OFF provenance + diet-quality signals are captured on the persisted catalog row.
        FoodCatalogItemEntity persisted = foodCatalogItemRepository
                .findBySourceDatasetAndSourceRef("OFF", BARCODE).orElseThrow();
        assertThat(persisted.getNovaGroup()).isEqualTo(1);
        assertThat(persisted.getNutriScore()).isEqualTo("A");
        assertThat(foodNutrientProfileRepository.count()).isEqualTo(1);
        assertThat(foodServingRepository.count()).isEqualTo(1);
        assertThat(foodBarcodeRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/nutrition/foods/import/barcode/{barcode}", BARCODE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodId").value(foodId));
        assertThat(foodCatalogItemRepository.count()).isEqualTo(1);
        assertThat(foodBarcodeRepository.count()).isEqualTo(1);

        externalFoodSource.reset();
        mockMvc.perform(get("/api/v1/nutrition/barcodes/{barcode}", BARCODE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.foodId").value(foodId))
                .andExpect(jsonPath("$.source").value("IMPORTED"));
        assertThat(externalFoodSource.barcodeLookupCount()).isZero();
    }

    @Test
    void importMissingExternalBarcodeReturnsNotFoundAndSearchRemainsLocalOnly() throws Exception {
        String token = tokenFor("off-disabled@example.com", "OFF Disabled");

        mockMvc.perform(post("/api/v1/nutrition/foods/import/barcode/{barcode}", "0000000000000")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "not available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void importExternalFoodWithoutCaloriesIsRejected() throws Exception {
        externalFoodSource.addBarcodeResult(BARCODE, externalFood(BARCODE, "No Calories", "OFF", "OTHER", null));
        String token = tokenFor("off-invalid@example.com", "OFF Invalid");

        mockMvc.perform(post("/api/v1/nutrition/foods/import/barcode/{barcode}", BARCODE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertThat(foodCatalogItemRepository.count()).isZero();
        assertThat(foodBarcodeRepository.count()).isZero();
    }

    @Test
    void searchFallbackAppendsExternalResultsAfterLocalResults() throws Exception {
        UUID localFoodId = seedLocalFood("Greek Yogurt Local", "8880001112223");
        externalFoodSource.setSearchResults(List.of(
                new FoodSearchResultView(
                        null,
                        "Greek Yogurt External",
                        "FAGE",
                        "DAIRY",
                        null,
                        FoodCatalogScope.GLOBAL,
                        FoodCatalogSource.IMPORTED,
                        false,
                        59,
                        "100 g",
                        BARCODE)));
        String token = tokenFor("off-search@example.com", "OFF Search");

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "Greek Yogurt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].foodId").value(localFoodId.toString()))
                .andExpect(jsonPath("$.content[0].displayName").value("Greek Yogurt Local"))
                .andExpect(jsonPath("$.content[1].foodId").doesNotExist())
                .andExpect(jsonPath("$.content[1].displayName").value("Greek Yogurt External"))
                .andExpect(jsonPath("$.content[1].source").value("IMPORTED"))
                .andExpect(jsonPath("$.content[1].primaryBarcode").value(BARCODE));
        assertThat(externalFoodSource.searchCount()).isEqualTo(1);
    }

    @Test
    void searchFallbackFailureReturnsLocalResultsOnly() throws Exception {
        String token = tokenFor("off-search-failure@example.com", "OFF Search Failure");
        externalFoodSource.failSearch();

        mockMvc.perform(get("/api/v1/nutrition/foods/search")
                        .header("Authorization", "Bearer " + token)
                        .param("q", "external only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        assertThat(externalFoodSource.searchCount()).isEqualTo(1);
    }

    private UUID seedLocalFood(String displayName, String barcode) {
        UUID foodId = UUID.randomUUID();
        FoodCatalogItemEntity food = new FoodCatalogItemEntity();
        food.setFoodId(foodId);
        food.setScope(FoodCatalogScope.GLOBAL);
        food.setSource(FoodCatalogSource.OFFICIAL);
        food.setCreationMode(FoodCreationMode.MANUAL);
        food.setDisplayName(displayName);
        food.setNormalizedName(displayName.toLowerCase(java.util.Locale.ROOT));
        food.setCategoryCode("DAIRY");
        food.setVerified(true);
        food.setActive(true);
        foodCatalogItemRepository.save(food);

        FoodNutrientProfileEntity profile = new FoodNutrientProfileEntity();
        profile.setNutrientProfileId(UUID.randomUUID());
        profile.setFoodId(foodId);
        profile.setBasisQuantity(new BigDecimal("100.00"));
        profile.setBasisUnit("g");
        profile.setCalories(100);
        profile.setActive(true);
        foodNutrientProfileRepository.save(profile);

        FoodServingEntity serving = new FoodServingEntity();
        serving.setServingId(UUID.randomUUID());
        serving.setFoodId(foodId);
        serving.setLabel("100 g");
        serving.setQuantity(new BigDecimal("100.00"));
        serving.setUnit("g");
        serving.setGramsEquivalent(new BigDecimal("100.00"));
        serving.setSortOrder(1);
        serving.setDefaultServing(true);
        serving.setActive(true);
        foodServingRepository.save(serving);

        food.setDefaultServingId(serving.getServingId());
        foodCatalogItemRepository.save(food);

        FoodBarcodeEntity barcodeEntity = new FoodBarcodeEntity();
        barcodeEntity.setBarcodeId(UUID.randomUUID());
        barcodeEntity.setFoodId(foodId);
        barcodeEntity.setBarcode(barcode);
        barcodeEntity.setPrimaryBarcode(true);
        barcodeEntity.setActive(true);
        foodBarcodeRepository.save(barcodeEntity);
        return foodId;
    }

    @Test
    void loggingCatalogFoodSnapshotsClassificationOntoMealItem() throws Exception {
        externalFoodSource.addBarcodeResult(BARCODE,
                externalFood(BARCODE, "Greek Yogurt", "FAGE", "DAIRY", 59));
        String token = tokenFor("snapshot@example.com", "Snapshot User");

        String importResponse = mockMvc.perform(post("/api/v1/nutrition/foods/import/barcode/{barcode}", BARCODE)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String foodId = com.jayway.jsonpath.JsonPath.read(importResponse, "$.foodId");

        String mealResponse = mockMvc.perform(post("/api/v1/nutrition/meals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("""
                                {
                                  "mealType": "BREAKFAST",
                                  "name": "Yogurt",
                                  "source": "MANUAL",
                                  "consumedAt": "2026-06-11T02:00:00Z",
                                  "timezone": "UTC",
                                  "items": [ { "foodCatalogItemId": "%s", "quantity": 1.0, "unit": "serving" } ]
                                }
                                """.formatted(foodId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mealId = com.jayway.jsonpath.JsonPath.read(mealResponse, "$.mealId");

        List<MealItemEntity> items =
                mealItemRepository.findByMealIdOrderByDateTimeCreatedAsc(java.util.UUID.fromString(mealId));
        assertThat(items).hasSize(1);
        MealItemEntity item = items.get(0);
        // SNAP-3: classification snapshot copied from the referenced catalog food at log time.
        assertThat(item.getCategoryCode()).isEqualTo("DAIRY");
        assertThat(item.getNovaGroup()).isEqualTo(1);
        assertThat(item.getNutriScore()).isEqualTo("A");
        assertThat(item.getSourceDataset()).isEqualTo("OFF");
    }

    private FoodDetailView externalFood(String barcode,
                                        String displayName,
                                        String brandName,
                                        String categoryCode,
                                        Integer calories) {
        return new FoodDetailView(
                null,
                displayName,
                displayName.toLowerCase(java.util.Locale.ROOT),
                brandName,
                categoryCode,
                FoodCatalogScope.GLOBAL,
                null,
                FoodCreationMode.MANUAL,
                FoodCatalogSource.IMPORTED,
                false,
                null,
                calories,
                new BigDecimal("10.30"),
                new BigDecimal("3.60"),
                new BigDecimal("0.40"),
                null,
                null,
                BigDecimal.ZERO,
                new BigDecimal("3.20"),
                new BigDecimal("36.00"),
                null,
                null,
                new BigDecimal("100.00"),
                "g",
                barcode,
                1,
                "A",
                "OFF",
                List.of(),
                List.of());
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

    @TestConfiguration
    static class ExternalFoodImportTestConfig {

        @Bean
        @Primary
        TestExternalFoodSource testExternalFoodSource() {
            return new TestExternalFoodSource();
        }
    }

    static class TestExternalFoodSource implements ExternalFoodSource {

        private final Map<String, FoodDetailView> barcodeResults = new HashMap<>();
        private List<FoodSearchResultView> searchResults = List.of();
        private boolean failSearch;
        private int barcodeLookupCount;
        private int searchCount;

        @Override
        public Optional<FoodDetailView> findByBarcode(String barcode) {
            barcodeLookupCount++;
            return Optional.ofNullable(barcodeResults.get(barcode));
        }

        @Override
        public List<FoodSearchResultView> search(String query, int limit) {
            searchCount++;
            if (failSearch) {
                throw new IllegalStateException("search unavailable");
            }
            return searchResults.stream()
                    .limit(limit)
                    .toList();
        }

        void addBarcodeResult(String barcode, FoodDetailView food) {
            barcodeResults.put(barcode, food);
        }

        void setSearchResults(List<FoodSearchResultView> results) {
            searchResults = List.copyOf(results);
        }

        void failSearch() {
            failSearch = true;
        }

        int barcodeLookupCount() {
            return barcodeLookupCount;
        }

        int searchCount() {
            return searchCount;
        }

        void reset() {
            barcodeResults.clear();
            searchResults = List.of();
            failSearch = false;
            barcodeLookupCount = 0;
            searchCount = 0;
        }
    }
}
