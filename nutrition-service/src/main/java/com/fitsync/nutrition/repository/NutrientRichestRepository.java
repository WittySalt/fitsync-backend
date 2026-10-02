package com.fitsync.nutrition.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Read-only catalogue query for the foods richest in a nutrient. It reads no user data. */
public interface NutrientRichestRepository extends Repository<FoodCatalogItemEntity, UUID> {

    interface RichFoodRow {
        UUID getFoodId();

        String getName();

        String getFoodGroup();

        String getCategoryCode();

        BigDecimal getAmount();
    }

    /**
     * The visible foods of the shared catalogue (active, in date, not owned by a user) whose category has a food
     * group other than OTHER, by the nutrient amount per 100 of the basis unit, highest first.
     */
    @Query("""
            select f.foodId as foodId, f.displayName as name, c.foodGroup as foodGroup,
                   f.categoryCode as categoryCode, d.amount as amount
            from FoodNutrientDetailEntity d, FoodCatalogItemEntity f, FoodCategoryEntity c
            where d.foodId = f.foodId
              and c.code = f.categoryCode
              and d.nutrientCode = :code
              and d.active = true
              and d.amount > 0
              and f.active = true
              and f.ownerUserId is null
              and c.active = true
              and c.foodGroup <> 'OTHER'
              and (f.dateEffectiveFrom is null or f.dateEffectiveFrom <= :onDate)
              and (f.dateEffectiveTo is null or f.dateEffectiveTo >= :onDate)
              and (d.dateEffectiveFrom is null or d.dateEffectiveFrom <= :onDate)
              and (d.dateEffectiveTo is null or d.dateEffectiveTo >= :onDate)
            order by d.amount desc, f.displayName asc, f.foodId asc
            """)
    List<RichFoodRow> findRichest(@Param("code") String code, @Param("onDate") LocalDate onDate, Pageable pageable);

    /** The same, only for foods that an imported (system) recipe uses as an ingredient, so they carry an everyday name. */
    @Query("""
            select distinct f.foodId as foodId, f.displayName as name, c.foodGroup as foodGroup,
                   f.categoryCode as categoryCode, d.amount as amount
            from FoodNutrientDetailEntity d, FoodCatalogItemEntity f, FoodCategoryEntity c,
                 RecipeIngredientEntity i, RecipeEntity r
            where d.foodId = f.foodId
              and c.code = f.categoryCode
              and i.foodCatalogItemId = f.foodId
              and i.recipeId = r.recipeId
              and r.source is not null and r.source <> 'USER'
              and d.nutrientCode = :code
              and d.active = true
              and d.amount > 0
              and f.active = true
              and f.ownerUserId is null
              and c.active = true
              and c.foodGroup <> 'OTHER'
              and (f.dateEffectiveFrom is null or f.dateEffectiveFrom <= :onDate)
              and (f.dateEffectiveTo is null or f.dateEffectiveTo >= :onDate)
              and (d.dateEffectiveFrom is null or d.dateEffectiveFrom <= :onDate)
              and (d.dateEffectiveTo is null or d.dateEffectiveTo >= :onDate)
            order by d.amount desc, f.displayName asc, f.foodId asc
            """)
    List<RichFoodRow> findRichestInRecipes(@Param("code") String code, @Param("onDate") LocalDate onDate,
                                           Pageable pageable);

    interface IngredientTextRow {
        UUID getFoodId();

        String getText();
    }

    /** The ingredient lines of imported recipes that use the given foods. */
    @Query("""
            select i.foodCatalogItemId as foodId, i.displayText as text
            from RecipeIngredientEntity i, RecipeEntity r
            where i.recipeId = r.recipeId
              and r.source is not null and r.source <> 'USER'
              and i.displayText is not null
              and i.foodCatalogItemId in :foodIds
            """)
    List<IngredientTextRow> findIngredientTexts(@Param("foodIds") Collection<UUID> foodIds);
}
