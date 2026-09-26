package com.fitsync.recommendation.coach.draft;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A training session the coach is proposing.
 *
 * <p>Every exercise carries a real catalogue identifier rather than a name the model chose, which
 * is what makes accepting one a database operation rather than an interpretation. A name can be
 * plausible and wrong; an identifier either resolves or it does not, and the validator checks that
 * it does before a user ever sees the draft.
 *
 * <p>Each exercise also carries one line of reasoning. That is a product decision as much as a
 * transparency one: a plan a user does not understand is a plan they abandon in week two, and the
 * reason has to be written while the choice is being made rather than reconstructed afterwards.
 *
 * @param title          what the user sees at the top of the card
 * @param focus          the muscle groups this session is built around
 * @param estimatedMinutes total working time including rest, used by the duration rule
 * @param exercises      in the order they should be performed
 * @param summary        two sentences on why this session, for today, for this user
 */
@Schema(description = "A proposed training session, every exercise resolved to a catalogue row.")
public record SessionDraft(
        String title,
        List<String> focus,
        int estimatedMinutes,
        List<DraftExercise> exercises,
        String summary) {

    public SessionDraft {
        focus = focus == null ? List.of() : List.copyOf(focus);
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<String> focus() {
        return List.copyOf(focus);
    }

    @Override
    public List<DraftExercise> exercises() {
        return List.copyOf(exercises);
    }
}
