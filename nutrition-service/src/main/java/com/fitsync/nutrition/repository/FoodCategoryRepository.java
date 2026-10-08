package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCategoryRepository extends JpaRepository<FoodCategoryEntity, UUID> {

    List<FoodCategoryEntity> findByActiveTrueOrderBySortOrderAscDisplayNameAsc();

    List<FoodCategoryEntity> findByCodeInAndActiveTrue(List<String> codes);

    /** Inactive categories included, so a food or meal item filed under a retired category keeps its group. */
    List<FoodCategoryEntity> findByCodeIn(List<String> codes);

    Optional<FoodCategoryEntity> findByCategoryIdAndActiveTrue(UUID categoryId);
}
