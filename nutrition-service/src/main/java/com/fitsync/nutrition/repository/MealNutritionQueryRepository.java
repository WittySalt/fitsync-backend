package com.fitsync.nutrition.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
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

    /** One meal item of the Diary with everything the daily summary and the drink list read from it. */
    interface DiaryItemRow {
        UUID getMealId();

        UUID getMealItemId();

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

        BigDecimal getFiberGrams();

        BigDecimal getSugarGrams();

        BigDecimal getSodiumMg();

        BigDecimal getSaturatedFatGrams();

        Integer getWaterMl();
    }

    /** One vitamin or mineral amount snapshotted on a meal item. */
    interface NutrientDetailRow {
        UUID getMealItemId();

        String getNutrientCode();

        BigDecimal getAmount();
    }

    /** The totals of one meal, summed in the database, so a range needs one row per meal and not one per item. */
    interface MealTotalsRow {
        UUID getMealId();

        Instant getConsumedAt();

        Long getCalories();

        BigDecimal getFiberGrams();

        BigDecimal getSugarGrams();

        BigDecimal getSodiumMg();

        BigDecimal getSaturatedFatGrams();
    }

    /** One nutrient summed over one meal. */
    interface MealNutrientTotalRow {
        UUID getMealId();

        Instant getConsumedAt();

        BigDecimal getTotal();
    }

    /** One nutrient summed over one meal, with the nutrient code. */
    interface MealCodeTotalRow {
        String getNutrientCode();

        Instant getConsumedAt();

        BigDecimal getTotal();
    }

    /** One nutrient summed over a whole range. */
    interface NutrientTotalRow {
        String getNutrientCode();

        BigDecimal getTotal();
    }

    /**
     * One row per item of the meals of one user consumed in {@code [from, to)}, oldest first. A meal without
     * items still yields a row with a null item, so that it counts as a meal.
     */
    @Query("""
            select m.mealId as mealId, i.mealItemId as mealItemId, m.mealType as mealType, m.consumedAt as consumedAt,
                   i.foodCatalogItemId as foodCatalogItemId, i.customFoodId as customFoodId,
                   i.foodName as foodName, i.categoryCode as categoryCode,
                   i.nutritionFacts.calories as calories, i.nutritionFacts.proteinGrams as proteinGrams,
                   i.nutritionFacts.carbsGrams as carbsGrams, i.nutritionFacts.fatGrams as fatGrams,
                   i.nutritionFacts.fiberGrams as fiberGrams, i.nutritionFacts.sugarGrams as sugarGrams,
                   i.nutritionFacts.sodiumMg as sodiumMg, i.saturatedFatGrams as saturatedFatGrams,
                   i.waterMl as waterMl
            from MealLogEntity m
            left join MealItemEntity i on i.mealId = m.mealId
            where m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            order by m.consumedAt, m.mealId, i.dateTimeCreated, i.mealItemId
            """)
    List<DiaryItemRow> findDiaryItemRows(@Param("userId") UUID userId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /** The snapshotted amounts of the given nutrients on the items of the meals in {@code [from, to)}. */
    @Query("""
            select d.mealItemId as mealItemId, d.nutrientCode as nutrientCode, d.amount as amount
            from MealItemNutrientDetailEntity d, MealItemEntity i, MealLogEntity m
            where d.mealItemId = i.mealItemId
              and i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
              and d.nutrientCode in :codes
            """)
    List<NutrientDetailRow> findNutrientDetailRows(@Param("userId") UUID userId,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to,
                                                   @Param("codes") Collection<String> codes);

    /** One row per meal consumed in {@code [from, to)} with its energy and the sums the Diary flags read. */
    @Query("""
            select m.mealId as mealId, m.consumedAt as consumedAt,
                   sum(i.nutritionFacts.calories) as calories,
                   sum(i.nutritionFacts.fiberGrams) as fiberGrams,
                   sum(i.nutritionFacts.sugarGrams) as sugarGrams,
                   sum(i.nutritionFacts.sodiumMg) as sodiumMg,
                   sum(i.saturatedFatGrams) as saturatedFatGrams
            from MealLogEntity m
            left join MealItemEntity i on i.mealId = m.mealId
            where m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            group by m.mealId, m.consumedAt
            """)
    List<MealTotalsRow> findMealTotals(@Param("userId") UUID userId,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to);

    /** One nutrient summed per meal, for the meals consumed in {@code [from, to)} that carry it. */
    @Query("""
            select m.mealId as mealId, m.consumedAt as consumedAt, sum(d.amount) as total
            from MealItemNutrientDetailEntity d, MealItemEntity i, MealLogEntity m
            where d.mealItemId = i.mealItemId
              and i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
              and d.nutrientCode = :code
            group by m.mealId, m.consumedAt
            """)
    List<MealNutrientTotalRow> findMealNutrientTotals(@Param("userId") UUID userId,
                                                      @Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("code") String code);

    /** Every given nutrient summed over the range, one row per nutrient. */
    @Query("""
            select d.nutrientCode as nutrientCode, sum(d.amount) as total
            from MealItemNutrientDetailEntity d, MealItemEntity i, MealLogEntity m
            where d.mealItemId = i.mealItemId
              and i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
              and d.nutrientCode in :codes
            group by d.nutrientCode
            """)
    List<NutrientTotalRow> findNutrientTotals(@Param("userId") UUID userId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to,
                                              @Param("codes") Collection<String> codes);

    /** One nutrient summed per meal, for every given nutrient, so a range is bucketed by local date from one query. */
    @Query("""
            select d.nutrientCode as nutrientCode, m.mealId as mealId, m.consumedAt as consumedAt, sum(d.amount) as total
            from MealItemNutrientDetailEntity d, MealItemEntity i, MealLogEntity m
            where d.mealItemId = i.mealItemId
              and i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
              and d.nutrientCode in :codes
            group by d.nutrientCode, m.mealId, m.consumedAt
            """)
    List<MealCodeTotalRow> findNutrientTotalsByMeal(@Param("userId") UUID userId,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to,
                                                    @Param("codes") Collection<String> codes);

    /** How many meal items a user logged in {@code [from, to)}. */
    @Query("""
            select count(i)
            from MealLogEntity m, MealItemEntity i
            where i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
            """)
    long countItems(@Param("userId") UUID userId, @Param("from") Instant from, @Param("to") Instant to);

    /** How many of those items have no vitamin or mineral snapshot at all. */
    @Query("""
            select count(i)
            from MealLogEntity m, MealItemEntity i
            where i.mealId = m.mealId
              and m.userId = :userId
              and m.consumedAt >= :from
              and m.consumedAt < :to
              and not exists (select 1 from MealItemNutrientDetailEntity d where d.mealItemId = i.mealItemId)
            """)
    long countItemsWithoutNutrients(@Param("userId") UUID userId, @Param("from") Instant from,
                                    @Param("to") Instant to);
}
