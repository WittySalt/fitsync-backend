package com.fitsync.nutrition.service;

import com.fitsync.nutrition.dto.UsualMealsView;
import com.fitsync.nutrition.security.CurrentUserPrincipal;

public interface UsualMealsService {

    UsualMealsView getUsualMeals(CurrentUserPrincipal principal, String timezone);
}
