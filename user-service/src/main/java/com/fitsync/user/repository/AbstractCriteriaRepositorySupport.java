package com.fitsync.user.repository;

import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Pageable;

abstract class AbstractCriteriaRepositorySupport {

    protected void applyPage(TypedQuery<?> query, Pageable pageable) {
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
    }
}
