package com.fitsync.analytics.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserActivityDateKey;
import com.fitsync.analytics.entity.AnalyticsUserDailyNutritionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsUserDailyNutritionRepository
        extends JpaRepository<AnalyticsUserDailyNutritionEntity, AnalyticsUserActivityDateKey> {

    Optional<AnalyticsUserDailyNutritionEntity> findByUserIdAndActivityDate(UUID userId, LocalDate activityDate);

    Optional<AnalyticsUserDailyNutritionEntity> findTopByUserIdAndMealCountGreaterThanOrderByActivityDateAsc(
            UUID userId, int mealCount);

    List<AnalyticsUserDailyNutritionEntity> findByUserIdAndActivityDateBetweenOrderByActivityDateAsc(UUID userId,
                                                                                                      LocalDate from,
                                                                                                      LocalDate to);

    List<AnalyticsUserDailyNutritionEntity> findByUserIdOrderByActivityDateAsc(UUID userId);

    void deleteByUserId(UUID userId);
}
