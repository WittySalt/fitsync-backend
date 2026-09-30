package com.fitsync.nutrition.service;

import com.fitsync.nutrition.dto.NutritionPageInsightView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;

public interface NutritionPageInsightService {

    NutritionPageInsightView getNutritionPageInsight(CurrentUserPrincipal principal, String timezone);
}
