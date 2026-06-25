package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodNutrientDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodNutrientDetailRepository extends JpaRepository<FoodNutrientDetailEntity, UUID> {

    @Query("""
            select d
            from FoodNutrientDetailEntity d
            where d.foodId = :foodId
              and d.active = true
              and (d.dateEffectiveFrom is null or d.dateEffectiveFrom <= :onDate)
              and (d.dateEffectiveTo is null or d.dateEffectiveTo >= :onDate)
            order by d.sortOrder asc
            """)
    List<FoodNutrientDetailEntity> findActiveByFoodId(@Param("foodId") UUID foodId, @Param("onDate") LocalDate onDate);

    void deleteByFoodIdIn(Collection<UUID> foodIds);
}
