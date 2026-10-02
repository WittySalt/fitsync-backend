package com.fitsync.nutrition.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.nutrition.entity.RecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipeRepository extends JpaRepository<RecipeEntity, UUID>, JpaSpecificationExecutor<RecipeEntity> {

    List<RecipeEntity> findByUserIdOrderByNameAsc(UUID userId);

    Optional<RecipeEntity> findByRecipeIdAndUserId(UUID recipeId, UUID userId);

    // The purge statements below are native on purpose. A deleted recipe is hidden from every entity query,
    // so an account purge that went through the entities would leave those rows behind.

    @Modifying
    @Query(value = """
            delete from recipe_steps
            where recipe_id in (select r.recipe_id from recipes r where r.user_id = :userId)
            """, nativeQuery = true)
    int purgeStepsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            delete from recipe_ingredients
            where recipe_id in (select r.recipe_id from recipes r where r.user_id = :userId)
            """, nativeQuery = true)
    int purgeIngredientsByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = "delete from recipes where user_id = :userId", nativeQuery = true)
    int purgeByUserId(@Param("userId") UUID userId);
}
