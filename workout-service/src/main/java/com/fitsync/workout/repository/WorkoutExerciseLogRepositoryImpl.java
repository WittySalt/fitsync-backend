package com.fitsync.workout.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.dto.ExerciseHistoryEntryView;
import com.fitsync.workout.dto.ExerciseRecordSetRow;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class WorkoutExerciseLogRepositoryImpl extends AbstractCriteriaRepositorySupport
        implements WorkoutExerciseLogRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<ExerciseHistoryEntryView> findExerciseHistory(UUID userId,
                                                              UUID exerciseId,
                                                              Instant from,
                                                              Instant to,
                                                              Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExerciseHistoryEntryView> queryDefinition =
                criteriaBuilder.createQuery(ExerciseHistoryEntryView.class);
        Root<WorkoutExerciseSetLogEntity> setLog = queryDefinition.from(WorkoutExerciseSetLogEntity.class);
        Join<WorkoutExerciseSetLogEntity, SessionEntry> exerciseLog = setLog.join("exerciseLog");
        Join<SessionEntry, WorkoutSessionEntity> session = exerciseLog.join("session");
        Join<SessionEntry, ExerciseEntity> exercise = exerciseLog.join("exercise");
        List<Predicate> predicates = basePredicates(
                criteriaBuilder,
                setLog,
                exerciseLog,
                session,
                exercise,
                userId,
                exerciseId,
                from,
                to);

        queryDefinition.select(criteriaBuilder.construct(
                        ExerciseHistoryEntryView.class,
                        session.get("sessionId"),
                        exerciseLog.get("exerciseLogId"),
                        setLog.get("setLogId"),
                        exercise.get("exerciseId"),
                        exerciseLog.get("exerciseNameSnapshot"),
                        session.get("name"),
                        session.get("startedAt"),
                        session.get("completedAt"),
                        exerciseLog.get("sequence"),
                        setLog.get("setNumber"),
                        setLog.get("reps"),
                        setLog.get("weightKg"),
                        setLog.get("durationSeconds"),
                        setLog.get("distanceKm"),
                        setLog.get("restSeconds"),
                        setLog.get("perceivedExertion"),
                        setLog.get("notes")))
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(
                        criteriaBuilder.desc(session.get("startedAt")),
                        criteriaBuilder.asc(exerciseLog.get("sequence")),
                        criteriaBuilder.asc(setLog.get("setNumber")));

        TypedQuery<ExerciseHistoryEntryView> typedQuery = entityManager.createQuery(queryDefinition);
        applyPage(typedQuery, pageable);

        CriteriaQuery<Long> countDefinition = criteriaBuilder.createQuery(Long.class);
        Root<WorkoutExerciseSetLogEntity> countSetLog = countDefinition.from(WorkoutExerciseSetLogEntity.class);
        Join<WorkoutExerciseSetLogEntity, SessionEntry> countExerciseLog = countSetLog.join("exerciseLog");
        Join<SessionEntry, WorkoutSessionEntity> countSession = countExerciseLog.join("session");
        Join<SessionEntry, ExerciseEntity> countExercise = countExerciseLog.join("exercise");
        countDefinition.select(criteriaBuilder.count(countSetLog))
                .where(basePredicates(
                        criteriaBuilder,
                        countSetLog,
                        countExerciseLog,
                        countSession,
                        countExercise,
                        userId,
                        exerciseId,
                        from,
                        to).toArray(Predicate[]::new));

        long total = entityManager.createQuery(countDefinition).getSingleResult();
        return new PageImpl<>(typedQuery.getResultList(), pageable, total);
    }

    @Override
    public List<ExerciseRecordSetRow> findRecordRows(UUID userId, UUID exerciseId, UUID excludedSessionId) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ExerciseRecordSetRow> queryDefinition = criteriaBuilder.createQuery(ExerciseRecordSetRow.class);
        Root<WorkoutExerciseSetLogEntity> setLog = queryDefinition.from(WorkoutExerciseSetLogEntity.class);
        Join<WorkoutExerciseSetLogEntity, SessionEntry> exerciseLog = setLog.join("exerciseLog");
        Join<SessionEntry, WorkoutSessionEntity> session = exerciseLog.join("session");
        Join<SessionEntry, ExerciseEntity> exercise = exerciseLog.join("exercise");
        List<Predicate> predicates = basePredicates(
                criteriaBuilder,
                setLog,
                exerciseLog,
                session,
                exercise,
                userId,
                exerciseId,
                null,
                null);

        if (excludedSessionId != null) {
            predicates.add(criteriaBuilder.notEqual(session.get("sessionId"), excludedSessionId));
        }

        queryDefinition.select(criteriaBuilder.construct(
                        ExerciseRecordSetRow.class,
                        session.get("sessionId"),
                        exerciseLog.get("exerciseLogId"),
                        exercise.get("exerciseId"),
                        exerciseLog.get("exerciseNameSnapshot"),
                        session.get("startedAt"),
                        session.get("completedAt"),
                        setLog.get("reps"),
                        setLog.get("weightKg")))
                .where(predicates.toArray(Predicate[]::new));

        return entityManager.createQuery(queryDefinition).getResultList();
    }

    private List<Predicate> basePredicates(CriteriaBuilder criteriaBuilder,
                                           Root<WorkoutExerciseSetLogEntity> setLog,
                                           Join<WorkoutExerciseSetLogEntity, SessionEntry> exerciseLog,
                                           Join<SessionEntry, WorkoutSessionEntity> session,
                                           Join<SessionEntry, ExerciseEntity> exercise,
                                           UUID userId,
                                           UUID exerciseId,
                                           Instant from,
                                           Instant to) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.equal(session.get("userId"), userId));
        predicates.add(criteriaBuilder.equal(exercise.get("exerciseId"), exerciseId));
        predicates.add(criteriaBuilder.equal(session.get("status"), WorkoutSessionStatus.COMPLETED));
        predicates.add(criteriaBuilder.equal(setLog.get("status"), WorkoutSetStatus.DONE));
        predicates.add(criteriaBuilder.isTrue(setLog.get("logged")));
        if (from != null) {
            predicates.add(criteriaBuilder.greaterThanOrEqualTo(session.get("startedAt"), from));
        }
        if (to != null) {
            predicates.add(criteriaBuilder.lessThanOrEqualTo(session.get("startedAt"), to));
        }
        return predicates;
    }
}
