package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodCatalogItemRepository extends JpaRepository<FoodCatalogItemEntity, UUID> {

    @Query("""
            select f
            from FoodCatalogItemEntity f
            where f.active = true
              and (f.dateEffectiveFrom is null or f.dateEffectiveFrom <= :onDate)
              and (f.dateEffectiveTo is null or f.dateEffectiveTo >= :onDate)
              and (f.ownerUserId is null or f.ownerUserId = :userId)
              and (
                    :query is null
                    or lower(f.displayName) like lower(concat('%', :query, '%'))
                    or lower(f.normalizedName) like lower(concat('%', :query, '%'))
                  )
            order by
              case when f.ownerUserId = :userId then 0 else 1 end,
              f.verified desc,
              f.displayName asc
            """)
    List<FoodCatalogItemEntity> searchAccessibleFoods(@Param("userId") UUID userId,
                                                      @Param("query") String query,
                                                      @Param("onDate") LocalDate onDate);

    @Query("""
            select f
            from FoodCatalogItemEntity f
            where f.foodId = :foodId
              and f.active = true
              and (f.dateEffectiveFrom is null or f.dateEffectiveFrom <= :onDate)
              and (f.dateEffectiveTo is null or f.dateEffectiveTo >= :onDate)
              and (f.ownerUserId is null or f.ownerUserId = :userId)
            """)
    java.util.Optional<FoodCatalogItemEntity> findAccessibleFoodById(@Param("foodId") UUID foodId,
                                                                     @Param("userId") UUID userId,
                                                                     @Param("onDate") LocalDate onDate);
}
