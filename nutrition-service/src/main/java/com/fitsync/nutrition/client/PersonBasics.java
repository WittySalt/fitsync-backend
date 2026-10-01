package com.fitsync.nutrition.client;

/**
 * What decides a person's daily reference amounts: sex as MALE or FEMALE, and age in years. Either is null
 * when user-service does not know it or could not be reached, and the references then fall back to an adult.
 */
public record PersonBasics(String sex, Integer age) {

    public static PersonBasics unknown() {
        return new PersonBasics(null, null);
    }
}
