package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FoodServingView(
        UUID servingId,
        String label,
        BigDecimal quantity,
        String unit,
        BigDecimal gramsEquivalent,
        BigDecimal millilitersEquivalent,
        Integer sortOrder,
        boolean defaultServing) {
}
