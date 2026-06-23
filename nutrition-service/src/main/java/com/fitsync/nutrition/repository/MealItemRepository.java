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
}
