package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter;
import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.ImportSummary;
import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.SrLegacyFood;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UsdaSrLegacyImporterTests {

    @Autowired
    private UsdaSrLegacyImporter importer;

    @Autowired
    private FoodCatalogItemRepository catalogRepository;

    @Test
    void resolvesSeededCategoryCountsUnmappedAndUpsertsIdempotently() {
        List<SrLegacyFood> foods = List.of(
                new SrLegacyFood("sr-1001", "Butter, salted", "Dairy and Egg Products", null),
                new SrLegacyFood("sr-5006", "Chicken, broilers, breast", "Poultry Products", null),
                // "Spices and Herbs" is intentionally NOT in the seeded map -> should count as unmapped.
                new SrLegacyFood("sr-2001", "Spices, allspice, ground", "Spices and Herbs", null));

        ImportSummary first = importer.importFoods(foods);

        assertThat(first.imported()).isEqualTo(3);
        assertThat(first.updated()).isZero();
        assertThat(first.unmapped()).isEqualTo(1);

        FoodCatalogItemEntity poultry = catalogRepository
                .findBySourceDatasetAndSourceRef(UsdaSrLegacyImporter.SOURCE_DATASET, "sr-5006")
                .orElseThrow();
        assertThat(poultry.getCategoryCode()).isEqualTo("MEAT_POULTRY");
        assertThat(poultry.getSourceDataset()).isEqualTo("USDA_SR");
        assertThat(poultry.getDisplayName()).isEqualTo("Chicken, broilers, breast");

        FoodCatalogItemEntity butter = catalogRepository
                .findBySourceDatasetAndSourceRef(UsdaSrLegacyImporter.SOURCE_DATASET, "sr-1001")
                .orElseThrow();
        assertThat(butter.getCategoryCode()).isEqualTo("DAIRY");

        // Unmapped food is still imported (data not lost), just without a category.
        FoodCatalogItemEntity spice = catalogRepository
                .findBySourceDatasetAndSourceRef(UsdaSrLegacyImporter.SOURCE_DATASET, "sr-2001")
                .orElseThrow();
        assertThat(spice.getCategoryCode()).isNull();

        // Re-import the same batch -> upsert, not duplicate.
        ImportSummary second = importer.importFoods(foods);
        assertThat(second.imported()).isZero();
        assertThat(second.updated()).isEqualTo(3);
    }
}
