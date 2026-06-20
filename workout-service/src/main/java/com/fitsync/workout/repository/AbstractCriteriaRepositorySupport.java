package com.fitsync.workout.repository;

import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

abstract class AbstractCriteriaRepositorySupport {

    protected void applyPage(TypedQuery<?> query, Pageable pageable) {
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
    }

    protected <T> void applySort(CriteriaBuilder criteriaBuilder,
                                 CriteriaQuery<T> query,
                                 Root<?> root,
                                 Sort sort) {
        if (sort.isUnsorted()) {
            return;
        }
        query.orderBy(sort.stream()
                .map(order -> order.isAscending()
                        ? criteriaBuilder.asc(resolvePath(root, order.getProperty()))
                        : criteriaBuilder.desc(resolvePath(root, order.getProperty())))
                .toList());
    }

    private Path<?> resolvePath(From<?, ?> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
