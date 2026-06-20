package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID> {

    List<RecipeEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<RecipeEntity> findByRecipeIdAndUserId(UUID recipeId, UUID userId);
}
