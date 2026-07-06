package com.fitsync.nutrition.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.config.ExternalFoodProperties;
import com.fitsync.nutrition.dto.FoodDetailView;
import com.fitsync.nutrition.dto.FoodSearchResultView;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenFoodFactsFoodSourceHttpTests {

    private static final String BASE_URL = "https://world.openfoodfacts.test";
    private static final String USER_AGENT = "FitSyncTests/1.0";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void findByBarcodeFetchesProductAndMapsFoodDetail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenFoodFactsFoodSource source = new OpenFoodFactsFoodSource(enabledProperties(), builder);
        server.expect(once(), requestTo(BASE_URL
                        + "/api/v2/product/737628064502.json"
                        + "?fields=code,product_name,brands,categories_tags,categories,nutriments,"
                        + "nova_group,nutriscore_grade"))
                .andExpect(header("User-Agent", USER_AGENT))
                .andRespond(withSuccess("""
                        {
                          "status": 1,
                          "product": {
                            "code": "737628064502",
                            "product_name": "Greek Yogurt",
                            "brands": "FAGE, Total",
                            "categories_tags": ["en:yogurts"],
                            "nova_group": 3,
                            "nutriscore_grade": "b",
                            "nutriments": {
                              "energy-kcal_100g": "59.4",
                              "proteins_100g": "10.25",
                              "carbohydrates_100g": "3.62",
                              "fat_100g": "0.42",
                              "saturated-fat_100g": "0.10",
                              "fiber_100g": "0",
                              "sugars_100g": "3.24",
                              "sodium_100g": "0.036"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        FoodDetailView detail = source.findByBarcode("737628064502").orElseThrow();

        assertThat(detail.displayName()).isEqualTo("Greek Yogurt");
        assertThat(detail.brandName()).isEqualTo("FAGE");
        assertThat(detail.categoryCode()).isEqualTo("DAIRY");
        assertThat(detail.calories()).isEqualTo(59);
        assertThat(detail.proteinGrams()).isEqualByComparingTo("10.25");
        assertThat(detail.sodiumMg()).isEqualByComparingTo("36.00");
        assertThat(detail.primaryBarcode()).isEqualTo("737628064502");
        assertThat(detail.novaGroup()).isEqualTo(3);
        assertThat(detail.nutriScore()).isEqualTo("B");
        server.verify();
    }

    @Test
    void findByBarcodeReturnsEmptyForMissingDisabledBlankAndMalformedResponses() {
        assertThat(new OpenFoodFactsFoodSource(disabledProperties()).findByBarcode("737628064502")).isEmpty();
        assertThat(new OpenFoodFactsFoodSource(enabledProperties()).findByBarcode(" ")).isEmpty();

        RestClient.Builder notFoundBuilder = RestClient.builder();
        MockRestServiceServer notFoundServer = MockRestServiceServer.bindTo(notFoundBuilder).build();
        OpenFoodFactsFoodSource notFoundSource = new OpenFoodFactsFoodSource(enabledProperties(), notFoundBuilder);
        notFoundServer.expect(once(), requestTo(BASE_URL
                        + "/api/v2/product/0000000000000.json"
                        + "?fields=code,product_name,brands,categories_tags,categories,nutriments,"
                        + "nova_group,nutriscore_grade"))
                .andRespond(withResourceNotFound());
        assertThat(notFoundSource.findByBarcode("0000000000000")).isEmpty();
        notFoundServer.verify();

        RestClient.Builder malformedBuilder = RestClient.builder();
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        OpenFoodFactsFoodSource malformedSource = new OpenFoodFactsFoodSource(enabledProperties(), malformedBuilder);
        malformedServer.expect(once(), requestTo(BASE_URL
                        + "/api/v2/product/1111111111111.json"
                        + "?fields=code,product_name,brands,categories_tags,categories,nutriments,"
                        + "nova_group,nutriscore_grade"))
                .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));
        assertThat(malformedSource.findByBarcode("1111111111111")).isEmpty();
        malformedServer.verify();
    }

    @Test
    void searchReturnsUniqueResultsUpToRequestedLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenFoodFactsFoodSource source = new OpenFoodFactsFoodSource(enabledProperties(), builder);
        server.expect(once(), requestTo(BASE_URL
                        + "/cgi/search.pl?search_terms=oat%20milk&search_simple=1&action=process&json=1"
                        + "&page_size=2&fields=code,product_name,brands,categories_tags,categories,"
                        + "nutriments,nova_group,nutriscore_grade"))
                .andRespond(withSuccess("""
                        {
                          "products": [
                            {
                              "code": "123",
                              "product_name": "Oat Milk",
                              "brands": "Oatly",
                              "categories_tags": ["en:beverages"],
                              "nutriments": {"energy-kcal_100g": 45}
                            },
                            {
                              "code": "123",
                              "product_name": "Duplicate Oat Milk",
                              "brands": "Oatly",
                              "categories_tags": ["en:beverages"],
                              "nutriments": {"energy-kcal_100g": 46}
                            },
                            {
                              "code": "456",
                              "product_name": "Almond Drink",
                              "brands": "Blue Diamond",
                              "categories": "en:nuts,en:beverages",
                              "nutriments": {"energy-kcal_100g": 30}
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<FoodSearchResultView> results = source.search("oat milk", 2);

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(FoodSearchResultView::primaryBarcode, FoodSearchResultView::displayName)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("123", "Oat Milk"),
                        org.assertj.core.api.Assertions.tuple("456", "Almond Drink"));
        assertThat(results.get(0).basisLabel()).isEqualTo("100 g");
        server.verify();
    }

    @Test
    void searchReturnsEmptyForInvalidInputsServerErrorsAndMalformedProducts() {
        OpenFoodFactsFoodSource disabled = new OpenFoodFactsFoodSource(disabledProperties());
        assertThat(disabled.search("oat", 5)).isEmpty();
        assertThat(new OpenFoodFactsFoodSource(enabledProperties()).search(" ", 5)).isEmpty();
        assertThat(new OpenFoodFactsFoodSource(enabledProperties()).search("oat", 0)).isEmpty();

        RestClient.Builder errorBuilder = RestClient.builder();
        MockRestServiceServer errorServer = MockRestServiceServer.bindTo(errorBuilder).build();
        OpenFoodFactsFoodSource errorSource = new OpenFoodFactsFoodSource(enabledProperties(), errorBuilder);
        errorServer.expect(once(), requestTo(BASE_URL
                        + "/cgi/search.pl?search_terms=oat&search_simple=1&action=process&json=1"
                        + "&page_size=3&fields=code,product_name,brands,categories_tags,categories,"
                        + "nutriments,nova_group,nutriscore_grade"))
                .andRespond(withServerError());
        assertThat(errorSource.search("oat", 3)).isEmpty();
        errorServer.verify();

        RestClient.Builder malformedBuilder = RestClient.builder();
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        OpenFoodFactsFoodSource malformedSource = new OpenFoodFactsFoodSource(enabledProperties(), malformedBuilder);
        malformedServer.expect(once(), requestTo(BASE_URL
                        + "/cgi/search.pl?search_terms=oat&search_simple=1&action=process&json=1"
                        + "&page_size=3&fields=code,product_name,brands,categories_tags,categories,"
                        + "nutriments,nova_group,nutriscore_grade"))
                .andRespond(withSuccess("{\"products\":{}}", MediaType.APPLICATION_JSON));
        assertThat(malformedSource.search("oat", 3)).isEmpty();
        malformedServer.verify();
    }

    @Test
    void mapsKnownOpenFoodFactsCategoriesToCatalogCategories() throws JsonProcessingException {
        OpenFoodFactsFoodSource source = new OpenFoodFactsFoodSource(enabledProperties());

        assertCategory(source, "en:chicken", "MEAT_POULTRY");
        assertCategory(source, "en:seafood", "SEAFOOD");
        assertCategory(source, "en:eggs", "EGGS");
        assertCategory(source, "en:tofu", "PLANT_PROTEIN");
        assertCategory(source, "en:breads", "GRAINS");
        assertCategory(source, "en:vegetables", "VEGETABLES");
        assertCategory(source, "en:fruits", "FRUITS");
        assertCategory(source, "en:seeds", "NUTS_SEEDS");
        assertCategory(source, "en:oils", "FATS_OILS");
        assertCategory(source, "en:snacks", "SNACKS");
        assertCategory(source, "en:chocolates", "SWEETS");
        assertCategory(source, "en:soups", "SOUPS_SAUCES");
        assertCategory(source, "en:herbs", "SPICES");
        assertCategory(source, "en:prepared-meals", "PREPARED");
    }

    @Test
    void mapsMissingOpenFoodFactsFieldsToSafeDefaults() throws JsonProcessingException {
        OpenFoodFactsFoodSource source = new OpenFoodFactsFoodSource(enabledProperties());

        FoodDetailView detail = source.toFoodDetailView(OBJECT_MAPPER.readTree("""
                {
                  "product_name": " ",
                  "brands": " ",
                  "categories_tags": ["en:uncategorized"],
                  "nutriments": {
                    "energy-kcal_100g": "not-a-number",
                    "proteins_100g": {},
                    "sodium_100g": "not-a-number"
                  }
                }
                """), "999");

        assertThat(detail.displayName()).isEqualTo("Unknown product");
        assertThat(detail.normalizedName()).isEqualTo("unknown product");
        assertThat(detail.brandName()).isNull();
        assertThat(detail.categoryCode()).isEqualTo("OTHER");
        assertThat(detail.calories()).isNull();
        assertThat(detail.proteinGrams()).isNull();
        assertThat(detail.sodiumMg()).isNull();
        assertThat(detail.primaryBarcode()).isEqualTo("999");
    }

    private static ExternalFoodProperties enabledProperties() {
        return new ExternalFoodProperties(
                true,
                BASE_URL,
                USER_AGENT,
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ZERO);
    }

    private static ExternalFoodProperties disabledProperties() {
        return new ExternalFoodProperties(
                false,
                BASE_URL,
                USER_AGENT,
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ZERO);
    }

    private static void assertCategory(OpenFoodFactsFoodSource source, String tag, String expectedCategory)
            throws JsonProcessingException {
        FoodDetailView detail = source.toFoodDetailView(OBJECT_MAPPER.readTree("""
                {
                  "product_name": "Mapped Food",
                  "categories_tags": ["%s"],
                  "nutriments": {}
                }
                """.formatted(tag)), tag);

        assertThat(detail.categoryCode()).isEqualTo(expectedCategory);
    }
}
