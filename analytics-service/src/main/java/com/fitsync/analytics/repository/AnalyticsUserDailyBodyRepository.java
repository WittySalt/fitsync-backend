package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.entity.AnalyticsUserMetricDateKey;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserDailyBodyRepository
        extends JpaRepository<AnalyticsUserDailyBodyEntity, AnalyticsUserMetricDateKey> {

    Optional<AnalyticsUserDailyBodyEntity> findByUserIdAndMetricDate(UUID userId, LocalDate metricDate);

    Optional<AnalyticsUserDailyBodyEntity> findTopByUserIdOrderByMetricDateAsc(UUID userId);

    Optional<AnalyticsUserDailyBodyEntity> findTopByUserIdOrderByMetricDateDesc(UUID userId);

    List<AnalyticsUserDailyBodyEntity> findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(UUID userId,
                                                                                             LocalDate from,
                                                                                             LocalDate to);

    Optional<AnalyticsUserDailyBodyEntity> findTopByUserIdAndMetricDateBeforeOrderByMetricDateDesc(UUID userId,
                                                                                                    LocalDate metricDate);

    Optional<AnalyticsUserDailyBodyEntity> findTopByUserIdAndMetricDateLessThanEqualOrderByMetricDateDesc(UUID userId,
                                                                                                           LocalDate metricDate);

    void deleteByUserId(UUID userId);
}
