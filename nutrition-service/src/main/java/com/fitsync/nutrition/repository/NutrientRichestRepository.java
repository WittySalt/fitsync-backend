package com.fitsync.nutrition.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
}
