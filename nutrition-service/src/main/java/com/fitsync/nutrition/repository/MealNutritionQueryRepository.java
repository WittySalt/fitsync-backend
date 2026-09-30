package com.fitsync.nutrition.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.entity.MealLogEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only aggregation queries over the meal log, kept apart from the writable meal repositories. */
public interface MealNutritionQueryRepository extends Repository<MealLogEntity, UUID> {

    interface MealNutritionRow {
        MealType getMealType();

        Instant getConsumedAt();

        Integer getCalories();

        BigDecimal getProteinGrams();
    }

    /**
     * One row per meal item of the caller's meals consumed in {@code [from, to)}, with the meal's type
     * and time. A meal without items still yields one row with null nutrition, so that it counts as a
     * logged day.
     */
    @Query("""
            select m.mealType as mealType, m.consumedAt as consumedAt,
                   i.nutritionFacts.calories as calories, i.nutritionFacts.proteinGrams as proteinGrams
            from MealLogEntity m
            left join MealItemEntity i on i.mealId = m.mealId
            where m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            """)
    List<MealNutritionRow> findMealNutritionRows(@Param("userId") UUID userId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);
}
