package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

import com.fitsync.nutrition.domain.MealType;

/** The protein of one meal slot of the day, zero for a slot that was not logged. */
public record ProteinMealView(MealType mealType, BigDecimal grams) {
}
