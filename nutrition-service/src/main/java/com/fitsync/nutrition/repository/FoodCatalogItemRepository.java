package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodCatalogItemRepository extends JpaRepository<FoodCatalogItemEntity, UUID> {

    /**
     * Paged, relevance-ranked food search. All filters are optional (null = no filter):
     * <ul>
     *   <li>{@code query} — case-insensitive substring on display/normalized name;</li>
     *   <li>{@code categoryCode} — exact subcategory; <b>or</b> {@code pathPrefix} — category subtree
     *       (mutually exclusive; the service picks one based on {@code includeSubcategories});</li>
     *   <li>{@code brandId}, {@code verified}, {@code scope} — direct attribute filters.</li>
     * </ul>
     * Ordering: exact name match first, then prefix match, then substring; tie-broken by the user's
     * own foods, verified, then name. Pagination is pushed down via {@link Pageable}.
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
              and (:categoryCode is null or f.categoryCode = :categoryCode)
              and (:pathPrefix is null or exists (
                    select 1 from FoodCategoryEntity c
                    where c.code = f.categoryCode
                      and c.active = true
                      and c.path like concat(:pathPrefix, '%')
                  ))
              and (:brandId is null or f.brandId = :brandId)
              and (:verified is null or f.verified = :verified)
              and (:scope is null or f.scope = :scope)
            order by
              case
                when :query is null then 2
                when lower(f.normalizedName) = lower(:query) then 0
                when lower(f.normalizedName) like lower(concat(:query, '%')) then 1
                else 2
              end,
              case when f.ownerUserId = :userId then 0 else 1 end,
              f.verified desc,
              f.displayName asc
            """)
    List<FoodCatalogItemEntity> searchAccessibleFoods(@Param("userId") UUID userId,
                                                      @Param("query") String query,
                                                      @Param("onDate") LocalDate onDate,
                                                      @Param("categoryCode") String categoryCode,
                                                      @Param("pathPrefix") String pathPrefix,
                                                      @Param("brandId") UUID brandId,
                                                      @Param("verified") Boolean verified,
                                                      @Param("scope") FoodCatalogScope scope,
                                                      Pageable pageable);

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
