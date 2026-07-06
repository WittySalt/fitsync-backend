package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Maps an external open-data category string (e.g. a USDA FoodData Central food category or an Open Food
 * Facts category tag) onto an internal {@code food_categories.code}. This is the layer that open data does
 * NOT provide — neither dataset exposes FitSync's curated dietary taxonomy — so analytics food-group
 * analysis ("meat-heavy / veg-light") depends on it. See recommendation-service/nutrition-data-plan.md §5.
 */
@Entity
@Table(name = "external_food_category_map")
public class ExternalFoodCategoryMappingEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "mapping_id", nullable = false)
    private UUID mappingId;

    /** Source dataset identifier, e.g. {@code USDA_SR}, {@code USDA_FOUNDATION}, {@code OFF}. */
    @Column(name = "source_dataset", nullable = false, length = 30)
    private String sourceDataset;

    /** The raw category code/tag as it appears in the source dataset. */
    @Column(name = "source_category_code", nullable = false, length = 120)
    private String sourceCategoryCode;

    /** The internal {@code food_categories.code} this source category maps to. */
    @Column(name = "internal_category_code", nullable = false, length = 40)
    private String internalCategoryCode;

    public UUID getMappingId() {
        return mappingId;
    }

    public void setMappingId(UUID mappingId) {
        this.mappingId = mappingId;
    }

    public String getSourceDataset() {
        return sourceDataset;
    }

    public void setSourceDataset(String sourceDataset) {
        this.sourceDataset = sourceDataset;
    }

    public String getSourceCategoryCode() {
        return sourceCategoryCode;
    }

    public void setSourceCategoryCode(String sourceCategoryCode) {
        this.sourceCategoryCode = sourceCategoryCode;
    }

    public String getInternalCategoryCode() {
        return internalCategoryCode;
    }

    public void setInternalCategoryCode(String internalCategoryCode) {
        this.internalCategoryCode = internalCategoryCode;
    }
}
