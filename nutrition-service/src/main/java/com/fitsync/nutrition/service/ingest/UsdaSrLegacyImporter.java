package com.fitsync.nutrition.service.ingest;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.repository.ExternalFoodCategoryMappingRepository;
import com.fitsync.nutrition.repository.FoodCatalogItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Skeleton importer for USDA FoodData Central — SR Legacy (the small, public-domain, nutrient-complete
 * generic-food dataset used as FitSync's "spine"; see recommendation-service/nutrition-data-plan.md §2-3).
 *
 * <p>Deliberately NOT auto-run: it is a plain service invoked explicitly (a future admin endpoint, a CLI
 * runner, or a test). It does not parse the CSV itself — callers supply already-parsed {@link SrLegacyFood}
 * rows so this class stays free of a CSV-library dependency and focused on the mapping + upsert logic.
 *
 * <p>Idempotent: rows are upserted on {@code (source_dataset, source_ref)} so re-imports do not duplicate.
 *
 * <p><b>TODO (follow-up, not in this skeleton):</b> persist the per-food nutrient profile
 * ({@code FoodNutrientProfileEntity}) and servings; normalise energy/units; populate
 * {@code data_completeness} from actual nutrient presence.
 */
@Service
public class UsdaSrLegacyImporter {

    /** Source-dataset tag stored on every row this importer writes. */
    public static final String SOURCE_DATASET = "USDA_SR";

    private static final Logger log = LoggerFactory.getLogger(UsdaSrLegacyImporter.class);

    private final FoodCatalogItemRepository catalogRepository;
    private final ExternalFoodCategoryMappingRepository categoryMappingRepository;

    public UsdaSrLegacyImporter(FoodCatalogItemRepository catalogRepository,
                                ExternalFoodCategoryMappingRepository categoryMappingRepository) {
        this.catalogRepository = catalogRepository;
        this.categoryMappingRepository = categoryMappingRepository;
    }

    /**
     * Upsert a batch of SR Legacy foods into the catalog. Each food's USDA category is resolved to an
     * internal {@code food_categories.code} via {@code external_food_category_map}; unmapped foods are
     * still imported (so nutrition data is not lost) but counted and logged so the mapping can be extended.
     */
    @Transactional
    public ImportSummary importFoods(Iterable<SrLegacyFood> foods) {
        int imported = 0;
        int updated = 0;
        int unmapped = 0;

        for (SrLegacyFood food : foods) {
            if (food == null || food.fdcId() == null || food.description() == null) {
                continue;
            }
            Optional<String> internalCategory = resolveCategory(food.usdaCategory());
            if (internalCategory.isEmpty() && food.usdaCategory() != null) {
                unmapped++;
            }

            Optional<FoodCatalogItemEntity> existing =
                    catalogRepository.findBySourceDatasetAndSourceRef(SOURCE_DATASET, food.fdcId());
            FoodCatalogItemEntity entity = existing.orElseGet(FoodCatalogItemEntity::new);
            boolean isNew = existing.isEmpty();
            if (isNew) {
                entity.setFoodId(UUID.randomUUID());
                entity.setScope(FoodCatalogScope.GLOBAL);
                entity.setSource(FoodCatalogSource.IMPORTED);
                entity.setSourceDataset(SOURCE_DATASET);
                entity.setSourceRef(food.fdcId());
                entity.setVerified(true);
            }
            entity.setDisplayName(truncate(food.description(), 120));
            entity.setNormalizedName(truncate(food.description().toLowerCase(Locale.ROOT), 120));
            internalCategory.ifPresent(entity::setCategoryCode);
            entity.setDataCompleteness(food.dataCompleteness());

            catalogRepository.save(entity);
            if (isNew) {
                imported++;
            } else {
                updated++;
            }
        }

        ImportSummary summary = new ImportSummary(imported, updated, unmapped);
        log.info("USDA SR Legacy import complete: {}", summary);
        return summary;
    }

    private Optional<String> resolveCategory(String usdaCategory) {
        if (usdaCategory == null || usdaCategory.isBlank()) {
            return Optional.empty();
        }
        return categoryMappingRepository
                .findBySourceDatasetAndSourceCategoryCode(SOURCE_DATASET, usdaCategory)
                .map(mapping -> mapping.getInternalCategoryCode());
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Minimal SR Legacy row. The real CSV carries far more (full nutrient set, portions); this is the
     * subset this skeleton needs. {@code dataCompleteness} (0.000-1.000) may be supplied by the parser.
     */
    public record SrLegacyFood(String fdcId,
                               String description,
                               String usdaCategory,
                               BigDecimal dataCompleteness) {
    }

    /** Outcome of an import batch. */
    public record ImportSummary(int imported, int updated, int unmapped) {
    }
}
