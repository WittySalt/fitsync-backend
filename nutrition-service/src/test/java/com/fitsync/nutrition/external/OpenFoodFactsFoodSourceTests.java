package com.fitsync.nutrition.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.nutrition.config.ExternalFoodProperties;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.dto.FoodDetailView;
import org.junit.jupiter.api.Test;

class OpenFoodFactsFoodSourceTests {

    private final OpenFoodFactsFoodSource source =
            new OpenFoodFactsFoodSource(new ExternalFoodProperties(false, null, null, null, null, null));

    @Test
    void mapsProductNutrimentsToFoodDetailView() throws Exception {
        String json = """
                {
                  "status": 1,
                  "product": {
                    "code": "737628064502",
                    "product_name": "Greek Yogurt",
                    "brands": "FAGE, Total",
                    "categories_tags": ["en:yogurts", "en:dairies"],
                    "nutriments": {
                      "energy-kcal_100g": 59,
                      "proteins_100g": 10.3,
                      "carbohydrates_100g": 3.6,
                      "fat_100g": 0.4,
                      "fiber_100g": 0,
                      "sugars_100g": 3.2,
                      "sodium_100g": 0.036
                    }
                  }
                }
                """;
        var product = new ObjectMapper().readTree(json).path("product");

        FoodDetailView view = source.toFoodDetailView(product, "737628064502");

        assertThat(view.foodId()).isNull();
        assertThat(view.displayName()).isEqualTo("Greek Yogurt");
        assertThat(view.brandName()).isEqualTo("FAGE");
        assertThat(view.categoryCode()).isEqualTo("DAIRY");
        assertThat(view.source()).isEqualTo(FoodCatalogSource.IMPORTED);
        assertThat(view.calories()).isEqualTo(59);
        assertThat(view.proteinGrams()).isEqualByComparingTo("10.30");
        assertThat(view.carbsGrams()).isEqualByComparingTo("3.60");
        assertThat(view.fatGrams()).isEqualByComparingTo("0.40");
        assertThat(view.sugarGrams()).isEqualByComparingTo("3.20");
        // sodium 0.036 g -> 36 mg (no premature rounding).
        assertThat(view.sodiumMg()).isEqualByComparingTo("36.00");
        assertThat(view.basisQuantity()).isEqualByComparingTo("100");
        assertThat(view.basisUnit()).isEqualTo("g");
        assertThat(view.primaryBarcode()).isEqualTo("737628064502");
        assertThat(view.servings()).isEmpty();
    }

    @Test
    void disabledSourceReturnsEmpty() {
        assertThat(source.findByBarcode("737628064502")).isEmpty();
    }
}
