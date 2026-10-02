package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One day of a nutrient in the range. logged is false, with amount and share null, for a day without meals.
 * On a logged day amount and share are null when that day's foods carry no value for the nutrient.
 */
public record NutrientDayView(LocalDate date, boolean logged, BigDecimal amount, BigDecimal share) {
}
