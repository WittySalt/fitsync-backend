package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserActivityDateKey;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserDailyWorkoutRepository
        extends JpaRepository<AnalyticsUserDailyWorkoutEntity, AnalyticsUserActivityDateKey> {

    Optional<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    List<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(UUID userId,
                                                                                                    LocalDate from,
                                                                                                    LocalDate to);

    List<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(UUID userId,
                                                                                                     LocalDate from,
                                                                                                     LocalDate to);

    void deleteByUserId(UUID userId);
}
