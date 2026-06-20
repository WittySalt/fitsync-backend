package com.fitsync.workout.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.entity.ExerciseEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class ExerciseRepositoryImpl extends AbstractCriteriaRepositorySupport implements ExerciseRepositoryCustom {

    private final EntityManager entityManager;

    public ExerciseRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<ExerciseEntity> searchAccessible(UUID userId,
                                                 ExerciseStatus status,
                                                 String query,
                                                 ExerciseType exerciseType,
                                                 MuscleGroup primaryMuscleGroup,
                                                 ExerciseEquipment equipment,
                                                 WorkoutDifficulty difficulty,
                                                 Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExerciseEntity> queryDefinition = criteriaBuilder.createQuery(ExerciseEntity.class);
        Root<ExerciseEntity> exercise = queryDefinition.from(ExerciseEntity.class);
        List<Predicate> predicates = predicates(
                criteriaBuilder,
                exercise,
                userId,
                status,
                query,
                exerciseType,
                primaryMuscleGroup,
                equipment,
                difficulty);

        queryDefinition.select(exercise).where(predicates.toArray(Predicate[]::new));
        applySort(criteriaBuilder, queryDefinition, exercise, pageable.getSort());

        TypedQuery<ExerciseEntity> typedQuery = entityManager.createQuery(queryDefinition);
        applyPage(typedQuery, pageable);

        CriteriaQuery<Long> countDefinition = criteriaBuilder.createQuery(Long.class);
        Root<ExerciseEntity> countRoot = countDefinition.from(ExerciseEntity.class);
        countDefinition.select(criteriaBuilder.count(countRoot))
                .where(predicates(
                        criteriaBuilder,
                        countRoot,
                        userId,
                        status,
                        query,
                        exerciseType,
                        primaryMuscleGroup,
                        equipment,
                        difficulty).toArray(Predicate[]::new));

        long total = entityManager.createQuery(countDefinition).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    private List<Predicate> predicates(CriteriaBuilder criteriaBuilder,
                                       Root<ExerciseEntity> exercise,
                                       UUID userId,
                                       ExerciseStatus status,
                                       String query,
                                       ExerciseType exerciseType,
                                       MuscleGroup primaryMuscleGroup,
                                       ExerciseEquipment equipment,
                                       WorkoutDifficulty difficulty) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(exercise.get("status"), status));
        predicates.add(criteriaBuilder.or(
                criteriaBuilder.isNull(exercise.get("ownerUserId")),
                criteriaBuilder.equal(exercise.get("ownerUserId"), userId)));

        if (query != null) {
            String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(exercise.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(exercise.get("code")), pattern)));
        }
        if (exerciseType != null) {
            predicates.add(criteriaBuilder.equal(exercise.get("exerciseType"), exerciseType));
        }
        if (primaryMuscleGroup != null) {
            predicates.add(criteriaBuilder.equal(exercise.get("primaryMuscleGroup"), primaryMuscleGroup));
        }
        if (equipment != null) {
            predicates.add(criteriaBuilder.equal(exercise.get("equipment"), equipment));
        }
        if (difficulty != null) {
            predicates.add(criteriaBuilder.equal(exercise.get("difficulty"), difficulty));
        }
        return predicates;
    }
}
