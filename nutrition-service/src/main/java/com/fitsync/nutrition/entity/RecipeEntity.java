package com.fitsync.nutrition.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

// A deleted recipe keeps its row and is left out of every read, like a deleted meal.
@Entity
@Table(name = "recipes")
@SQLRestriction("deleted_at is null")
public class RecipeEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "recipe_id", nullable = false)
    private UUID recipeId;

    // Null for a system recipe, which belongs to nobody.
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private RecipeScope scope = RecipeScope.USER;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "servings", nullable = false, precision = 10, scale = 2)
    private BigDecimal servings;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "source_ref", length = 60)
    private String sourceRef;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_credit", length = 120)
    private String imageCredit;

    @Column(name = "category", length = 60)
    private String category;

    @Column(name = "cuisine", length = 60)
    private String cuisine;

    @Column(name = "prep_minutes")
    private Integer prepMinutes;

    @Column(name = "published", nullable = false)
    private boolean published = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(UUID recipeId) {
        this.recipeId = recipeId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public RecipeScope getScope() {
        return scope;
    }

    public void setScope(RecipeScope scope) {
        this.scope = scope;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getServings() {
        return servings;
    }

    public void setServings(BigDecimal servings) {
        this.servings = servings;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImageCredit() {
        return imageCredit;
    }

    public void setImageCredit(String imageCredit) {
        this.imageCredit = imageCredit;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCuisine() {
        return cuisine;
    }

    public void setCuisine(String cuisine) {
        this.cuisine = cuisine;
    }

    public Integer getPrepMinutes() {
        return prepMinutes;
    }

    public void setPrepMinutes(Integer prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
