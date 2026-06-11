package com.fitsync.nutrition.entity;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.nutrition.domain.MealSource;
import com.fitsync.nutrition.domain.MealType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_logs")
public class MealLogEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "meal_id", nullable = false)
    private UUID mealId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 30)
    private MealType mealType;

    @Column(name = "name", length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private MealSource source;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    @Column(name = "timezone", length = 80)
    private String timezone;

    @Column(name = "note", length = 300)
    private String note;

    public UUID getMealId() {
        return mealId;
    }

    public void setMealId(UUID mealId) {
        this.mealId = mealId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public MealType getMealType() {
        return mealType;
    }

    public void setMealType(MealType mealType) {
        this.mealType = mealType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public MealSource getSource() {
        return source;
    }

    public void setSource(MealSource source) {
        this.source = source;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(Instant consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
