package com.fitsync.nutrition.domain;

public enum MealType {
    BREAKFAST,
    LUNCH,
    DINNER,
    SNACK,
    SUPPER,
    OTHER,
    /** A day marked as fasted: it holds no foods and counts as a logged day with no energy. */
    FAST
}
