package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionProjectionService {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsMealContributionRepository mealContributionRepository;
    private final AnalyticsWorkoutContributionRepository workoutContributionRepository;
    private final AnalyticsUserDailyBodyRepository dailyBodyRepository;
    private final AnalyticsUserDailyNutritionRepository dailyNutritionRepository;
    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserGoalProgressRepository goalProgressRepository;
    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsUserStreakRollupRepository streakRollupRepository;
    private final AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository;
    private final AnalyticsUserEnergyProfileRepository energyProfileRepository;
    private final AnalyticsCacheInvalidationService cacheInvalidationService;
    private final Clock clock;

    public AccountDeletionProjectionService(AnalyticsProcessedEventRepository processedEventRepository,
                                            AnalyticsMealContributionRepository mealContributionRepository,
                                            AnalyticsWorkoutContributionRepository workoutContributionRepository,
                                            AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                            AnalyticsUserDailyNutritionRepository dailyNutritionRepository,
                                            AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                            AnalyticsUserGoalProgressRepository goalProgressRepository,
                                            AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                            AnalyticsUserStreakRollupRepository streakRollupRepository,
                                            AnalyticsUserWorkoutRecordsRepository workoutRecordsRepository,
                                            AnalyticsUserEnergyProfileRepository energyProfileRepository,
                                            AnalyticsCacheInvalidationService cacheInvalidationService,
                                            Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.mealContributionRepository = mealContributionRepository;
        this.workoutContributionRepository = workoutContributionRepository;
        this.dailyBodyRepository = dailyBodyRepository;
        this.dailyNutritionRepository = dailyNutritionRepository;
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.goalProgressRepository = goalProgressRepository;
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.streakRollupRepository = streakRollupRepository;
        this.workoutRecordsRepository = workoutRecordsRepository;
        this.energyProfileRepository = energyProfileRepository;
        this.cacheInvalidationService = cacheInvalidationService;
        this.clock = clock;
    }

    @Transactional
    public void deleteUserData(FitSyncDomainEvent<?> event) {
        if (event == null || event.userId() == null || processedEventRepository.existsById(event.eventId())) {
            return;
        }

        UUID userId = event.userId();
        mealContributionRepository.deleteByUserId(userId);
        workoutContributionRepository.deleteByUserId(userId);
        dailyBodyRepository.deleteByUserId(userId);
        dailyNutritionRepository.deleteByUserId(userId);
        dailyWorkoutRepository.deleteByUserId(userId);
        goalProgressRepository.deleteByUserId(userId);
        dashboardSnapshotRepository.deleteById(userId);
        streakRollupRepository.deleteById(userId);
        workoutRecordsRepository.deleteById(userId);
        energyProfileRepository.deleteByUserId(userId);
        processedEventRepository.deleteByUserId(userId);
        recordProcessedEvent(event);
        cacheInvalidationService.evictForUser(userId);
    }

    private void recordProcessedEvent(FitSyncDomainEvent<?> event) {
        AnalyticsProcessedEventEntity processedEvent = new AnalyticsProcessedEventEntity();
        processedEvent.setEventId(event.eventId());
        processedEvent.setEventType(event.eventType());
        processedEvent.setUserId(event.userId());
        processedEvent.setProcessedAt(event.occurredAt() == null ? Instant.now(clock) : event.occurredAt());
        processedEventRepository.save(processedEvent);
    }
}
