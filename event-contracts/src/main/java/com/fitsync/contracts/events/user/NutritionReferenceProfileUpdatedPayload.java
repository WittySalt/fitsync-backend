package com.fitsync.contracts.events.user;

/**
 * The only two facts nutrition-service may know about a person's body when it looks up nutrient reference
 * ranges. user-service derives them and never publishes the gender or the birth date themselves.
 *
 * @param referenceSex FEMALE, MALE or BLENDED. BLENDED means the person has not picked a sex-specific range.
 * @param ageBand      one of "19-30", "31-50", "51-70" or "71+", or null when there is no birth date. A person
 *                     under 19 is reported as "19-30".
 */
public record NutritionReferenceProfileUpdatedPayload(
        String referenceSex,
        String ageBand) {
}
