package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserStreakRollupEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyWorkoutRepository;
import com.fitsync.analytics.repository.AnalyticsUserStreakRollupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes the per-user best workout streak (AN-1) so the streaks read endpoint never scans a
 * user's full workout history at request time. The rollup recomputes from the user's active days in
 * {@code analytics_user_daily_workout}; those rows are already keyed by the user's timezone-local
 * activity date at projection time, so run detection here is plain consecutive-date walking. Days
 * with all workouts later deleted no longer count, which keeps re-runs self-healing. The rollup
 * intentionally does not store a "current" streak — that stays on the dashboard snapshot as the
 * single streak definition shared by the dashboard and streaks endpoints.
 */
@Service
public class AnalyticsStreakRollupService {

    private final AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository;
    private final AnalyticsUserStreakRollupRepository streakRollupRepository;
    private final Clock clock;

    public AnalyticsStreakRollupService(AnalyticsUserDailyWorkoutRepository dailyWorkoutRepository,
                                        AnalyticsUserStreakRollupRepository streakRollupRepository,
                                        Clock clock) {
        this.dailyWorkoutRepository = dailyWorkoutRepository;
        this.streakRollupRepository = streakRollupRepository;
        this.clock = clock;
    }

    @Transactional
    public AnalyticsUserStreakRollupEntity rollupUser(UUID userId) {
        List<LocalDate> activeDates = dailyWorkoutRepository.findActiveActivityDatesByUserId(userId);

        int bestStreakDays = 0;
        LocalDate bestStreakFrom = null;
        LocalDate bestStreakTo = null;
        LocalDate runStart = null;
        LocalDate previousDate = null;
        for (LocalDate date : activeDates) {
            if (previousDate == null || !date.equals(previousDate.plusDays(1))) {
                runStart = date;
            }
            previousDate = date;
            int runLength = (int) (date.toEpochDay() - runStart.toEpochDay()) + 1;
            // >= so equal-length runs resolve to the most recent one.
            if (runLength >= bestStreakDays) {
                bestStreakDays = runLength;
                bestStreakFrom = runStart;
                bestStreakTo = date;
            }
        }

        AnalyticsUserStreakRollupEntity rollup = streakRollupRepository.findById(userId)
                .orElseGet(() -> createRollup(userId));
        rollup.setBestStreakDays(bestStreakDays);
        rollup.setBestStreakFrom(bestStreakFrom);
        rollup.setBestStreakTo(bestStreakTo);
        rollup.setLastActiveDate(activeDates.isEmpty() ? null : activeDates.get(activeDates.size() - 1));
        rollup.setComputedAt(Instant.now(clock));
        return streakRollupRepository.save(rollup);
    }

    private AnalyticsUserStreakRollupEntity createRollup(UUID userId) {
        AnalyticsUserStreakRollupEntity rollup = new AnalyticsUserStreakRollupEntity();
        rollup.setUserId(userId);
        rollup.setBestStreakDays(0);
        return rollup;
    }
}
