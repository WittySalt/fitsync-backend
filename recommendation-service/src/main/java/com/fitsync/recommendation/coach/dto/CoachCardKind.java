package com.fitsync.recommendation.coach.dto;

/**
 * Which Progress card or page a question was asked on. Stored by name nowhere, but sent by name by
 * the client, so a case is never renamed.
 */
public enum CoachCardKind {
    GOAL,
    TRAINING,
    FOOD,
    STRENGTH,
    CARDIO,
    SPORT,
    EXERCISE;

    /** True for the kinds that name one activity of the Training page. */
    public boolean isActivity() {
        return this == STRENGTH || this == CARDIO || this == SPORT;
    }
}
