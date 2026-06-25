package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodServingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodServingRepository extends JpaRepository<FoodServingEntity, UUID> {

    @Query("""
            select s
            from FoodServingEntity s
            where s.foodId = :foodId
              and s.active = true
              and (s.dateEffectiveFrom is null or s.dateEffectiveFrom <= :onDate)
              and (s.dateEffectiveTo is null or s.dateEffectiveTo >= :onDate)
            order by s.sortOrder asc
            """)
    List<FoodServingEntity> findActiveByFoodId(@Param("foodId") UUID foodId, @Param("onDate") LocalDate onDate);

    @Query("""
            select s
            from FoodServingEntity s
            where s.servingId = :servingId
              and s.active = true
              and (s.dateEffectiveFrom is null or s.dateEffectiveFrom <= :onDate)
              and (s.dateEffectiveTo is null or s.dateEffectiveTo >= :onDate)
            """)
    java.util.Optional<FoodServingEntity> findActiveByServingId(@Param("servingId") UUID servingId, @Param("onDate") LocalDate onDate);

    void deleteByFoodIdIn(Collection<UUID> foodIds);
}
