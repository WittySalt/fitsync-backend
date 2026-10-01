package com.fitsync.nutrition.client;

/**
 * What decides a person's daily reference amounts. referenceSex is FEMALE, MALE or BLENDED, and age is the
 * youngest age of the person's age band (19, 31, 51 or 71). Either is null when it is not known, and the
 * references then fall back to an adult.
 */
public record PersonBasics(String referenceSex, Integer age) {

    public static PersonBasics unknown() {
        return new PersonBasics(null, null);
    }
}
