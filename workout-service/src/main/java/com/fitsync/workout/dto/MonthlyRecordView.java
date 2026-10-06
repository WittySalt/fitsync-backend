package com.fitsync.workout.dto;

import java.math.BigDecimal;

/**
 * A record one exercise set on a day of the month: its heaviest set, heavier than in every earlier
 * completed session of that exercise. The month view's day detail names it ("Bent-Over Row, 27.5 kg").
 */
public record MonthlyRecordView(String exerciseName, BigDecimal weightKg) {
}
