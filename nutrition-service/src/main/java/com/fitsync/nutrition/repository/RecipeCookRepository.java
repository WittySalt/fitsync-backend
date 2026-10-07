package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeCookEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeCookRepository extends JpaRepository<RecipeCookEntity, UUID> {

    interface CookedRecipeRow {
        UUID getRecipeId();

        Instant getLastCookedAt();
    }

    interface CookCountRow {
        UUID getRecipeId();

        Long getTimes();

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

    /** How often the user cooked each recipe since a time, with the newest cook, cooks marked as deleted left out. */
    @Query("""
            select c.recipeId as recipeId, count(c) as times, max(c.finishedAt) as lastCookedAt
            from RecipeCookEntity c
            where c.userId = :userId and c.deletedAt is null and c.finishedAt >= :since
            group by c.recipeId
            """)
    List<CookCountRow> countCooksSince(@Param("userId") UUID userId, @Param("since") Instant since);

    /** The caller's cooks since a time that still have servings left, newest first, deleted cooks left out. */
    List<RecipeCookEntity> findByUserIdAndDeletedAtIsNullAndServingsLeftGreaterThanAndFinishedAtGreaterThanEqualOrderByFinishedAtDesc(
            UUID userId, int servingsLeft, Instant since);

    /**
     * Takes servings off a cook's leftovers in one statement, only when that many are left, so that two taps
     * at once cannot log more than was cooked. Returns the rows changed (0 when too few are left).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RecipeCookEntity c set c.servingsLeft = c.servingsLeft - :servings
            where c.cookId = :cookId and c.userId = :userId and c.deletedAt is null and c.servingsLeft >= :servings
            """)
    int takeLeftover(@Param("cookId") UUID cookId, @Param("userId") UUID userId, @Param("servings") int servings);

    long countByUserIdAndDeletedAtIsNullAndFinishedAtGreaterThanEqualAndFinishedAtLessThan(UUID userId, Instant from,
                                                                                         Instant to);

    Optional<RecipeCookEntity> findFirstByUserIdAndDeletedAtIsNullAndFinishedAtGreaterThanEqualAndFinishedAtLessThanOrderByFinishedAtDesc(
            UUID userId, Instant from, Instant to);
}
