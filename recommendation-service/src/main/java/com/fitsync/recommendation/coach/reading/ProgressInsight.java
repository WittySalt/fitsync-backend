package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * user-service's own progress-insight contract, read exactly as it defines it.
 *
 * <p>The weight series it also sends is left out: a reading works from the latest weight, the rate
 * and the goal, and ignoring the rest means a change to the series never touches this record.
 * {@code weeklyRateKg} is negative while the weight falls and null with too few weigh-ins, the goal
 * fields are null without a weight goal, and {@code projectedDate} is null when the rate does not
 * lead to the goal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProgressInsight(
        String timezone,
        LocalDate referenceDate,
        BigDecimal weightKg,
        LocalDate measuredOn,
        Integer entries,
        BigDecimal weeklyRateKg,
        Integer rateWindowDays,
        BigDecimal goalWeightKg,
        LocalDate goalDate,
        LocalDate projectedDate) {

    /** The period label a progress reading carries, because the upstream contract names none. */
    public static final String PERIOD = "LAST_28_DAYS";

    /** Days the period covers, counting the reference date itself. */
    public static final int PERIOD_DAYS = 28;
}
