package com.fitsync.nutrition.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeStepRepository extends JpaRepository<RecipeStepEntity, UUID> {

    List<RecipeStepEntity> findByRecipeIdOrderByStepNumberAsc(UUID recipeId);

    List<RecipeStepEntity> findByRecipeIdInOrderByRecipeIdAscStepNumberAsc(Collection<UUID> recipeIds);

    void deleteByRecipeId(UUID recipeId);
}
