package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.entity.MealLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealLogRepository extends JpaRepository<MealLogEntity, UUID> {

    Optional<MealLogEntity> findByMealIdAndUserId(UUID mealId, UUID userId);

    Optional<MealLogEntity> findTopByUserIdOrderByConsumedAtDesc(UUID userId);

    List<MealLogEntity> findByUserIdOrderByConsumedAtDesc(UUID userId);

    List<MealLogEntity> findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
            UUID userId, Instant from, Instant to);

    List<MealLogEntity> findByUserIdAndMealTypeOrderByConsumedAtDesc(UUID userId, MealType mealType);

    List<MealLogEntity> findByUserIdAndMealTypeAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
            UUID userId, MealType mealType, Instant from, Instant to);
}
