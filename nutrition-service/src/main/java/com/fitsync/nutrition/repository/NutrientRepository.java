package com.fitsync.nutrition.repository;

import java.util.List;

import com.fitsync.nutrition.entity.NutrientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientRepository extends JpaRepository<NutrientEntity, String> {

    List<NutrientEntity> findAllByOrderBySortOrderAsc();
}
