package com.fitsync.recommendation.coach.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * The Progress card a question was asked on, and the period that card was showing.
 *
 * <p>Only these four values are trusted, and each is checked before it is used: the kind is one of
 * the enum's names, the id is a UUID or an activity key, and the dates are real dates no more than
 * 370 days apart. The card's figures are never sent. The coach reads them again for the caller with
 * the caller's own token, so nothing a client could invent reaches the model's prompt.
 *
 * @param kind which card or page
 * @param id   the exercise id for {@code EXERCISE}, the activity key such as {@code SPORT:BADMINTON}
 *             for {@code SPORT}, optional for {@code STRENGTH} and {@code CARDIO}, ignored otherwise
 * @param from the first day the card showed
 * @param to   the last day the card showed
 */
@Schema(description = "The Progress card the question was asked on and the period it showed. "
        + "Never carries the card's figures.")
public record CoachCardContext(
        @NotNull CoachCardKind kind,
        @jakarta.validation.constraints.Pattern(regexp = ID_PATTERN) String id,
        @NotNull LocalDate from,
        @NotNull LocalDate to) {

    /** A UUID, or an activity key of capital letters, underscores and colons. */
    static final String ID_PATTERN =
            "^([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|[A-Z_:]{1,40})$";

    /** The longest period a card may name, the same cap every progress endpoint applies. */
    public static final long MAX_PERIOD_DAYS = 370;

    private static final Pattern UUID_TEXT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern SPORT_KEY = Pattern.compile("^SPORT:[A-Z_]{1,34}$");

    /** The period runs forwards and spans at most {@link #MAX_PERIOD_DAYS} days. */
    @JsonIgnore
    @AssertTrue(message = "to must not be before from, and the period may span at most 370 days")
    public boolean isPeriodValid() {
        if (from == null || to == null) {
            return true;
        }
        return !to.isBefore(from) && ChronoUnit.DAYS.between(from, to) <= MAX_PERIOD_DAYS;
    }

    /** The id has the shape its kind needs. */
    @JsonIgnore
    @AssertTrue(message = "id does not match the card kind")
    public boolean isIdValidForKind() {
        if (kind == null) {
            return true;
        }
        return switch (kind) {
            case EXERCISE -> id != null && UUID_TEXT.matcher(id).matches();
            case SPORT -> id != null && SPORT_KEY.matcher(id).matches();
            case STRENGTH, CARDIO -> id == null || id.equals(kind.name());
            case GOAL, TRAINING, FOOD -> true;
        };
    }

    /** The activity key the Training page uses for this card, or null when the card is not an activity. */
    @JsonIgnore
    public String activityKey() {
        if (kind == null || !kind.isActivity()) {
            return null;
        }
        return kind == CoachCardKind.SPORT ? id : kind.name();
    }
}
