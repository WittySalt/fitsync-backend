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

    interface MealItemRow {
        UUID getMealId();

        MealType getMealType();

        Instant getConsumedAt();

        UUID getFoodCatalogItemId();

        UUID getCustomFoodId();

        String getFoodName();

        String getCategoryCode();

        Integer getCalories();

        BigDecimal getProteinGrams();

        BigDecimal getCarbsGrams();

        BigDecimal getFatGrams();
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

    /**
     * One row per meal item of the caller's meals consumed in {@code [from, to)}, newest meal first, with
     * what identifies and describes each food. A meal without items still yields one row with null food
     * columns, so that it counts as a meal eaten at that time.
     */
    @Query("""
            select m.mealId as mealId, m.mealType as mealType, m.consumedAt as consumedAt,
                   i.foodCatalogItemId as foodCatalogItemId, i.customFoodId as customFoodId,
                   i.foodName as foodName, i.categoryCode as categoryCode,
                   i.nutritionFacts.calories as calories, i.nutritionFacts.proteinGrams as proteinGrams,
                   i.nutritionFacts.carbsGrams as carbsGrams, i.nutritionFacts.fatGrams as fatGrams
            from MealLogEntity m
            left join MealItemEntity i on i.mealId = m.mealId
            where m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            order by m.consumedAt desc, m.mealId, i.dateTimeCreated, i.mealItemId
            """)
    List<MealItemRow> findMealItemRows(@Param("userId") UUID userId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);
}
