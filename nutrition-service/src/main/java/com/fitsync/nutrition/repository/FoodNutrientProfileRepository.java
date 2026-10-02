package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodNutrientProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodNutrientProfileRepository extends JpaRepository<FoodNutrientProfileEntity, UUID> {

    @Query("""
            select p
            from FoodNutrientProfileEntity p
            where p.foodId = :foodId
              and p.active = true
              and (p.dateEffectiveFrom is null or p.dateEffectiveFrom <= :onDate)
              and (p.dateEffectiveTo is null or p.dateEffectiveTo >= :onDate)
            order by p.dateTimeCreated desc
            """)
    java.util.List<FoodNutrientProfileEntity> findActiveProfilesByFoodId(@Param("foodId") UUID foodId,
                                                                         @Param("onDate") LocalDate onDate);

    @Query("""
            select p
            from FoodNutrientProfileEntity p
            where p.foodId in :foodIds
              and p.active = true
              and (p.dateEffectiveFrom is null or p.dateEffectiveFrom <= :onDate)
              and (p.dateEffectiveTo is null or p.dateEffectiveTo >= :onDate)
            order by p.dateTimeCreated desc
            """)
    java.util.List<FoodNutrientProfileEntity> findActiveProfilesByFoodIdIn(
            @Param("foodIds") Collection<UUID> foodIds, @Param("onDate") LocalDate onDate);

    void deleteByFoodIdIn(Collection<UUID> foodIds);
}
