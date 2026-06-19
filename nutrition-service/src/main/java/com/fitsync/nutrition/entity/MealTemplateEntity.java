package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.nutrition.domain.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_templates")
public class MealTemplateEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", length = 30)
    private MealType mealType;

    @Column(name = "note", length = 300)
    private String note;

    public UUID getTemplateId() {
        return templateId;
    }

    public void setTemplateId(UUID templateId) {
        this.templateId = templateId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
