package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import com.fitsync.nutrition.service.ingest.UsdaSrLegacyCsvParser;
import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.SrLegacyFood;
import org.junit.jupiter.api.Test;

class UsdaSrLegacyCsvParserTests {

    private final UsdaSrLegacyCsvParser parser = new UsdaSrLegacyCsvParser();

    @Test
    void joinsCategoryHandlesQuotedCommasAndSkipsNonSrLegacyRows() throws IOException {
        String foodCategoryCsv = """
                id,code,description
                100,"0100","Dairy and Egg Products"
                500,"0500","Poultry Products"
                """;
        String foodCsv = """
                fdc_id,data_type,description,food_category_id,publication_date
                1001,sr_legacy_food,"Butter, salted",100,2019-04-01
                5006,sr_legacy_food,"Chicken, broilers or fryers, breast, raw",500,2019-04-01
                9999,foundation_food,"Foundation food should be skipped",100,2020-01-01
                """;

        List<SrLegacyFood> foods = parser.parse(new StringReader(foodCsv),
                new StringReader(foodCategoryCsv));

        assertThat(foods).hasSize(2);

        SrLegacyFood butter = foods.get(0);
        assertThat(butter.fdcId()).isEqualTo("1001");
        // The comma inside the quoted description must be preserved as one field.
        assertThat(butter.description()).isEqualTo("Butter, salted");
        assertThat(butter.usdaCategory()).isEqualTo("Dairy and Egg Products");

        SrLegacyFood chicken = foods.get(1);
        assertThat(chicken.fdcId()).isEqualTo("5006");
        assertThat(chicken.usdaCategory()).isEqualTo("Poultry Products");
    }

    @Test
    void foodWithoutCategoryYieldsNullCategory() throws IOException {
        String foodCategoryCsv = "id,code,description\n";
        String foodCsv = """
                fdc_id,data_type,description,food_category_id,publication_date
                2001,sr_legacy_food,"Water, bottled",,2019-04-01
                """;

        List<SrLegacyFood> foods = parser.parse(new StringReader(foodCsv),
                new StringReader(foodCategoryCsv));

        assertThat(foods).hasSize(1);
        assertThat(foods.get(0).usdaCategory()).isNull();
    }
}
