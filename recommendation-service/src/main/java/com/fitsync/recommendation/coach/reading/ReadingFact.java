package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;

/**
 * One measured fact behind a page reading, numbered for citation.
 *
 * <p>This is both the internal unit {@link ReadingFactsBuilder} produces and the exact JSON shape
 * the response and the model prompt send it in; a fact never carries anything beyond what a user or
 * a model should see; there is no server-only field hiding on it.
 *
 * @param note   a short label for the tile, meant to stay well under about fourteen characters
 *               (a percentage change, a weekday name, "Every one"), or empty when there is nothing
 *               short to say
 * @param detail the longer wording the short note is standing in for, or empty when there is none
 */
public record ReadingFact(
        int number,
        String key,
        String label,
        String value,
        String unit,
        BigDecimal numericValue,
        String note,
        String detail) {
}
