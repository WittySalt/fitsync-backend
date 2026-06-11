package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.MealItemNutrientDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealItemNutrientDetailRepository extends JpaRepository<MealItemNutrientDetailEntity, UUID> {

    List<MealItemNutrientDetailEntity> findByMealItemIdOrderBySortOrderAsc(UUID mealItemId);

    List<MealItemNutrientDetailEntity> findByMealItemIdInOrderByMealItemIdAscSortOrderAsc(List<UUID> mealItemIds);

    void deleteByMealItemId(UUID mealItemId);
}
