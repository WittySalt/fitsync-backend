package com.fitsync.nutrition.repository;

import java.util.UUID;

import com.fitsync.nutrition.entity.NutritionPreferenceProjectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionPreferenceProjectionRepository extends JpaRepository<NutritionPreferenceProjectionEntity, UUID> {
}
