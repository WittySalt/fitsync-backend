package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.MealItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealItemRepository extends JpaRepository<MealItemEntity, UUID> {

    interface RecentLoggedFoodRow {
        MealItemEntity getItem();

        Instant getConsumedAt();
    }

    List<MealItemEntity> findByMealIdOrderByDateTimeCreatedAsc(UUID mealId);

    void deleteByMealId(UUID mealId);

    /**
     * The caller's meal items, most-recently-consumed first (tie-broken by item creation time),
     * for deriving recent / frequent foods. Bounded by the supplied {@link Pageable}.
     */
    @Query("""
            select i as item, m.consumedAt as consumedAt
            from MealItemEntity i, MealLogEntity m
            where i.mealId = m.mealId
              and m.userId = :userId
            order by m.consumedAt desc, i.dateTimeCreated desc
            """)
    List<RecentLoggedFoodRow> findRecentItemsByUserId(@Param("userId") UUID userId, Pageable pageable);

    /**
     * The same rows as {@link #findRecentItemsByUserId}, kept to the items whose snapshotted category belongs to
     * one plate group, so that a filter such as DRINKS looks through the whole scan window and not only through
     * what is left of it after the filter.
     */
    @Query("""
            select i as item, m.consumedAt as consumedAt
            from MealItemEntity i, MealLogEntity m, FoodCategoryEntity c
            where i.mealId = m.mealId
              and m.userId = :userId
              and c.code = i.categoryCode
              and c.foodGroup = :foodGroup
            order by m.consumedAt desc, i.dateTimeCreated desc
            """)
    List<RecentLoggedFoodRow> findRecentItemsByUserIdAndFoodGroup(@Param("userId") UUID userId,
                                                                  @Param("foodGroup") String foodGroup,
                                                                  Pageable pageable);

    /** One food the caller logged in a range: how often and when last, grouped by its snapshot identity. */
    interface LoggedFoodCountRow {
        UUID getFoodCatalogItemId();

        UUID getCustomFoodId();

        String getFoodName();

        String getCategoryCode();

        long getTimes();

        Instant getLastConsumedAt();
    }

    /**
     * The caller's logged foods in {@code [from, to)}, counted per meal item and grouped by catalogue food,
     * custom food and name, most often first. Deleted meals are left out by the entity's restriction.
     */
    @Query("""
            select i.foodCatalogItemId as foodCatalogItemId, i.customFoodId as customFoodId, i.foodName as foodName,
                   max(i.categoryCode) as categoryCode, count(i) as times, max(m.consumedAt) as lastConsumedAt
            from MealItemEntity i, MealLogEntity m
            where i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            group by i.foodCatalogItemId, i.customFoodId, i.foodName
            order by count(i) desc, max(m.consumedAt) desc
            """)
    List<LoggedFoodCountRow> countLoggedFoods(@Param("userId") UUID userId, @Param("from") Instant from,
                                              @Param("to") Instant to);
}
