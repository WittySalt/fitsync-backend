package com.fitsync.recommendation.coach.draft;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One reason a draft was rejected.
 *
 * <p>Structured rather than a sentence, because it has three readers with different needs. The
 * repair round needs something a model can act on precisely. The evaluation harness needs to count
 * how often each kind of mistake happens, which a free-text message makes impossible. And a
 * developer reading a failed generation needs to see which exercise was at fault.
 *
 * @param code       what rule was broken
 * @param exerciseId the exercise at fault, or null when the whole session is
 * @param message    how to fix it, phrased for the model that has to
 */
@Schema(description = "One rule a proposed session broke.")
public record DraftViolation(Code code, UUID exerciseId, String message) {

    public enum Code {
        /** The identifier does not resolve to any exercise this user can see. */
        UNKNOWN_EXERCISE,
        /** The exercise exists but has been retired from the catalogue. */
        INACTIVE_EXERCISE,
        /** The exercise needs equipment the user has not said they have. */
        EQUIPMENT_UNAVAILABLE,
        /** The session trains a muscle group that was trained the day before. */
        MUSCLE_GROUP_NOT_RECOVERED,
        /** The session is materially longer or shorter than the user's usual one. */
        DURATION_OUT_OF_RANGE,
        /** The exercise loads an area the user has flagged as injured. */
        INJURY_EXCLUDED,
        /** The draft is structurally unusable: no exercises, or nonsensical set counts. */
        MALFORMED
    }

    public static DraftViolation of(Code code, UUID exerciseId, String message) {
        return new DraftViolation(code, exerciseId, message);
    }

    public static DraftViolation session(Code code, String message) {
        return new DraftViolation(code, null, message);
    }
}
