package com.fitsync.workout.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class WorkoutPlanRepositoryImpl extends AbstractCriteriaRepositorySupport implements WorkoutPlanRepositoryCustom {

    private final EntityManager entityManager;

    public WorkoutPlanRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<WorkoutPlanEntity> findByUserIdAndOptionalStatus(UUID userId,
                                                                 WorkoutPlanStatus status,
                                                                 Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<WorkoutPlanEntity> queryDefinition = criteriaBuilder.createQuery(WorkoutPlanEntity.class);
        Root<WorkoutPlanEntity> plan = queryDefinition.from(WorkoutPlanEntity.class);
        List<Predicate> predicates = predicates(criteriaBuilder, plan, userId, status);

        queryDefinition.select(plan).where(predicates.toArray(Predicate[]::new));
        applySort(criteriaBuilder, queryDefinition, plan, pageable.getSort());

        TypedQuery<WorkoutPlanEntity> typedQuery = entityManager.createQuery(queryDefinition);
        applyPage(typedQuery, pageable);

        CriteriaQuery<Long> countDefinition = criteriaBuilder.createQuery(Long.class);
        Root<WorkoutPlanEntity> countRoot = countDefinition.from(WorkoutPlanEntity.class);
        countDefinition.select(criteriaBuilder.count(countRoot))
                .where(predicates(criteriaBuilder, countRoot, userId, status).toArray(Predicate[]::new));

        long total = entityManager.createQuery(countDefinition).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(CriteriaBuilder criteriaBuilder,
                                       Root<WorkoutPlanEntity> plan,
                                       UUID userId,
                                       WorkoutPlanStatus status) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(plan.get("userId"), userId));
        if (status != null) {
            predicates.add(criteriaBuilder.equal(plan.get("status"), status));
        }
        return predicates;
    }
}
