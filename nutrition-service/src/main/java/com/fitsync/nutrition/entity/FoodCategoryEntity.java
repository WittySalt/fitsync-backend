package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A node in the curated food category taxonomy (adjacency list). {@code parentId} is null for a
 * top-level group. {@code path} is a denormalized materialized path (e.g. {@code /proteins/meat_poultry/})
 * so subtree filtering is a single portable {@code path LIKE :prefix%} query. Foods link to a leaf
 * node via {@code food_catalog_items.category_code == code}.
 */
@Entity
@Table(name = "food_categories")
public class FoodCategoryEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "path", nullable = false, length = 255)
    private String path;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "food_group", nullable = false, length = 20)
    private String foodGroup = "OTHER";

    public UUID getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getFoodGroup() {
        return foodGroup;
    }

    public void setFoodGroup(String foodGroup) {
        this.foodGroup = foodGroup;
    }
}
