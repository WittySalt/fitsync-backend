package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeCookEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeCookRepository extends JpaRepository<RecipeCookEntity, UUID> {

    interface CookedRecipeRow {
        UUID getRecipeId();

        Instant getLastCookedAt();
    }

    /** The cook with this client id, deleted or not, so that a retry after a delete cannot hit the unique key. */
    Optional<RecipeCookEntity> findByUserIdAndClientCookId(UUID userId, UUID clientCookId);

    Optional<RecipeCookEntity> findByCookIdAndUserIdAndDeletedAtIsNull(UUID cookId, UUID userId);

    long countByUserIdAndRecipeIdAndDeletedAtIsNull(UUID userId, UUID recipeId);

    @Query("""
            select max(c.finishedAt) from RecipeCookEntity c
            where c.userId = :userId and c.recipeId = :recipeId and c.deletedAt is null
            """)
    Optional<Instant> findLastFinishedAt(@Param("userId") UUID userId, @Param("recipeId") UUID recipeId);

    /** The user's recipes with the time of the newest cook that is not deleted, newest first. */
    @Query("""
            select c.recipeId as recipeId, max(c.finishedAt) as lastCookedAt
            from RecipeCookEntity c
            where c.userId = :userId and c.deletedAt is null
            group by c.recipeId
            order by max(c.finishedAt) desc, c.recipeId asc
            """)
    List<CookedRecipeRow> findRecentRecipes(@Param("userId") UUID userId, Pageable pageable);
}
