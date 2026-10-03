package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

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
 * @param card   the Progress card the fact belongs to ({@code GOAL}, {@code TRAINING} or {@code FOOD}),
 *               so a client can scroll to it, or null on pages without cards; left out of the JSON when
 *               null and never part of the facts hash
 */
public record ReadingFact(
        int number,
        String key,
        String label,
        String value,
        String unit,
        BigDecimal numericValue,
        String note,
        String detail,
        @JsonInclude(JsonInclude.Include.NON_NULL) String card) {

    @JsonCreator
    public ReadingFact {
        // The canonical constructor is the one Jackson uses when a stored fact is read back.
    }

    /** A fact that belongs to no card, which is every fact on the workout and nutrition pages. */
    public ReadingFact(int number, String key, String label, String value, String unit, BigDecimal numericValue,
                       String note, String detail) {
        this(number, key, label, value, unit, numericValue, note, detail, null);
    }

    /** The same fact, placed on a Progress card. */
    public ReadingFact withCard(String newCard) {
        return new ReadingFact(number, key, label, value, unit, numericValue, note, detail, newCard);
    }
}
