package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

public record UsualMealItemView(String name, int calories, BigDecimal proteinGrams, String categoryCode) {
}
