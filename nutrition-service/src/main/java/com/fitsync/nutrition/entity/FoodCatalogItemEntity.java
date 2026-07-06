package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.domain.FoodCreationMode;
import com.fitsync.nutrition.domain.FoodCatalogSource;
import com.fitsync.persistence.entity.BaseEffectiveEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_catalog_items")
public class FoodCatalogItemEntity extends BaseEffectiveEntity {

    @Id
    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private FoodCatalogScope scope;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "base_food_id")
    private UUID baseFoodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "creation_mode", nullable = false, length = 20)
    private FoodCreationMode creationMode = FoodCreationMode.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private FoodCatalogSource source;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(name = "category_code", length = 40)
    private String categoryCode;

    @Column(name = "brand_id")
    private UUID brandId;

    @Column(name = "default_serving_id")
    private UUID defaultServingId;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "density_grams_per_ml", precision = 10, scale = 4)
    private BigDecimal densityGramsPerMl;

    // --- Open-data provenance & diet-quality signals (additive; nullable) ---------------------------
    // Populated by external-data importers (USDA FoodData Central, Open Food Facts). Kept nullable so
    // existing/seeded rows and manually-created foods remain valid. See
    // recommendation-service/nutrition-data-plan.md.

    @Column(name = "source_dataset", length = 30)
    private String sourceDataset;

    @Column(name = "source_ref", length = 120)
    private String sourceRef;

    @Column(name = "nova_group")
    private Integer novaGroup;

    @Column(name = "nutri_score", length = 1)
    private String nutriScore;

    @Column(name = "data_completeness", precision = 4, scale = 3)
    private BigDecimal dataCompleteness;

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }

    public FoodCatalogScope getScope() {
        return scope;
    }

    public void setScope(FoodCatalogScope scope) {
        this.scope = scope;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(UUID ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public UUID getBaseFoodId() {
        return baseFoodId;
    }

    public void setBaseFoodId(UUID baseFoodId) {
        this.baseFoodId = baseFoodId;
    }

    public FoodCreationMode getCreationMode() {
        return creationMode;
    }

    public void setCreationMode(FoodCreationMode creationMode) {
        this.creationMode = creationMode;
    }

    public FoodCatalogSource getSource() {
        return source;
    }

    public void setSource(FoodCatalogSource source) {
        this.source = source;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public UUID getBrandId() {
        return brandId;
    }

    public void setBrandId(UUID brandId) {
        this.brandId = brandId;
    }

    public UUID getDefaultServingId() {
        return defaultServingId;
    }

    public void setDefaultServingId(UUID defaultServingId) {
        this.defaultServingId = defaultServingId;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public BigDecimal getDensityGramsPerMl() {
        return densityGramsPerMl;
    }

    public void setDensityGramsPerMl(BigDecimal densityGramsPerMl) {
        this.densityGramsPerMl = densityGramsPerMl;
    }

    public String getSourceDataset() {
        return sourceDataset;
    }

    public void setSourceDataset(String sourceDataset) {
        this.sourceDataset = sourceDataset;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public Integer getNovaGroup() {
        return novaGroup;
    }

    public void setNovaGroup(Integer novaGroup) {
        this.novaGroup = novaGroup;
    }

    public String getNutriScore() {
        return nutriScore;
    }

    public void setNutriScore(String nutriScore) {
        this.nutriScore = nutriScore;
    }

    public BigDecimal getDataCompleteness() {
        return dataCompleteness;
    }

    public void setDataCompleteness(BigDecimal dataCompleteness) {
        this.dataCompleteness = dataCompleteness;
    }
}
