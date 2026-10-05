package com.fitsync.nutrition.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.nutrition.domain.FoodCatalogScope;
import com.fitsync.nutrition.entity.FoodBrandEntity;
import com.fitsync.nutrition.entity.FoodCatalogItemEntity;
import com.fitsync.nutrition.entity.FoodCategoryEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
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

    private static final char LIKE_ESCAPE = '\\';

    private FoodCatalogSearchSpecification() {
    }

    /** The characters a word may follow, so "nugget" starts a word in "Chicken, (nuggets)" and "stick/nugget". */
    private static final String[] WORD_SEPARATORS = {" ", ",", "(", "/", "-"};

    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /**
     * Relevance of a match, lower is better: 0 the name is the query, 1 the name (or the brand) starts with it,
     * 2 a word of the name starts with it, 3 it is only contained somewhere, for example in the full USDA
     * name that a branded item keeps in its normalized name. Built as a CASE in the query so that the order
     * holds across pages.
     */
    private static Expression<Integer> relevanceTier(Root<FoodCatalogItemEntity> root, CriteriaQuery<?> cq,
                                                     CriteriaBuilder cb, String lowered) {
        Expression<String> name = cb.lower(root.<String>get("displayName"));
        String escaped = escapeLike(lowered);

        Subquery<Long> brandStarts = cq.subquery(Long.class);
        Root<FoodBrandEntity> brand = brandStarts.from(FoodBrandEntity.class);
        brandStarts.select(cb.literal(1L)).where(
                cb.equal(brand.get("brandId"), root.get("brandId")),
                cb.like(cb.lower(brand.<String>get("name")), escaped + "%", LIKE_ESCAPE));

        Predicate[] wordStarts = new Predicate[WORD_SEPARATORS.length];
        for (int i = 0; i < WORD_SEPARATORS.length; i++) {
            wordStarts[i] = cb.like(name, "%" + WORD_SEPARATORS[i] + escaped + "%", LIKE_ESCAPE);
        }
        return cb.<Integer>selectCase()
                .when(cb.equal(name, lowered), 0)
                .when(cb.or(cb.like(name, escaped + "%", LIKE_ESCAPE), cb.exists(brandStarts)), 1)
                .when(cb.or(wordStarts), 2)
                .otherwise(3);
    }

    public static Specification<FoodCatalogItemEntity> accessibleSearch(
            UUID userId, String query, LocalDate onDate, String categoryCode, String pathPrefix,
            UUID brandId, Boolean verified, FoodCatalogScope scope, String foodGroup) {
        boolean otherGroup = FOOD_GROUP_OTHER.equals(foodGroup);
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
                String like = "%" + escapeLike(lowered) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.<String>get("displayName")), like, LIKE_ESCAPE),
                        cb.like(cb.lower(root.<String>get("normalizedName")), like, LIKE_ESCAPE)));
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
                if (otherGroup) {
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
                orders.add(cb.asc(relevanceTier(root, cq, cb, lowered)));
            }
            Expression<Integer> ownerRank = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("ownerUserId"), userId), 0)
                    .otherwise(1);
            orders.add(cb.asc(ownerRank));
            // A branded item (brand_id set) comes before an unbranded one, then the shorter name, then the
            // name, then the id so that two rows never tie and paging stays stable.
            Expression<Integer> brandRank = cb.<Integer>selectCase()
                    .when(cb.isNull(root.get("brandId")), 1)
                    .otherwise(0);
            orders.add(cb.asc(brandRank));
            orders.add(cb.asc(cb.length(root.<String>get("displayName"))));
            orders.add(cb.asc(root.get("displayName")));
            orders.add(cb.asc(root.get("foodId")));
            cq.orderBy(orders);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
