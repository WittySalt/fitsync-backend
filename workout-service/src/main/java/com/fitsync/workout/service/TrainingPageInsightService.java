package com.fitsync.workout.service;

import java.time.LocalDate;

import com.fitsync.workout.dto.TrainingPageInsightView;
import com.fitsync.workout.security.CurrentUserPrincipal;

public interface TrainingPageInsightService {

    /**
     * Builds the training page insight summary (period volume/duration trend, a personal record,
     * the most-trained muscle group, and recent lift progress) for the authenticated user.
     *
     * @param currentUser the authenticated caller; the result is always scoped to this user
     * @param timezone    IANA zone id used to resolve local dates, or null/blank for UTC
     * @param date        the reference day, or null to use today in the given timezone
     */
    TrainingPageInsightView getTrainingPageInsight(CurrentUserPrincipal currentUser, String timezone, LocalDate date);

    /**
     * As above, and with {@code thisWeekOnly} the period is always the current week so far (Monday to the
     * reference day), or NONE when it holds no session, instead of falling back to the last full week. The
     * coach page readings use it (me-achievements-spec.md, section 4c).
     */
    TrainingPageInsightView getTrainingPageInsight(CurrentUserPrincipal currentUser, String timezone, LocalDate date,
                                                   boolean thisWeekOnly);
}
