package com.fitsync.analytics.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserDailyBodyEntity;
import com.fitsync.analytics.repository.AnalyticsUserDailyBodyRepository;

/**
 * The dashboard's {@code weightChange30d}: the weight on a measurement date minus the weight on the newest
 * body row at least 30 days before it. Both the BodyMetricsRecorded projection and the dashboard rebuild
 * write the field; the rebuild used to write the change since the first weigh-in instead, so the number
 * changed meaning every night. They now share this so the two cannot drift apart again.
 */
public final class BodyWeightChange {

    static final int WINDOW_DAYS = 30;

    private BodyWeightChange() {
    }

    /**
     * Null when the measurement carries no weight, when there is no body row 30 or more days earlier, or
     * when that row has no weight: a change over a shorter span would be shown as a 30-day change.
     */
    public static BigDecimal over30Days(AnalyticsUserDailyBodyRepository dailyBodyRepository,
                                        UUID userId,
                                        LocalDate metricDate,
                                        BigDecimal weightKg) {
        if (weightKg == null || metricDate == null) {
            return null;
        }
        return dailyBodyRepository.findTopByUserIdAndMetricDateLessThanEqualOrderByMetricDateDesc(
                        userId,
                        metricDate.minusDays(WINDOW_DAYS))
                .map(AnalyticsUserDailyBodyEntity::getWeightKg)
                .map(weightKg::subtract)
                .orElse(null);
    }
}
