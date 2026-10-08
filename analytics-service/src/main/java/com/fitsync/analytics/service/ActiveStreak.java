package com.fitsync.analytics.service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import com.fitsync.analytics.entity.AnalyticsUserDailyWorkoutEntity;

/**
 * The one definition of the active streak stored on the dashboard snapshot: consecutive days with a
 * completed workout, walking back from today in the user's zone. The workout projection and the dashboard
 * rebuild both write that field, so they share this rather than each keeping a copy that could drift.
 */
public final class ActiveStreak {

    /** How far back a streak is counted: past the longest milestone, so none is cut short at a week. */
    public static final int WINDOW_DAYS = 400;

    private ActiveStreak() {
    }

    /**
     * Counts the streak over the given daily rows. A streak may also start yesterday, so it is not reported
     * as broken during the day before today's workout is logged; it decays only once the latest active day
     * is two or more days before today. The rows are sorted newest first here, so the result does not
     * depend on the order a repository returned them in.
     */
    public static int days(List<AnalyticsUserDailyWorkoutEntity> rows, LocalDate today) {
        int streak = 0;
        LocalDate expectedDate = today;
        for (AnalyticsUserDailyWorkoutEntity row : rows.stream()
                .sorted(Comparator.comparing(AnalyticsUserDailyWorkoutEntity::getActivityDate).reversed())
                .toList()) {
            if (!expectedDate.equals(row.getActivityDate())) {
                if (streak == 0 && row.getActivityDate().equals(today.minusDays(1))
                        && completedCount(row) > 0) {
                    // No row for today yet, but yesterday was active: the streak starts there.
                } else {
                    break;
                }
            }
            if (completedCount(row) <= 0) {
                break;
            }
            streak++;
            expectedDate = row.getActivityDate().minusDays(1);
        }
        return streak;
    }

    private static int completedCount(AnalyticsUserDailyWorkoutEntity row) {
        return row.getCompletedWorkoutCount() == null ? 0 : row.getCompletedWorkoutCount();
    }
}
