package com.fitsync.analytics.repository;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.entity.AnalyticsUserAchievementKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserAchievementRepository
        extends JpaRepository<AnalyticsUserAchievementEntity, AnalyticsUserAchievementKey> {

    List<AnalyticsUserAchievementEntity> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
