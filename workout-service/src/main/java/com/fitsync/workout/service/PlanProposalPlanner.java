package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.fitsync.workout.domain.DayTargetKind;
import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;

/**
 * Lays out a default week from a profile (spec 2.2), a pure function of its inputs and the move picker.
 * Habit days come first: a sport on the weekday it has been played on three weeks or more. Then up to
 * three strength days on Monday, Thursday and Saturday by preference, never on neighbouring days,
 * titled Full body A/B/C for a beginner or Upper / Lower / Full body above. Then one easy 30-minute run
 * on the first free day after a strength day when the focus includes cardio. Mobility fills any count
 * that is left, from Sunday on. Every other day is rest.
 */
public final class PlanProposalPlanner {

    static final int MIN_DAYS = 2;
    static final int MAX_DAYS = 6;
    static final int MAX_STRENGTH_DAYS = 3;
    static final int DEFAULT_DAYS = 3;
    static final int DEFAULT_MINUTES = 45;
    static final int EASY_RUN_MINUTES = 30;
    static final int EASY_EFFORT = 3;
    static final int MOBILITY_MOVES = 6;
    static final int MOBILITY_HOLD_SECONDS = 45;
    static final int MINUTES_PER_MOVE = 8;
    static final int MIN_MOVES = 4;
    static final int MAX_MOVES = 7;

    private static final List<Integer> STRENGTH_PREFERENCE = List.of(1, 4, 6, 3, 5, 2, 7);
    private static final int SUNDAY = 7;
    private static final Set<String> STRENGTH_FOCUS = Set.of("FULL_BODY", "UPPER", "LOWER", "CORE", "STRENGTH");

    private PlanProposalPlanner() {
    }

    /** The profile figures the week is built from. */
    public record Profile(WorkoutDifficulty level, Integer weeklyWorkoutDays, Integer preferredDurationMinutes,
                          Set<String> focusAreas) {

        public Profile {
            focusAreas = focusAreas == null ? Set.of() : Set.copyOf(focusAreas);
        }

        @Override
        public Set<String> focusAreas() {
            return Set.copyOf(focusAreas);
        }

        int days() {
            int days = weeklyWorkoutDays == null ? DEFAULT_DAYS : weeklyWorkoutDays;
            return Math.max(MIN_DAYS, Math.min(MAX_DAYS, days));
        }

        int minutes() {
            return preferredDurationMinutes == null ? DEFAULT_MINUTES : preferredDurationMinutes;
        }

        boolean beginner() {
            return level == null || level == WorkoutDifficulty.BEGINNER;
        }

        boolean wantsStrength() {
            return focusAreas.isEmpty() || focusAreas.stream().anyMatch(STRENGTH_FOCUS::contains);
        }

        boolean wantsCardio() {
            return focusAreas.contains("CARDIO");
        }
    }

    /** A move the catalogue offered: its id and name. */
    public record Move(UUID exerciseId, String name) {
    }

    /** Picks moves from the catalogue. */
    public interface MovePicker {

        /**
         * The best move of a group not used yet: one whose name matches the earliest hint, else any. The
         * variant picks the second or third best, so Full body A, B and C differ.
         */
        Optional<Move> pick(MuscleGroup group, List<String> hints, int variant, Set<UUID> used);

        /** Up to {@code count} stretching moves for a mobility flow. */
        List<Move> mobility(int count);
    }

    /** One slot of a strength day: the group and the names to look for, best first. */
    record Slot(MuscleGroup group, List<String> hints) {
    }

    private static final List<Slot> FULL_BODY_A = List.of(
            new Slot(MuscleGroup.LEGS, List.of("goblet squat", "squat")),
            new Slot(MuscleGroup.CHEST, List.of("bench press", "push-up", "pushup")),
            new Slot(MuscleGroup.BACK, List.of("bent over row", "row")),
            new Slot(MuscleGroup.SHOULDERS, List.of("shoulder press", "overhead press")),
            new Slot(MuscleGroup.CORE, List.of("plank", "crunch")),
            new Slot(MuscleGroup.LEGS, List.of("calf raise")),
            new Slot(MuscleGroup.GLUTES, List.of("glute bridge", "hip thrust")));
    private static final List<Slot> FULL_BODY_B = List.of(
            new Slot(MuscleGroup.LEGS, List.of("lunge", "split squat")),
            new Slot(MuscleGroup.BACK, List.of("pulldown", "pull-up", "pullup", "chin-up", "chin up")),
            new Slot(MuscleGroup.CHEST, List.of("incline", "press")),
            new Slot(MuscleGroup.LEGS, List.of("calf raise")),
            new Slot(MuscleGroup.SHOULDERS, List.of("lateral raise", "raise")),
            new Slot(MuscleGroup.ARMS, List.of("curl")),
            new Slot(MuscleGroup.CORE, List.of("plank", "crunch")));
    private static final List<Slot> FULL_BODY_C = List.of(
            new Slot(MuscleGroup.GLUTES, List.of("romanian deadlift", "deadlift", "hip thrust")),
            new Slot(MuscleGroup.CHEST, List.of("push-up", "pushup", "fly")),
            new Slot(MuscleGroup.BACK, List.of("one arm", "row")),
            new Slot(MuscleGroup.SHOULDERS, List.of("arnold", "press")),
            new Slot(MuscleGroup.LEGS, List.of("calf raise")),
            new Slot(MuscleGroup.ARMS, List.of("triceps", "extension")),
            new Slot(MuscleGroup.CORE, List.of("crunch", "plank")));
    private static final List<Slot> UPPER = List.of(
            new Slot(MuscleGroup.CHEST, List.of("bench press", "press")),
            new Slot(MuscleGroup.BACK, List.of("pulldown", "pull-up", "pullup", "chin-up", "chin up")),
            new Slot(MuscleGroup.SHOULDERS, List.of("shoulder press", "overhead press")),
            new Slot(MuscleGroup.BACK, List.of("bent over row", "row")),
            new Slot(MuscleGroup.ARMS, List.of("curl")),
            new Slot(MuscleGroup.ARMS, List.of("triceps", "extension")),
            new Slot(MuscleGroup.CHEST, List.of("fly")));
    private static final List<Slot> LOWER = List.of(
            new Slot(MuscleGroup.LEGS, List.of("squat")),
            new Slot(MuscleGroup.GLUTES, List.of("romanian deadlift", "deadlift", "hip thrust")),
            new Slot(MuscleGroup.LEGS, List.of("lunge", "split squat")),
            new Slot(MuscleGroup.LEGS, List.of("calf raise")),
            new Slot(MuscleGroup.CORE, List.of("plank", "crunch")),
            new Slot(MuscleGroup.LEGS, List.of("leg curl", "curl")),
            new Slot(MuscleGroup.GLUTES, List.of("glute bridge", "bridge")));

    /** The proposed week, as plan day inputs ordered by weekday. */
    public static List<WorkoutPlanDayInput> propose(Profile profile, List<HabitDays.Habit> habits, MovePicker picker) {
        int count = profile.days();
        int minutes = profile.minutes();
        Map<Integer, WorkoutPlanDayInput> week = new TreeMap<>();

        for (HabitDays.Habit habit : habits) {
            int weekday = habit.weekday().getValue();
            if (week.size() >= count || week.containsKey(weekday)) {
                continue;
            }
            int habitMinutes = habit.minutes() > 0 ? habit.minutes() : minutes;
            week.put(weekday, new WorkoutPlanDayInput(null, weekday, SessionKind.SPORT, habit.sport().label(),
                    habit.sport(), habitMinutes, List.of(), null, null, null, null,
                    PlanEntryRules.defaultSegments(habitMinutes, habit.sport())));
        }

        List<Integer> strengthDays = new ArrayList<>();
        if (profile.wantsStrength()) {
            int wanted = Math.min(count - week.size(), MAX_STRENGTH_DAYS);
            for (int weekday : STRENGTH_PREFERENCE) {
                if (strengthDays.size() >= wanted) {
                    break;
                }
                if (!week.containsKey(weekday) && !neighbours(weekday, strengthDays)) {
                    strengthDays.add(weekday);
                }
            }
            strengthDays.sort(Integer::compareTo);
            List<String> titles = strengthTitles(profile.beginner(), strengthDays.size());
            List<List<Slot>> templates = strengthTemplates(profile.beginner(), strengthDays.size());
            Set<UUID> used = new HashSet<>();
            for (int index = 0; index < strengthDays.size(); index++) {
                int weekday = strengthDays.get(index);
                week.put(weekday, new WorkoutPlanDayInput(null, weekday, SessionKind.STRENGTH, titles.get(index), null,
                        minutes, List.of(), null, null, null, null,
                        strengthEntries(templates.get(index), index, minutes, profile, picker, used)));
            }
        }

        if (profile.wantsCardio() && week.size() < count) {
            Integer runDay = firstFreeAfter(strengthDays, week);
            if (runDay != null) {
                week.put(runDay, new WorkoutPlanDayInput(null, runDay, SessionKind.CARDIO, "Easy run", null,
                        EASY_RUN_MINUTES, List.of(), DayTargetKind.MINUTES, BigDecimal.valueOf(EASY_RUN_MINUTES), null,
                        EASY_EFFORT, PlanEntryRules.defaultSegments(EASY_RUN_MINUTES, null)));
            }
        }

        if (week.size() < count) {
            List<PlanEntryInput> flow = mobilityEntries(picker);
            for (int offset = 0; offset < 7 && week.size() < count; offset++) {
                int weekday = (SUNDAY - 1 + offset) % 7 + 1;
                if (!week.containsKey(weekday)) {
                    week.put(weekday, new WorkoutPlanDayInput(null, weekday, SessionKind.MOBILITY, "Mobility flow", null,
                            minutes, List.of(), null, null, null, null, flow));
                }
            }
        }
        return new ArrayList<>(week.values());
    }

    /** The plan's name from its week: "Full body, 3 days", "Strength and cardio, 4 days", "Running and badminton, 3 days". */
    public static String nameOf(List<WorkoutPlanDayInput> days) {
        List<String> parts = new ArrayList<>();
        boolean strength = days.stream().anyMatch(day -> day.kind() == SessionKind.STRENGTH);
        boolean cardio = days.stream().anyMatch(day -> day.kind() == SessionKind.CARDIO);
        if (strength) {
            parts.add(cardio || days.stream().anyMatch(day -> day.kind() != SessionKind.STRENGTH) ? "Strength" : "Full body");
        }
        if (cardio) {
            parts.add(strength ? "cardio" : "Running");
        }
        days.stream()
                .filter(day -> day.kind() == SessionKind.SPORT && day.sportActivity() != null)
                .map(day -> day.sportActivity().label())
                .distinct()
                .forEach(label -> parts.add(parts.isEmpty() ? label : label.toLowerCase(Locale.ROOT)));
        if (parts.isEmpty()) {
            parts.add("Mobility");
        }
        String joined = parts.size() == 1 ? parts.get(0)
                : String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
        return joined + ", " + days.size() + " days";
    }

    private static boolean neighbours(int weekday, List<Integer> chosen) {
        for (int other : chosen) {
            int distance = Math.abs(weekday - other);
            if (distance == 1 || distance == 6) {
                return true;
            }
        }
        return false;
    }

    private static Integer firstFreeAfter(List<Integer> strengthDays, Map<Integer, WorkoutPlanDayInput> week) {
        for (int weekday : strengthDays) {
            int next = weekday % 7 + 1;
            if (!week.containsKey(next)) {
                return next;
            }
        }
        for (int weekday = 1; weekday <= 7; weekday++) {
            if (!week.containsKey(weekday)) {
                return weekday;
            }
        }
        return null;
    }

    static List<String> strengthTitles(boolean beginner, int days) {
        if (beginner) {
            return List.of("Full body A", "Full body B", "Full body C").subList(0, days);
        }
        if (days == 1) {
            return List.of("Full body");
        }
        return List.of("Upper", "Lower", "Full body").subList(0, days);
    }

    private static List<List<Slot>> strengthTemplates(boolean beginner, int days) {
        if (beginner) {
            return List.of(FULL_BODY_A, FULL_BODY_B, FULL_BODY_C).subList(0, days);
        }
        if (days == 1) {
            return List.of(FULL_BODY_A);
        }
        return List.of(UPPER, LOWER, FULL_BODY_A).subList(0, days);
    }

    private static List<PlanEntryInput> strengthEntries(List<Slot> template, int variant, int minutes, Profile profile,
                                                        MovePicker picker, Set<UUID> used) {
        int moves = Math.max(MIN_MOVES, Math.min(MAX_MOVES, minutes / MINUTES_PER_MOVE));
        int sets = profile.level() == WorkoutDifficulty.ADVANCED ? 4 : 3;
        int reps = profile.level() == WorkoutDifficulty.ADVANCED ? 8 : 10;
        int rest = profile.beginner() ? 75 : (profile.level() == WorkoutDifficulty.ADVANCED ? 120 : 90);
        List<PlanEntryInput> entries = new ArrayList<>();
        for (Slot slot : template) {
            if (entries.size() >= moves) {
                break;
            }
            Optional<Move> move = picker.pick(slot.group(), slot.hints(), variant, used);
            if (move.isPresent()) {
                used.add(move.get().exerciseId());
                entries.add(PlanEntryInput.set(move.get().exerciseId(), entries.size() + 1, sets, reps, null, rest));
            }
        }
        return entries;
    }

    private static List<PlanEntryInput> mobilityEntries(MovePicker picker) {
        List<PlanEntryInput> entries = new ArrayList<>();
        for (Move move : picker.mobility(MOBILITY_MOVES)) {
            entries.add(new PlanEntryInput(EntryType.SET, move.exerciseId(), null, null, entries.size() + 1, 2, null,
                    null, 15, null, MOBILITY_HOLD_SECONDS, null, null));
        }
        return entries;
    }
}
