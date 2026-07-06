package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
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

    List<FoodCatalogItemEntity> findByOwnerUserId(UUID ownerUserId);

    // Used by external-data importers to make ingestion idempotent (upsert keyed on the source record).
    java.util.Optional<FoodCatalogItemEntity> findBySourceDatasetAndSourceRef(String sourceDataset,
                                                                              String sourceRef);
}
