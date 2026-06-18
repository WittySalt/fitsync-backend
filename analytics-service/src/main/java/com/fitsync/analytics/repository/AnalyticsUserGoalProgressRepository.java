package com.fitsync.analytics.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserGoalProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserGoalProgressRepository extends JpaRepository<AnalyticsUserGoalProgressEntity, UUID> {

    List<AnalyticsUserGoalProgressEntity> findByUserIdOrderByTargetDateAscGoalIdAsc(UUID userId);

    List<AnalyticsUserGoalProgressEntity> findByUserIdAndGoalStatusOrderByTargetDateAscGoalIdAsc(
            UUID userId, String goalStatus);
}
