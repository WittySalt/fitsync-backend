package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanCoverageView.GroupSets;
import com.fitsync.workout.dto.PlanCoverageView.Note;

/**
 * What a plan's week trains, from its strength days' planned sets, by the same hard-set rule as the
 * Progress muscle figure ({@link MuscleCredit}). A pure function of its inputs.
 *
 * <p>The catalogue keeps seven muscle groups, so calves and the lats are read from the move's name:
 * a move named for the calves trains them, and a pull-up, chin-up or pulldown is a pull from above.
 */
public final class PlanCoverageCalculator {

    public static final String CALVES_NOT_TRAINED = "CALVES_NOT_TRAINED";
    public static final String NO_VERTICAL_PULL = "NO_VERTICAL_PULL";
    public static final String SHOULDERS_HELPER_ONLY = "SHOULDERS_HELPER_ONLY";
    public static final String STRENGTH_BACK_TO_BACK = "STRENGTH_BACK_TO_BACK";
    public static final String OVER_PROFILE_MINUTES = "OVER_PROFILE_MINUTES";

    static final String CALVES_TEXT = "Calves are not trained yet.";
    static final String VERTICAL_PULL_TEXT = "No pull from above yet (lats).";
    static final String SHOULDERS_TEXT = "Shoulders only as a helper.";
    static final String BACK_TO_BACK_TEXT = "Two strength days back to back.";
    static final String OVER_PROFILE_TEXT = "That is more than your profile says you have.";

    /** Plan minutes this far over the profile's minutes a week, or more, earn the note. */
    static final BigDecimal OVER_PROFILE_RATIO = new BigDecimal("1.5");
    private static final int DAYS_PER_WEEK = 7;
    private static final List<String> CALF_WORDS = List.of("calf", "calves");
    private static final List<String> VERTICAL_PULL_WORDS = List.of("pull-up", "pullup", "pull up", "chin-up",
            "chinup", "chin up", "pulldown", "pull-down", "pull down", "lat pull");

    private PlanCoverageCalculator() {
    }

    /** One planned move: its name, groups and planned sets. */
    public record Move(String name, MuscleGroup primary, Set<MuscleGroup> secondary, int sets) {

        public Move {
            secondary = secondary == null ? Set.of() : Set.copyOf(secondary);
        }

        @Override
        public Set<MuscleGroup> secondary() {
            return Set.copyOf(secondary);
        }
    }

    /**
     * One day of the plan. A flat plan, which has no week, is one strength day without a weekday
     * (dayOfWeek null).
     */
    public record Day(Integer dayOfWeek, SessionKind kind, Integer minutes, List<Move> moves) {

        public Day {
            moves = moves == null ? List.of() : List.copyOf(moves);
        }

        @Override
        public List<Move> moves() {
            return List.copyOf(moves);
        }
    }

    /**
     * The coverage of a week.
     *
     * @param days                  the plan's days
     * @param profileMinutesPerWeek the profile's training days times its minutes, or null when unknown
     */
    public static PlanCoverageView calculate(List<Day> days, Integer profileMinutesPerWeek) {
        Map<MuscleGroup, BigDecimal> totals = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, BigDecimal> primaryTotals = new EnumMap<>(MuscleGroup.class);
        boolean anyMove = false;
        boolean calves = false;
        boolean verticalPull = false;
        for (Day day : days) {
            if (day.kind() != SessionKind.STRENGTH) {
                continue;
            }
            for (Move move : day.moves()) {
                if (move.sets() <= 0) {
                    continue;
                }
                anyMove = true;
                String name = move.name() == null ? "" : move.name().toLowerCase(Locale.ROOT);
                calves = calves || CALF_WORDS.stream().anyMatch(name::contains);
                verticalPull = verticalPull || VERTICAL_PULL_WORDS.stream().anyMatch(name::contains);
                BigDecimal sets = BigDecimal.valueOf(move.sets());
                MuscleCredit.credit(move.primary(), move.secondary(), sets,
                        (group, amount) -> totals.merge(group, amount, BigDecimal::add));
                if (MuscleCredit.GROUPS.contains(move.primary())) {
                    primaryTotals.merge(move.primary(), sets, BigDecimal::add);
                }
            }
        }

        List<GroupSets> groups = new ArrayList<>();
        for (MuscleGroup group : MuscleCredit.GROUPS) {
            groups.add(new GroupSets(group,
                    totals.getOrDefault(group, BigDecimal.ZERO).setScale(1, RoundingMode.HALF_UP)));
        }

        List<Note> notes = new ArrayList<>();
        if (anyMove && !calves) {
            notes.add(new Note(CALVES_NOT_TRAINED, CALVES_TEXT));
        }
        if (anyMove && !verticalPull) {
            notes.add(new Note(NO_VERTICAL_PULL, VERTICAL_PULL_TEXT));
        }
        if (primaryTotals.getOrDefault(MuscleGroup.SHOULDERS, BigDecimal.ZERO).signum() == 0
                && totals.getOrDefault(MuscleGroup.SHOULDERS, BigDecimal.ZERO).signum() > 0) {
            notes.add(new Note(SHOULDERS_HELPER_ONLY, SHOULDERS_TEXT));
        }
        if (strengthBackToBack(days)) {
            notes.add(new Note(STRENGTH_BACK_TO_BACK, BACK_TO_BACK_TEXT));
        }
        if (overProfile(days, profileMinutesPerWeek)) {
            notes.add(new Note(OVER_PROFILE_MINUTES, OVER_PROFILE_TEXT));
        }
        return new PlanCoverageView(groups, notes);
    }

    /** True when two strength days fall on neighbouring weekdays, Sunday to Monday included. */
    static boolean strengthBackToBack(List<Day> days) {
        Set<Integer> strengthDays = new TreeSet<>();
        for (Day day : days) {
            if (day.kind() == SessionKind.STRENGTH && day.dayOfWeek() != null) {
                strengthDays.add(day.dayOfWeek());
            }
        }
        for (int dayOfWeek : strengthDays) {
            int next = dayOfWeek % DAYS_PER_WEEK + 1;
            if (strengthDays.contains(next)) {
                return true;
            }
        }
        return false;
    }

    /** True when the week's minutes are at least one and a half times what the profile says it has. */
    static boolean overProfile(List<Day> days, Integer profileMinutesPerWeek) {
        if (profileMinutesPerWeek == null || profileMinutesPerWeek <= 0) {
            return false;
        }
        int minutes = 0;
        for (Day day : days) {
            if (day.minutes() != null && day.minutes() > 0) {
                minutes += day.minutes();
            }
        }
        return BigDecimal.valueOf(minutes).compareTo(
                BigDecimal.valueOf(profileMinutesPerWeek).multiply(OVER_PROFILE_RATIO)) >= 0;
    }
}
