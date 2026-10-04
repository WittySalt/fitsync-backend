package com.fitsync.workout.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlanEntity, UUID>, WorkoutPlanRepositoryCustom {

    // "days" is deliberately left out of these graphs: Hibernate cannot join-fetch two List
    // ("bag") collections in one query (MultipleBagFetchException), and entries already needs
    // its join for entries.exercise. Reading entity.getDays() still costs only one extra query
    // per plan, lazily, inside the surrounding @Transactional method — the same characteristic
    // reading entity.getEntries() already has on every other finder in this repository.
    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    Optional<WorkoutPlanEntity> findByPlanIdAndUserId(UUID planId, UUID userId);

    @EntityGraph(attributePaths = {"entries", "entries.exercise"})
    List<WorkoutPlanEntity> findByUserIdOrderByDateTimeUpdatedDesc(UUID userId);

    List<WorkoutPlanEntity> findByUserIdAndStatusOrderByDateTimeUpdatedDesc(UUID userId, WorkoutPlanStatus status);

    List<WorkoutPlanEntity> findByUserIdAndStatus(UUID userId, WorkoutPlanStatus status);
}
