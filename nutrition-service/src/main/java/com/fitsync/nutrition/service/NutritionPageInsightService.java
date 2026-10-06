package com.fitsync.nutrition.service;

import com.fitsync.nutrition.dto.NutritionPageInsightView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;

public interface NutritionPageInsightService {

    /** The last full week, compared with the week before it. */
    NutritionPageInsightView getNutritionPageInsight(CurrentUserPrincipal principal, String timezone);

    /**
     * With {@code thisWeek} the period is the current week so far, Monday to today, compared with the last
     * full week; the coach page reading uses it (me-achievements-spec.md, section 4c).
     */
    NutritionPageInsightView getNutritionPageInsight(CurrentUserPrincipal principal, String timezone, boolean thisWeek);
}
