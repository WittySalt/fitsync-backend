package com.fitsync.user.domain;

/**
 * Which nutrient reference range a person wants the Nutrition Diary to compare against. PROFILE follows the
 * gender on the profile, the other values pin the range whatever the profile says.
 */
public enum NutrientReferenceRange {
    PROFILE,
    FEMALE,
    MALE,
    BLENDED
}
