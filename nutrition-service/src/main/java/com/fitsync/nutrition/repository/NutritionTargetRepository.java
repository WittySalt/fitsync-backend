package com.fitsync.nutrition.repository;

import java.util.UUID;

import com.fitsync.nutrition.entity.NutritionTargetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NutritionTargetRepository extends JpaRepository<NutritionTargetEntity, UUID> {
}
