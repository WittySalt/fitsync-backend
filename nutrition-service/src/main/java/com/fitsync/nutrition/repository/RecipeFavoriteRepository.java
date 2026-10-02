package com.fitsync.nutrition.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeFavoriteRepository extends JpaRepository<RecipeFavoriteEntity, UUID> {

    Optional<RecipeFavoriteEntity> findByUserIdAndRecipeId(UUID userId, UUID recipeId);

    /** Which of the given recipes the user has saved, so that a page of recipes needs one query. */
    @Query("select f.recipeId from RecipeFavoriteEntity f where f.userId = :userId and f.recipeId in :recipeIds")
    List<UUID> findSavedRecipeIds(@Param("userId") UUID userId, @Param("recipeIds") Collection<UUID> recipeIds);

    void deleteByUserId(UUID userId);
}
