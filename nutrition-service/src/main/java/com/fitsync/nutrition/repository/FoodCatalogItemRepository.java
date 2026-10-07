package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

public interface FoodCatalogItemRepository extends JpaRepository<FoodCatalogItemEntity, UUID>,
        JpaSpecificationExecutor<FoodCatalogItemEntity> {

    // Paged, relevance-ranked food search is built dynamically via FoodCatalogSearchSpecification
    // (through JpaSpecificationExecutor.findAll) so each optional filter adds a predicate only when
    // present — the query never sends an untyped null bind parameter (Postgres-safe; the old static
    // JPQL with (:param is null or ...) + lower(:query) 500'd on Postgres while passing on H2).

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
     * PostgreSQL only (pg_trgm): the foods the caller may see whose name is close to the query, best first. A
     * name scores the larger of its trigram similarity to the whole query and its word similarity (the query
     * against the best run of words in the name), so a typo still finds a name with many words. It runs in
     * its own transaction so that a failure, such as a missing extension, cannot poison the caller's.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @Query(value = """
            select f.food_id from food_catalog_items f
            where f.active = true
              and (f.date_effective_from is null or f.date_effective_from <= :onDate)
              and (f.date_effective_to is null or f.date_effective_to >= :onDate)
              and (f.owner_user_id is null or f.owner_user_id = :userId)
              and greatest(similarity(lower(f.display_name), cast(:query as text)),
                           word_similarity(cast(:query as text), lower(f.display_name))) > :minSimilarity
            order by greatest(similarity(lower(f.display_name), cast(:query as text)),
                              word_similarity(cast(:query as text), lower(f.display_name))) desc,
                     length(f.display_name) asc, f.display_name asc, f.food_id asc
            limit :limit
            """, nativeQuery = true)
    List<UUID> findSimilarFoodIds(@Param("userId") UUID userId, @Param("query") String query,
                                  @Param("onDate") LocalDate onDate, @Param("minSimilarity") double minSimilarity,
                                  @Param("limit") int limit);

    List<FoodCatalogItemEntity> findByOwnerUserId(UUID ownerUserId);

    // Used by external-data importers to make ingestion idempotent (upsert keyed on the source record).
    java.util.Optional<FoodCatalogItemEntity> findBySourceDatasetAndSourceRef(String sourceDataset,
                                                                              String sourceRef);
}
