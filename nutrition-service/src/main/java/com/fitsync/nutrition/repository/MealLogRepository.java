package com.fitsync.nutrition.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.domain.MealType;
import com.fitsync.nutrition.entity.MealLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MealLogRepository extends JpaRepository<MealLogEntity, UUID> {

    Optional<MealLogEntity> findByMealIdAndUserId(UUID mealId, UUID userId);

    Optional<MealLogEntity> findTopByUserIdOrderByConsumedAtDesc(UUID userId);

    List<MealLogEntity> findByUserIdOrderByConsumedAtDesc(UUID userId);

    List<MealLogEntity> findByUserIdAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
            UUID userId, Instant from, Instant to);

    List<MealLogEntity> findByUserIdAndMealTypeOrderByConsumedAtDesc(UUID userId, MealType mealType);

    List<MealLogEntity> findByUserIdAndMealTypeAndConsumedAtGreaterThanEqualAndConsumedAtLessThanEqualOrderByConsumedAtDesc(
            UUID userId, MealType mealType, Instant from, Instant to);

    void deleteByUserId(UUID userId);

    // The three purge statements below are native on purpose. A deleted meal is hidden from every entity
    // query, so an account purge that went through the entities would leave those rows behind.

    @Modifying
    @Query(value = """
            delete from meal_item_nutrient_details
            where meal_item_id in (
                select i.meal_item_id
                from meal_items i
                join meal_logs m on m.meal_id = i.meal_id
                where m.user_id = :userId)
            """, nativeQuery = true)
    int purgeItemDetailsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            delete from meal_items
            where meal_id in (select m.meal_id from meal_logs m where m.user_id = :userId)
            """, nativeQuery = true)
    int purgeItemsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "delete from meal_logs where user_id = :userId", nativeQuery = true)
    int purgeByUserId(@Param("userId") UUID userId);
}
