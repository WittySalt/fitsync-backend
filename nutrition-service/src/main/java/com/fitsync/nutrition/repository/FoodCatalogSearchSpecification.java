package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic, relevance-ranked food search built with the Criteria API. A predicate is added only for
 * each non-null filter, so the query never sends an untyped null bind parameter. This avoids the
 * PostgreSQL failure (e.g. {@code function lower(bytea) does not exist} / 42P18 "could not determine
 * data type") that a single static JPQL query with {@code (:param is null or ...)} clauses + a null
 * {@code :query} inside {@code lower(...)} triggers — H2 tolerates it, Postgres does not.
 */
public final class FoodCatalogSearchSpecification {

    private static final String FOOD_GROUP_OTHER = "OTHER";

    private FoodCatalogSearchSpecification() {
    }

    public static Specification<FoodCatalogItemEntity> accessibleSearch(
            UUID userId, String query, LocalDate onDate, String categoryCode, String pathPrefix,
            UUID brandId, Boolean verified, FoodCatalogScope scope, String foodGroup) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.or(cb.isNull(root.get("dateEffectiveFrom")),
                    cb.lessThanOrEqualTo(root.<LocalDate>get("dateEffectiveFrom"), onDate)));
            predicates.add(cb.or(cb.isNull(root.get("dateEffectiveTo")),
                    cb.greaterThanOrEqualTo(root.<LocalDate>get("dateEffectiveTo"), onDate)));
            predicates.add(cb.or(cb.isNull(root.get("ownerUserId")),
                    cb.equal(root.get("ownerUserId"), userId)));

            String lowered = query == null ? null : query.toLowerCase(Locale.ROOT);
            if (lowered != null) {
                String like = "%" + lowered + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("displayName")), like),
                        cb.like(cb.lower(root.<String>get("normalizedName")), like)));
            }
            if (categoryCode != null) {
                predicates.add(cb.equal(root.get("categoryCode"), categoryCode));
            }
            if (pathPrefix != null) {
                Subquery<Long> sub = cq.subquery(Long.class);
                Root<FoodCategoryEntity> category = sub.from(FoodCategoryEntity.class);
                sub.select(cb.literal(1L)).where(
                        cb.equal(category.get("code"), root.get("categoryCode")),
                        cb.isTrue(category.get("active")),
                        cb.like(category.<String>get("path"), pathPrefix + "%"));
                predicates.add(cb.exists(sub));
            }
            if (foodGroup != null) {
                Subquery<Long> sub = cq.subquery(Long.class);
                Root<FoodCategoryEntity> category = sub.from(FoodCategoryEntity.class);
                if (FOOD_GROUP_OTHER.equals(foodGroup)) {
                    // OTHER is also where a food lands whose category is missing or not mapped to a group.
                    sub.select(cb.literal(1L)).where(
                            cb.equal(category.get("code"), root.get("categoryCode")),
                            cb.notEqual(category.get("foodGroup"), FOOD_GROUP_OTHER));
                    predicates.add(cb.not(cb.exists(sub)));
                } else {
                    sub.select(cb.literal(1L)).where(
                            cb.equal(category.get("code"), root.get("categoryCode")),
                            cb.equal(category.get("foodGroup"), foodGroup));
                    predicates.add(cb.exists(sub));
                }
            }
            if (brandId != null) {
                predicates.add(cb.equal(root.get("brandId"), brandId));
            }
            if (verified != null) {
                predicates.add(cb.equal(root.get("verified"), verified));
            }
            if (scope != null) {
                predicates.add(cb.equal(root.get("scope"), scope));
            }

            List<Order> orders = new ArrayList<>();
            if (lowered != null) {
                Expression<Integer> relevance = cb.<Integer>selectCase()
                        .when(cb.equal(cb.lower(root.<String>get("normalizedName")), lowered), 0)
                        .when(cb.like(cb.lower(root.<String>get("normalizedName")), lowered + "%"), 1)
                        .otherwise(2);
                orders.add(cb.asc(relevance));
            }
            Expression<Integer> ownerRank = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("ownerUserId"), userId), 0)
                    .otherwise(1);
            orders.add(cb.asc(ownerRank));
            orders.add(cb.desc(root.get("verified")));
            orders.add(cb.asc(root.get("displayName")));
            cq.orderBy(orders);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
