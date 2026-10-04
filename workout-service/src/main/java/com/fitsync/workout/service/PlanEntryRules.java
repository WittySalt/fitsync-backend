package com.fitsync.workout.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;

/**
 * The rules for the entries of a plan day, pure functions over the request. A strength or mobility
 * day holds moves (SET entries), a cardio or sport day holds time segments (SEGMENT entries), and a
 * day never mixes the two. The segments of a day, each counted as many times as its rounds, add up to
 * the day's duration within a minute. A cardio or sport day sent without any entry gets the default
 * shape: five minutes of warm-up, the rest steady, five minutes of cool-down.
 *
 * <p>The older exercises shape of a day is still accepted on a cardio day for one release and stored as
 * SET entries; the entries shape follows the rules above. Every message is fixed and never echoes the
 * request.
 */
public final class PlanEntryRules {

    /** Segments number their sequence from here, so the moves of the same day or session keep theirs. */
    public static final int SEGMENT_SEQUENCE_BASE = 1000;
    static final int WARMUP_MINUTES = 5;
    static final int COOLDOWN_MINUTES = 5;
    static final int DURATION_TOLERANCE_SECONDS = 60;
    private static final int SECONDS_PER_MINUTE = 60;
    static final int HIIT_ROUNDS = 8;
    static final int HIIT_WORK_SECONDS = 30;
    static final int HIIT_EASY_SECONDS = 60;

    static final String MIXED_DAY_MESSAGE = "A day holds either moves or time segments, not both.";
    static final String SEGMENTS_ON_MOVES_DAY_MESSAGE = "A strength or mobility day holds moves, not time segments.";
    static final String MOVE_NEEDS_EXERCISE_MESSAGE = "A move needs an exercise.";
    static final String SEGMENT_NEEDS_KIND_MESSAGE = "A time segment needs a kind and a length.";
    static final String SEGMENTS_MUST_FILL_DAY_MESSAGE =
            "The time segments must add up to the day's duration, within a minute.";
    static final String ENTRIES_OR_EXERCISES_MESSAGE = "A day lists its entries or its exercises, not both.";
    static final String DUPLICATE_POSITION_MESSAGE = "Entry positions must be unique within a day.";
    static final String SPORT_DAY_HAS_NO_MOVES_MESSAGE = "A sport day has no exercises.";
    static final String MOVES_ON_SEGMENTS_DAY_MESSAGE = "A cardio day holds time segments, not moves.";

    private PlanEntryRules() {
    }

    /** True for the kinds of day whose entries are time segments. */
    public static boolean holdsSegments(SessionKind kind) {
        return kind == SessionKind.CARDIO || kind == SessionKind.SPORT;
    }

    /** Checks one day's entries against the rules, throwing IllegalArgumentException on the first break. */
    public static void validateDay(WorkoutPlanDayInput day) {
        List<PlanEntryInput> entries = day.entries();
        if (entries.isEmpty()) {
            return;
        }
        if (!day.exercises().isEmpty()) {
            throw new IllegalArgumentException(ENTRIES_OR_EXERCISES_MESSAGE);
        }
        boolean anySet = false;
        boolean anySegment = false;
        Set<Integer> positions = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            PlanEntryInput entry = entries.get(index);
            if (!positions.add(positionOf(entry, index))) {
                throw new IllegalArgumentException(DUPLICATE_POSITION_MESSAGE);
            }
            if (entry.type() == EntryType.SET) {
                anySet = true;
                if (entry.exerciseId() == null) {
                    throw new IllegalArgumentException(MOVE_NEEDS_EXERCISE_MESSAGE);
                }
            } else {
                anySegment = true;
                if (entry.kind() == null || entry.plannedSeconds() == null) {
                    throw new IllegalArgumentException(SEGMENT_NEEDS_KIND_MESSAGE);
                }
            }
        }
        if (anySet && anySegment) {
            throw new IllegalArgumentException(MIXED_DAY_MESSAGE);
        }
        if (anySegment && !holdsSegments(day.kind())) {
            throw new IllegalArgumentException(SEGMENTS_ON_MOVES_DAY_MESSAGE);
        }
        if (anySet && day.kind() == SessionKind.SPORT) {
            throw new IllegalArgumentException(SPORT_DAY_HAS_NO_MOVES_MESSAGE);
        }
        if (anySet && day.kind() == SessionKind.CARDIO) {
            throw new IllegalArgumentException(MOVES_ON_SEGMENTS_DAY_MESSAGE);
        }
        if (anySegment && day.estimatedDurationMinutes() != null) {
            int planned = totalSeconds(entries);
            int expected = day.estimatedDurationMinutes() * SECONDS_PER_MINUTE;
            if (Math.abs(planned - expected) > DURATION_TOLERANCE_SECONDS) {
                throw new IllegalArgumentException(SEGMENTS_MUST_FILL_DAY_MESSAGE);
            }
        }
    }

    /** The position of an entry, or its place in the list when it has none. */
    public static int positionOf(PlanEntryInput entry, int index) {
        return entry.position() == null ? index + 1 : entry.position();
    }

    /** The seconds the segments of a day take, each counted as many times as its rounds. */
    public static int totalSeconds(List<PlanEntryInput> entries) {
        int total = 0;
        for (PlanEntryInput entry : entries) {
            if (entry.type() == EntryType.SEGMENT && entry.plannedSeconds() != null) {
                total += entry.plannedSeconds() * roundsOf(entry.rounds());
            }
        }
        return total;
    }

    /** Rounds of one or more; empty means once. */
    public static int roundsOf(Integer rounds) {
        return rounds == null || rounds < 1 ? 1 : rounds;
    }

    /**
     * The default shape of a cardio or sport day of the given minutes: 5 warm-up, the rest steady, 5
     * cool-down; a day of ten minutes or less is one steady stretch. Empty without a duration. The steady
     * stretch of a sport day is labelled with the sport.
     */
    public static List<PlanEntryInput> defaultSegments(Integer durationMinutes, SportActivity sport) {
        if (durationMinutes == null || durationMinutes < 1) {
            return List.of();
        }
        String steadyLabel = sport == null ? SegmentKind.STEADY.label() : sport.label();
        if (durationMinutes <= WARMUP_MINUTES + COOLDOWN_MINUTES) {
            return List.of(PlanEntryInput.segment(SegmentKind.STEADY, steadyLabel, 1,
                    durationMinutes * SECONDS_PER_MINUTE, null));
        }
        List<PlanEntryInput> segments = new ArrayList<>();
        segments.add(PlanEntryInput.segment(SegmentKind.WARMUP, SegmentKind.WARMUP.label(), 1,
                WARMUP_MINUTES * SECONDS_PER_MINUTE, null));
        segments.add(PlanEntryInput.segment(SegmentKind.STEADY, steadyLabel, 2,
                (durationMinutes - WARMUP_MINUTES - COOLDOWN_MINUTES) * SECONDS_PER_MINUTE, null));
        segments.add(PlanEntryInput.segment(SegmentKind.COOLDOWN, SegmentKind.COOLDOWN.label(), 3,
                COOLDOWN_MINUTES * SECONDS_PER_MINUTE, null));
        return segments;
    }

    /** A time segment to be laid out: its kind, label, seconds and rounds. */
    public record SegmentSpec(SegmentKind kind, String label, int plannedSeconds, int rounds) {
    }

    /** The HIIT default: 5 minutes warm-up, 8 rounds of 30 s work and 60 s easy, 5 minutes cool-down. */
    public static List<SegmentSpec> defaultIntervals() {
        return List.of(
                new SegmentSpec(SegmentKind.WARMUP, SegmentKind.WARMUP.label(), WARMUP_MINUTES * SECONDS_PER_MINUTE, 1),
                new SegmentSpec(SegmentKind.WORK, SegmentKind.WORK.label(), HIIT_WORK_SECONDS, HIIT_ROUNDS),
                new SegmentSpec(SegmentKind.EASY, SegmentKind.EASY.label(), HIIT_EASY_SECONDS, HIIT_ROUNDS),
                new SegmentSpec(SegmentKind.COOLDOWN, SegmentKind.COOLDOWN.label(),
                        COOLDOWN_MINUTES * SECONDS_PER_MINUTE, 1));
    }

    /**
     * Lays segments out in time, one row per round. Neighbouring segments with the same number of rounds
     * (more than one) repeat together, so Work and Easy of 8 rounds become Work 1, Easy 1 ... Work 8,
     * Easy 8. A segment of one round stays as it is.
     */
    public static List<SegmentSpec> expand(List<SegmentSpec> segments) {
        List<SegmentSpec> expanded = new ArrayList<>();
        int index = 0;
        while (index < segments.size()) {
            int rounds = roundsOf(segments.get(index).rounds());
            if (rounds == 1) {
                SegmentSpec one = segments.get(index);
                expanded.add(new SegmentSpec(one.kind(), labelOf(one.kind(), one.label()), one.plannedSeconds(), 1));
                index++;
                continue;
            }
            int end = index;
            while (end < segments.size() && roundsOf(segments.get(end).rounds()) == rounds) {
                end++;
            }
            for (int round = 1; round <= rounds; round++) {
                for (int member = index; member < end; member++) {
                    SegmentSpec segment = segments.get(member);
                    expanded.add(new SegmentSpec(segment.kind(), labelOf(segment.kind(), segment.label()) + " " + round,
                            segment.plannedSeconds(), 1));
                }
            }
            index = end;
        }
        return expanded;
    }

    /** The label a segment is shown with: its own, else its kind's. */
    public static String labelOf(SegmentKind kind, String label) {
        if (label != null && !label.isBlank()) {
            return label.strip();
        }
        return kind == null ? SegmentKind.STEADY.label() : kind.label();
    }
}
