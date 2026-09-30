package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

import com.fitsync.nutrition.domain.MealType;

public record MealProteinShareView(MealType mealType, String label, BigDecimal gramsPerDay) {
}
