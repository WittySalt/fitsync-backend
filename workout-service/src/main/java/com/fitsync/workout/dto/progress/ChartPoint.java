package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;

/** One labelled value of a small chart. */
public record ChartPoint(String label, BigDecimal value) {
}
