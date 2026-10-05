package com.fitsync.nutrition.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** The serving a food is shown with before its detail loads, such as "1 sandwich" or "100 g". */
public record DefaultServingView(UUID servingId, String label, BigDecimal gramsEquivalent) {
}
