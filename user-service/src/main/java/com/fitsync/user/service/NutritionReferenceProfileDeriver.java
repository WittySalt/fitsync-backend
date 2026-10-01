package com.fitsync.user.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

import com.fitsync.user.domain.Gender;
import com.fitsync.user.domain.NutrientReferenceRange;

/**
 * Turns the gender, the birth date and the nutrient reference range preference into the two derived values that
 * nutrition-service may know. The gender and the birth date themselves never leave user-service.
 */
public final class NutritionReferenceProfileDeriver {

    /** The reference sex and the age band that go on the NutritionReferenceProfileUpdated event. */
    public record Derived(String referenceSex, String ageBand) {
    }

    private static final String SEX_FEMALE = "FEMALE";
    private static final String SEX_MALE = "MALE";
    private static final String SEX_BLENDED = "BLENDED";

    private NutritionReferenceProfileDeriver() {
    }

    public static Derived derive(Gender gender, LocalDate birthDate, NutrientReferenceRange preference, Clock clock) {
        return new Derived(referenceSex(gender, preference), ageBand(birthDate, clock));
    }

    static String referenceSex(Gender gender, NutrientReferenceRange preference) {
        if (preference == NutrientReferenceRange.FEMALE) {
            return SEX_FEMALE;
        }
        if (preference == NutrientReferenceRange.MALE) {
            return SEX_MALE;
        }
        if (preference == NutrientReferenceRange.BLENDED) {
            return SEX_BLENDED;
        }
        if (gender == Gender.FEMALE) {
            return SEX_FEMALE;
        }
        if (gender == Gender.MALE) {
            return SEX_MALE;
        }
        return SEX_BLENDED;
    }

    static String ageBand(LocalDate birthDate, Clock clock) {
        if (birthDate == null) {
            return null;
        }
        int years = Period.between(birthDate, LocalDate.now(clock)).getYears();
        if (years <= 30) {
            return "19-30";
        }
        if (years <= 50) {
            return "31-50";
        }
        if (years <= 70) {
            return "51-70";
        }
        return "71+";
    }
}
