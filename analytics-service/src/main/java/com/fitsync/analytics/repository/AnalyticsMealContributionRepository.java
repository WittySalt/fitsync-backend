package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsMealContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsMealContributionRepository extends JpaRepository<AnalyticsMealContributionEntity, UUID> {

    List<AnalyticsMealContributionEntity> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    List<AnalyticsMealContributionEntity> findByUserIdAndActivityDateBetween(UUID userId, LocalDate from, LocalDate to);

    void deleteByUserId(UUID userId);
}
