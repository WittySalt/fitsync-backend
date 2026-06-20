package com.fitsync.user.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.user.entity.BodyMetricEntryEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Pageable;

public class BodyMetricEntryRepositoryImpl extends AbstractCriteriaRepositorySupport
        implements BodyMetricEntryRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<BodyMetricEntryEntity> findHistory(UUID userId, Instant from, Instant to, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<BodyMetricEntryEntity> queryDefinition = criteriaBuilder.createQuery(BodyMetricEntryEntity.class);
        Root<BodyMetricEntryEntity> metric = queryDefinition.from(BodyMetricEntryEntity.class);

        queryDefinition.select(metric)
                .where(predicates(criteriaBuilder, metric, userId, from, to).toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.desc(metric.<Instant>get("measuredAt")));

        var typedQuery = entityManager.createQuery(queryDefinition);
        applyPage(typedQuery, pageable);
        return typedQuery.getResultList();
    }

    @Override
    public List<BodyMetricEntryEntity> findTrendSource(UUID userId, Instant from, Instant to) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<BodyMetricEntryEntity> queryDefinition = criteriaBuilder.createQuery(BodyMetricEntryEntity.class);
        Root<BodyMetricEntryEntity> metric = queryDefinition.from(BodyMetricEntryEntity.class);

        queryDefinition.select(metric)
                .where(predicates(criteriaBuilder, metric, userId, from, to).toArray(Predicate[]::new))
                .orderBy(criteriaBuilder.asc(metric.<Instant>get("measuredAt")));

        return entityManager.createQuery(queryDefinition).getResultList();
    }

    private List<Predicate> predicates(CriteriaBuilder criteriaBuilder,
                                       Root<BodyMetricEntryEntity> metric,
                                       UUID userId,
                                       Instant from,
                                       Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(metric.get("userId"), userId));
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(metric.<Instant>get("measuredAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(metric.<Instant>get("measuredAt"), to));
        }
        return predicates;
    }
}
