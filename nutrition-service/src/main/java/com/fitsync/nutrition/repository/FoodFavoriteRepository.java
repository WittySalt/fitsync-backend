package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.FoodFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface FoodFavoriteRepository extends JpaRepository<FoodFavoriteEntity, UUID> {

    boolean existsByUserIdAndFoodId(UUID userId, UUID foodId);

    List<FoodFavoriteEntity> findByUserIdOrderByDateTimeCreatedDesc(UUID userId);

    @Transactional
    void deleteByUserIdAndFoodId(UUID userId, UUID foodId);
}
