package com.fitsync.nutrition.client;

import java.math.BigDecimal;

/**
 * What the Diary knows about a person from the events it has received: reference sex and age band for the reference amounts,
 * the latest weight, and the dietary pattern. Every part is null when it is not known.
 */
public record PersonFacts(PersonBasics basics, BigDecimal weightKg, String dietaryPattern) {

    public static PersonFacts unknown() {
        return new PersonFacts(PersonBasics.unknown(), null, null);
    }
}
