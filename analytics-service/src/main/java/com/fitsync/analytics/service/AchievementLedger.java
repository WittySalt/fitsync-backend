package com.fitsync.analytics.service;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.entity.AnalyticsUserAchievementKey;
import com.fitsync.analytics.repository.AnalyticsUserAchievementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The stored badges. Rows are only ever added: a badge earned once keeps its first date for good, so a
 * row that is already there is left as it is.
 */
@Service
public class AchievementLedger {

    private final AnalyticsUserAchievementRepository repository;

    public AchievementLedger(AnalyticsUserAchievementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsUserAchievementEntity> stored(UUID userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public void add(List<AnalyticsUserAchievementEntity> earned) {
        for (AnalyticsUserAchievementEntity row : earned) {
            if (!repository.existsById(new AnalyticsUserAchievementKey(row.getUserId(), row.getCode()))) {
                repository.save(row);
            }
        }
    }
}
