package com.fitsync.analytics.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserGoalProgressRepository extends JpaRepository<AnalyticsUserGoalProgressEntity, UUID> {

    List<AnalyticsUserGoalProgressEntity> findByUserIdOrderByTargetDateAscGoalIdAsc(UUID userId);

    List<AnalyticsUserGoalProgressEntity> findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(
            UUID userId, String goalStatus);

    List<AnalyticsUserGoalProgressEntity> findByUserIdAndGoalStatusAndGoalTypeInOrderByTargetDateAscGoalIdAsc(
            UUID userId, String goalStatus, Set<String> goalTypes);

    void deleteByUserId(UUID userId);
}
