package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.CustomFoodEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFoodRepository extends JpaRepository<CustomFoodEntity, UUID> {

    List<CustomFoodEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<CustomFoodEntity> findByCustomFoodIdAndUserId(UUID customFoodId, UUID userId);
}
