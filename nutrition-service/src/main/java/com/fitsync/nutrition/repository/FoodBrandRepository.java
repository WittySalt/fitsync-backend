package com.fitsync.nutrition.repository;

import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodBrandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodBrandRepository extends JpaRepository<FoodBrandEntity, UUID> {

    Optional<FoodBrandEntity> findByNormalizedNameAndActiveTrue(String normalizedName);
}
