package com.fitsync.nutrition.client;

import java.math.BigDecimal;

/**
 * Reads the facts about the signed-in person that live in user-service. None of these methods throws: a value
 * that cannot be read is returned as unknown, so that the nutrition request itself never fails because of
 * another service.
 */
public interface PersonLookup {

    /** Sex and age of the person, each null when unknown. */
    PersonBasics basics();

    /** The most recent weight in kilograms, or null when there is none. */
    BigDecimal latestWeightKg();

    /** The dietary pattern code such as VEGETARIAN, or null when there is none. */
    String dietaryPattern();
}
