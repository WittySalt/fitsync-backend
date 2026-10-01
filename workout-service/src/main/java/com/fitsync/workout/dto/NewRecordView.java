package com.fitsync.workout.dto;

import java.math.BigDecimal;

/**
 * A personal record the session set, given only in the answer to completing or counting it:
 * the exercise, the kind of record (MAX_WEIGHT_KG, MAX_REPS or MAX_SINGLE_SET_VOLUME_KG) and its value.
 */
public record NewRecordView(String exerciseName, String recordType, BigDecimal value) {
}
