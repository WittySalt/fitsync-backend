package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserActivityDateKey;
import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyticsUserDailyWorkoutRepository
        extends JpaRepository<AnalyticsUserDailyWorkoutEntity, AnalyticsUserActivityDateKey> {

    Optional<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    Optional<AnalyticsUserDailyWorkoutEntity> findTopByUserIdAndCompletedWorkoutCountGreaterThanOrderByActivityDateDesc(
            UUID userId, int completedWorkoutCount);

    @Query("select row.activityDate from AnalyticsUserDailyWorkoutEntity row "
            + "where row.userId = :userId and row.completedWorkoutCount > 0 "
            + "order by row.activityDate asc")
    List<LocalDate> findActiveActivityDatesByUserId(@Param("userId") UUID userId);

    List<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(UUID userId,
                                                                                                    LocalDate from,
                                                                                                    LocalDate to);

    List<AnalyticsUserDailyWorkoutEntity> findByUserIdAndActivityDateBetweenOrderByActivityDateDesc(UUID userId,
                                                                                                     LocalDate from,
                                                                                                     LocalDate to);

    void deleteByUserId(UUID userId);
}
