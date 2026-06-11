package com.fitsync.nutrition.dto;

import java.math.BigDecimal;

public record NutrientDetailView(
        String nutrientCode,
        String displayName,
        BigDecimal amount,
        String unit,
        Integer sortOrder) {
}
