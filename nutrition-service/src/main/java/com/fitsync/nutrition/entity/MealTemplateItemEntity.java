package com.fitsync.nutrition.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_template_items")
public class MealTemplateItemEntity extends AbstractFoodLineItemEntity {

    @Id
    @Column(name = "template_item_id", nullable = false)
    private UUID templateItemId;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public UUID getTemplateItemId() {
        return templateItemId;
    }

    public void setTemplateItemId(UUID templateItemId) {
        this.templateItemId = templateItemId;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
