package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.util.UUID;

import com.fitsync.analytics.repository.AnalyticsMealContributionRepository;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserAchievementRepository;
import com.fitsync.analytics.repository.AnalyticsUserCooksRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyNutritionRepository;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import com.fitsync.analytics.repository.AnalyticsUserGoalProgressRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import com.fitsync.analytics.repository.AnalyticsUserWorkoutRecordsRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.analytics.repository.AnalyticsWeeklyRecapMarkRepository;
import com.fitsync.analytics.repository.AnalyticsWorkoutContributionRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes everything analytics-service holds about a user when their account is deleted: contributions,
 * daily rows, goals, the dashboard snapshot, rollups, achievements, cook and record tallies and recap marks.
 *
 * <p>The user's processed-event rows are kept, with the user id cleared, rather than deleted. They hold only
 * event ids, event types and timestamps, and they are what makes a redelivery a no-op: deleting them let a
 * late redelivery of an event that had already been applied, say a WorkoutLogged still in a retry topic,
 * be projected again and recreate rows for the deleted account. Clearing the user id matches the
 * KEPT_ANONYMISED decision for that table. The AccountDeleted event itself is recorded after the purge, so
 * a redelivery of it is still ignored.
 */
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
    private final AnalyticsUserAchievementRepository achievementRepository;
    private final AnalyticsUserCooksRepository cooksRepository;
    private final AnalyticsWeeklyRecapMarkRepository recapMarkRepository;
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
                                            AnalyticsUserAchievementRepository achievementRepository,
                                            AnalyticsUserCooksRepository cooksRepository,
                                            AnalyticsWeeklyRecapMarkRepository recapMarkRepository,
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
        this.achievementRepository = achievementRepository;
        this.cooksRepository = cooksRepository;
        this.recapMarkRepository = recapMarkRepository;
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
        achievementRepository.deleteByUserId(userId);
        cooksRepository.deleteById(userId);
        recapMarkRepository.deleteByUserId(userId);
        processedEventRepository.anonymiseByUserId(userId);
        ProcessedEvents.record(processedEventRepository, event, clock);
        cacheInvalidationService.evictForUser(userId);
    }
}
