package com.fitsync.recommendation.coach.draft;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One exercise in a proposed session.
 *
 * <p>The name is carried alongside the identifier deliberately, even though it is derivable. It is
 * a snapshot: a user looking at an accepted draft months later should see the exercise they were
 * shown, not whatever that catalogue row has since been renamed to.
 *
 * @param exerciseId  a real catalogue row; the validator rejects anything that does not resolve
 * @param name        the catalogue name at the time of drafting
 * @param sets        working sets
 * @param reps        repetitions per set, or null for a timed exercise
 * @param seconds     duration per set for a timed exercise, or null for a rep-based one
 * @param restSeconds rest between sets
 * @param rationale   one line on why this exercise is in this session
 */
@Schema(description = "One exercise in a proposed session, resolved to a catalogue row.")
public record DraftExercise(
        UUID exerciseId,
        String name,
        int sets,
        Integer reps,
        Integer seconds,
        int restSeconds,
        String rationale) {
}
