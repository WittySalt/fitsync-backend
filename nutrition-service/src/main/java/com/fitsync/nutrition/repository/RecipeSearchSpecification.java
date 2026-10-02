package com.fitsync.nutrition.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.domain.RecipeScope;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.RecipeEntity;
import com.fitsync.nutrition.entity.RecipeFavoriteEntity;
import com.fitsync.nutrition.entity.RecipeIngredientEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * The recipe list queries, built with the Criteria API so that a filter that is not given adds no predicate
 * and the query never carries an untyped null parameter. Every value reaches the database as a bind parameter,
 * and the text search escapes the LIKE wildcards of the text it is given.
 */
public final class RecipeSearchSpecification {

    private static final char LIKE_ESCAPE = '\\';

    private RecipeSearchSpecification() {
    }

    /** The shared library: system recipes that are published. */
    public static Specification<RecipeEntity> library(String category, String query, Set<String> excludedCategories) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = libraryPredicates(root, cb);
            addFilters(predicates, root, cq, cb, category, query, excludedCategories);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The recipes the user wrote. */
    public static Specification<RecipeEntity> mine(UUID userId, String category, String query,
                                                   Set<String> excludedCategories) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("scope"), RecipeScope.USER));
            predicates.add(cb.equal(root.get("userId"), userId));
            addFilters(predicates, root, cq, cb, category, query, excludedCategories);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The library recipes the user has saved. */
    public static Specification<RecipeEntity> favorites(UUID userId, String category, String query,
                                                        Set<String> excludedCategories) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = libraryPredicates(root, cb);
            Subquery<UUID> saved = cq.subquery(UUID.class);
            Root<RecipeFavoriteEntity> favorite = saved.from(RecipeFavoriteEntity.class);
            saved.select(favorite.<UUID>get("recipeId")).where(cb.equal(favorite.get("userId"), userId));
            predicates.add(root.<UUID>get("recipeId").in(saved));
            addFilters(predicates, root, cq, cb, category, query, excludedCategories);
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static List<Predicate> libraryPredicates(Root<RecipeEntity> root, CriteriaBuilder cb) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("scope"), RecipeScope.SYSTEM));
        predicates.add(cb.isTrue(root.get("published")));
        return predicates;
    }

    private static void addFilters(List<Predicate> predicates, Root<RecipeEntity> root,
                                   jakarta.persistence.criteria.CriteriaQuery<?> cq, CriteriaBuilder cb,
                                   String category, String query, Set<String> excludedCategories) {
        if (category != null) {
            predicates.add(cb.equal(cb.lower(root.<String>get("category")), category.toLowerCase(Locale.ROOT)));
        }
        if (query != null) {
            predicates.add(cb.like(cb.lower(root.<String>get("name")),
                    "%" + escapeLike(query.toLowerCase(Locale.ROOT)) + "%", LIKE_ESCAPE));
        }
        if (excludedCategories != null && !excludedCategories.isEmpty()) {
            // A diet keeps a recipe only when none of its ingredients is a food the diet leaves out.
            Subquery<Integer> excluded = cq.subquery(Integer.class);
            Root<RecipeIngredientEntity> ingredient = excluded.from(RecipeIngredientEntity.class);
            Root<FoodCatalogItemEntity> food = excluded.from(FoodCatalogItemEntity.class);
            excluded.select(cb.literal(1)).where(
                    cb.equal(ingredient.get("recipeId"), root.get("recipeId")),
                    cb.equal(ingredient.get("foodCatalogItemId"), food.get("foodId")),
                    food.get("categoryCode").in(excludedCategories));
            predicates.add(cb.not(cb.exists(excluded)));
        }
    }

    /** Makes the escape character and the two LIKE wildcards match as themselves. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
