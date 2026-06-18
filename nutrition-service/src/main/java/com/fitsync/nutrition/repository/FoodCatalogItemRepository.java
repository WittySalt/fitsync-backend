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

    /**
     * As {@link #searchAccessibleFoods} but restricted to foods whose category (matched by
     * {@code category_code}) sits within the given materialized-path subtree — e.g. selecting a
     * top-level group returns foods in all of its subcategories.
     */
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
              and exists (
                    select 1 from FoodCategoryEntity c
                    where c.code = f.categoryCode
                      and c.active = true
                      and c.path like concat(:pathPrefix, '%')
                  )
            order by
              case when f.ownerUserId = :userId then 0 else 1 end,
              f.verified desc,
              f.displayName asc
            """)
    List<FoodCatalogItemEntity> searchAccessibleFoodsInCategorySubtree(@Param("userId") UUID userId,
                                                                       @Param("query") String query,
                                                                       @Param("onDate") LocalDate onDate,
                                                                       @Param("pathPrefix") String pathPrefix);

    /**
     * As {@link #searchAccessibleFoods} but restricted to foods directly in a single category
     * (exact {@code category_code} match, ignoring subcategories).
     */
    @Query("""
            select f
            from FoodCatalogItemEntity f
            where f.active = true
              and (f.dateEffectiveFrom is null or f.dateEffectiveFrom <= :onDate)
              and (f.dateEffectiveTo is null or f.dateEffectiveTo >= :onDate)
              and (f.ownerUserId is null or f.ownerUserId = :userId)
              and f.categoryCode = :categoryCode
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
    List<FoodCatalogItemEntity> searchAccessibleFoodsInCategory(@Param("userId") UUID userId,
                                                                @Param("query") String query,
                                                                @Param("onDate") LocalDate onDate,
                                                                @Param("categoryCode") String categoryCode);
}
