package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredientEntity, UUID> {

    List<RecipeIngredientEntity> findByRecipeIdOrderBySortOrderAsc(UUID recipeId);

    List<RecipeIngredientEntity> findByRecipeIdInOrderByRecipeIdAscSortOrderAsc(java.util.Collection<UUID> recipeIds);

    void deleteByRecipeId(UUID recipeId);
}
