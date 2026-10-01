package com.fitsync.workout.service;

import com.fitsync.workout.dto.MonthlyTrainingInsightView;
import com.fitsync.workout.security.CurrentUserPrincipal;

public interface MonthlyTrainingInsightService {

    /**
     * Builds the calendar-month training summary for the authenticated user.
     *
     * @param currentUser the authenticated caller; the result is always scoped to this user
     * @param timezone    IANA zone id used to resolve local dates, or null/blank for UTC
     * @param month       the month as {@code YYYY-MM}, or null/blank for the month of today in the
     *                    given timezone; a malformed or future month is an {@link IllegalArgumentException}
     */
    MonthlyTrainingInsightView getMonthlyTrainingInsight(CurrentUserPrincipal currentUser, String timezone, String month);
}
