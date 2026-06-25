package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsWorkoutContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsWorkoutContributionRepository extends JpaRepository<AnalyticsWorkoutContributionEntity, UUID> {

    List<AnalyticsWorkoutContributionEntity> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    void deleteByUserId(UUID userId);
}
