package com.fitsync.workout.service;

import java.time.LocalDate;

import com.fitsync.workout.dto.WeeklyTrainingInsightView;
import com.fitsync.workout.security.CurrentUserPrincipal;

public interface WeeklyTrainingInsightService {

    /**
     * Builds the weekly training summary for the authenticated user.
     *
     * @param currentUser the authenticated caller; the result is always scoped to this user
     * @param timezone    IANA zone id used to resolve local dates, or null/blank for UTC
     * @param date        the reference day, or null to use today in the given timezone
     */
    WeeklyTrainingInsightView getWeeklyTrainingInsight(CurrentUserPrincipal currentUser, String timezone, LocalDate date);
}
