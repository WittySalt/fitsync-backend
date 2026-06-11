package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.MealItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealItemRepository extends JpaRepository<MealItemEntity, UUID> {

    List<MealItemEntity> findByMealIdOrderByDateTimeCreatedAsc(UUID mealId);

    void deleteByMealId(UUID mealId);
}
