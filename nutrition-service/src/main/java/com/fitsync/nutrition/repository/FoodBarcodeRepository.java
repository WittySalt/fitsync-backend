package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodBarcodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FoodBarcodeRepository extends JpaRepository<FoodBarcodeEntity, UUID> {

    @Query("""
            select b
            from FoodBarcodeEntity b
            where b.barcode = :barcode
              and b.active = true
              and (b.dateEffectiveFrom is null or b.dateEffectiveFrom <= :onDate)
              and (b.dateEffectiveTo is null or b.dateEffectiveTo >= :onDate)
            order by b.primaryBarcode desc, b.dateTimeCreated desc
            """)
    java.util.Optional<FoodBarcodeEntity> findActiveByBarcode(@Param("barcode") String barcode,
                                                              @Param("onDate") LocalDate onDate);

    @Query("""
            select b
            from FoodBarcodeEntity b
            where b.foodId in :foodIds
              and b.active = true
              and (b.dateEffectiveFrom is null or b.dateEffectiveFrom <= :onDate)
              and (b.dateEffectiveTo is null or b.dateEffectiveTo >= :onDate)
            order by b.foodId asc, b.primaryBarcode desc, b.dateTimeCreated desc
            """)
    List<FoodBarcodeEntity> findActiveByFoodIds(@Param("foodIds") Collection<UUID> foodIds,
                                                @Param("onDate") LocalDate onDate);
}
