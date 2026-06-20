package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class WorkoutSessionRepositoryImpl extends AbstractCriteriaRepositorySupport
        implements WorkoutSessionRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<UUID> findHistoryIds(UUID userId,
                                     Instant from,
                                     Instant to,
                                     WorkoutSessionStatus status,
                                     UUID planId,
                                     Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> queryDefinition = criteriaBuilder.createQuery(UUID.class);
        Root<WorkoutSessionEntity> session = queryDefinition.from(WorkoutSessionEntity.class);
        List<Predicate> predicates = predicates(criteriaBuilder, session, userId, from, to, status, planId);

        queryDefinition.select(session.get("sessionId"))
                .where(predicates.toArray(Predicate[]::new));
        applySort(criteriaBuilder, queryDefinition, session, pageable.getSort());

        TypedQuery<UUID> typedQuery = entityManager.createQuery(queryDefinition);
        applyPage(typedQuery, pageable);

        CriteriaQuery<Long> countDefinition = criteriaBuilder.createQuery(Long.class);
        Root<WorkoutSessionEntity> countRoot = countDefinition.from(WorkoutSessionEntity.class);
        countDefinition.select(criteriaBuilder.count(countRoot))
                .where(predicates(criteriaBuilder, countRoot, userId, from, to, status, planId)
                        .toArray(Predicate[]::new));

        long total = entityManager.createQuery(countDefinition).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(CriteriaBuilder criteriaBuilder,
                                       Root<WorkoutSessionEntity> session,
                                       UUID userId,
                                       Instant from,
                                       Instant to,
                                       WorkoutSessionStatus status,
                                       UUID planId) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(session.get("userId"), userId));
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(session.get("startedAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(session.get("startedAt"), to));
        }
        if (status != null) {
            predicates.add(criteriaBuilder.equal(session.get("status"), status));
        }
        if (planId != null) {
            predicates.add(criteriaBuilder.equal(session.get("planId"), planId));
        }
        return predicates;
    }
}
