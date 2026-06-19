package com.fitsync.nutrition.entity;

import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "food_favorites")
public class FoodFavoriteEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "favorite_id", nullable = false)
    private UUID favoriteId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "food_id", nullable = false)
    private UUID foodId;

    public UUID getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(UUID favoriteId) {
        this.favoriteId = favoriteId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getFoodId() {
        return foodId;
    }

    public void setFoodId(UUID foodId) {
        this.foodId = foodId;
    }
}
