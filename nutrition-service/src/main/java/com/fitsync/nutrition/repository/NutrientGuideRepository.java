package com.fitsync.nutrition.repository;

import com.fitsync.nutrition.entity.NutrientGuideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutrientGuideRepository extends JpaRepository<NutrientGuideEntity, String> {
}
