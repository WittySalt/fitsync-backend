package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.fitsync.workout.domain.ExerciseEquipment;
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
    static final int SETS = 3;
    static final int BEGINNER_REPS = 10;
    static final int BARBELL_COMPOUND_REPS = 8;
    static final int ISOLATION_REPS = 12;
    static final int COMPOUND_REST_SECONDS = 90;
    static final int OTHER_REST_SECONDS = 60;
    static final int HOLD_SECONDS = 45;

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

    /** A move the catalogue offered: its id, name and equipment. */
    public record Move(UUID exerciseId, String name, ExerciseEquipment equipment) {
    }

    /** One catalogue exercise the curated lists are matched against. */
    public record Candidate(UUID exerciseId, String name, ExerciseEquipment equipment) {
    }

    /** Picks moves from the catalogue. */
    public interface MovePicker {

        /**
         * The move for a slot that is not used yet: the curated choice for the person's level when the
         * catalogue has one with their equipment, else the best by the slot's name hints.
         */
        Optional<Move> pick(Slot slot, boolean beginner, Set<UUID> used);

        /** Up to {@code count} moves for a mobility flow, from the curated flow first. */
        List<Move> mobility(int count, boolean beginner);
    }

    /**
     * One slot of a strength day: its group, the curated moves in order of preference, the name hints the
     * older ranking falls back on, and whether the move is a compound one.
     */
    public record Slot(MuscleGroup group, List<String> curated, List<String> hints, boolean compound) {

        public Slot {
            curated = List.copyOf(curated);
            hints = List.copyOf(hints);
        }

        @Override
        public List<String> curated() {
            return List.copyOf(curated);
        }

        @Override
        public List<String> hints() {
            return List.copyOf(hints);
        }
    }

    // The curated moves of each slot, the owner's list in order; where the free-exercise-db name differs,
    // the catalogue's own name for the same move follows the owner's name directly ("Pullups", "Plank").
    static final Slot PUSH = new Slot(MuscleGroup.CHEST, List.of("Barbell Bench Press",
            "Barbell Bench Press - Medium Grip", "Dumbbell Bench Press", "Push-Up", "Pushups",
            "Incline Dumbbell Press", "Machine Chest Press", "Leverage Chest Press"),
            List.of("bench press", "push-up", "pushup"), true);
    static final Slot VERTICAL_PULL = new Slot(MuscleGroup.BACK, List.of("Pull-Up", "Lat Pulldown",
            "Wide-Grip Lat Pulldown", "Band Assisted Pull-Up"),
            List.of("pulldown", "pull-up", "pullup", "chin"), true);
    static final Slot HORIZONTAL_PULL = new Slot(MuscleGroup.BACK, List.of("Barbell Row", "Bent Over Barbell Row",
            "Bent-Over Row", "Seated Cable Row", "Seated Cable Rows", "Single-Arm Row", "One-Arm Dumbbell Row",
            "Bent Over Two-Dumbbell Row"), List.of("bent over row", "row"), true);
    static final Slot SQUAT = new Slot(MuscleGroup.LEGS, List.of("Back Squat", "Barbell Squat", "Goblet Squat",
            "Leg Press", "Bodyweight Squat"), List.of("squat"), true);
    static final Slot HINGE = new Slot(MuscleGroup.GLUTES, List.of("Romanian Deadlift", "Deadlift",
            "Barbell Deadlift", "Hip Thrust", "Stiff-Legged Dumbbell Deadlift"),
            List.of("deadlift", "hip thrust", "glute bridge"), true);
    static final Slot LUNGE = new Slot(MuscleGroup.LEGS, List.of("Lunge", "Barbell Lunge", "Dumbbell Lunges",
            "Bulgarian Split Squat", "Split Squat with Dumbbells", "Bodyweight Walking Lunge"),
            List.of("lunge", "split squat"), true);
    static final Slot CORE = new Slot(MuscleGroup.CORE, List.of("Plank Hold", "Plank", "Dead Bug",
            "Hanging Knee Raise", "Hanging Leg Raise", "Crunches"), List.of("plank", "crunch"), false);
    static final Slot SHOULDER_PRESS = new Slot(MuscleGroup.SHOULDERS, List.of("Overhead Press",
            "Standing Military Press", "Dumbbell Shoulder Press", "Seated Dumbbell Press", "Standing Dumbbell Press",
            "Barbell Shoulder Press", "Leverage Shoulder Press", "Shoulder Press - With Bands"),
            List.of("shoulder press", "military press"), true);
    static final Slot CALF = new Slot(MuscleGroup.LEGS, List.of("Standing Calf Raises", "Standing Barbell Calf Raise",
            "Standing Dumbbell Calf Raise", "Calf Raises - With Bands", "Calf Raise"),
            List.of("calf raise"), false);
    static final Slot CURL = new Slot(MuscleGroup.ARMS, List.of("Barbell Curl", "Dumbbell Bicep Curl",
            "Hammer Curls"), List.of("curl"), false);
    static final Slot TRICEPS = new Slot(MuscleGroup.ARMS, List.of("Triceps Pushdown", "Bench Dips",
            "Dips - Triceps Version"), List.of("triceps", "extension"), false);

    /** The curated mobility flow, the owner's list first, then the catalogue's names for the same stretches. */
    static final List<String> MOBILITY_FLOW = List.of("Cat-Cow", "90/90 Hip Switch", "World's Greatest Stretch",
            "Thoracic Rotation", "Hamstring Floss", "Child's Pose", "Hip Flexor Stretch", "Downward Dog",
            "Cat Stretch", "90/90 Hamstring", "Hamstring Stretch", "Kneeling Hip Flexor");

    // Five slots a day; the slots after the fifth are used only when the day is long enough for more moves.
    // Upright rows and lateral raises fill no slot.
    private static final List<Slot> FULL_BODY_A = List.of(SQUAT, PUSH, HORIZONTAL_PULL, SHOULDER_PRESS, CORE,
            CALF, VERTICAL_PULL);
    private static final List<Slot> FULL_BODY_B = List.of(LUNGE, PUSH, VERTICAL_PULL, CALF, CORE,
            SHOULDER_PRESS, CURL);
    private static final List<Slot> FULL_BODY_C = List.of(HINGE, PUSH, HORIZONTAL_PULL, SHOULDER_PRESS, CORE,
            CALF, TRICEPS);
    private static final List<Slot> UPPER = List.of(PUSH, VERTICAL_PULL, HORIZONTAL_PULL, SHOULDER_PRESS, CURL,
            TRICEPS, PUSH);
    private static final List<Slot> LOWER = List.of(SQUAT, HINGE, LUNGE, CALF, CORE, SQUAT, CORE);
    private static final List<Slot> FULL_BODY = List.of(SQUAT, PUSH, HORIZONTAL_PULL, SHOULDER_PRESS, CORE,
            CALF, VERTICAL_PULL);

    private static final Set<ExerciseEquipment> BEGINNER_EQUIPMENT = Set.of(ExerciseEquipment.BODYWEIGHT,
            ExerciseEquipment.NONE, ExerciseEquipment.DUMBBELL);
    private static final Set<ExerciseEquipment> LOADED_EQUIPMENT = Set.of(ExerciseEquipment.BARBELL,
            ExerciseEquipment.MACHINE, ExerciseEquipment.CABLE);

    /** Words that make a move a variant; a name carrying one ranks after the plain moves. */
    static final List<String> QUALIFIERS = List.of("decline", "incline", "narrow", "wide", "close", "one arm",
            "single arm", "sprint", "seated", "cable", "upright", "reverse", "alternating", "smith", "kneeling",
            "standing");

    private static final int EXACT = 0;
    private static final int PLAIN = 1;
    private static final int QUALIFIED = 2;
    private static final int NO_MATCH = 3;

    /**
     * The curated choice among the catalogue's candidates, or null when no curated name matches.
     *
     * <p>A catalogue name matches a curated one when it is the same, ignoring case, spaces, punctuation
     * and a plural s, or when it holds the curated name's words in order with at most one word more. The
     * same name anywhere in the list always wins; then a plain match; a match that carries a qualifier
     * word the curated name does not ("decline", "seated", "one arm" and the others in {@link #QUALIFIERS})
     * comes last. Within one of these ranks the curated order decides, then the shorter name.
     *
     * <p>A beginner takes the first match with bodyweight or dumbbells, a person with some experience the
     * first with a barbell, a machine or a cable; without one of those, the first match of any equipment.
     */
    public static Candidate chooseCurated(List<Candidate> candidates, List<String> curated, boolean beginner,
                                          Set<UUID> used) {
        Set<ExerciseEquipment> preferred = beginner ? BEGINNER_EQUIPMENT : LOADED_EQUIPMENT;
        for (int rank : List.of(EXACT, PLAIN, QUALIFIED)) {
            Candidate fallback = null;
            for (String wanted : curated) {
                List<Candidate> matches = new ArrayList<>();
                for (Candidate candidate : candidates) {
                    if (!used.contains(candidate.exerciseId()) && matchRank(candidate.name(), wanted) == rank) {
                        matches.add(candidate);
                    }
                }
                matches.sort(Comparator.comparingInt((Candidate candidate) -> candidate.name().length())
                        .thenComparing(Candidate::name));
                for (Candidate match : matches) {
                    if (preferred.contains(match.equipment())) {
                        return match;
                    }
                    if (fallback == null) {
                        fallback = match;
                    }
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    /** How a catalogue name matches a curated one: the same, a plain match, a qualified match, or none. */
    static int matchRank(String catalogueName, String curatedName) {
        String plainCatalogue = plain(catalogueName);
        String plainCurated = plain(curatedName);
        if (plainCatalogue.equals(plainCurated) || plainCatalogue.equals(plainCurated + "s")
                || plainCatalogue.equals(plainCurated + "es")) {
            return EXACT;
        }
        List<String> have = words(catalogueName);
        List<String> want = words(curatedName);
        if (have.size() > want.size() + 1 || !holdsInOrder(have, want)) {
            return NO_MATCH;
        }
        String spaced = " " + String.join(" ", have) + " ";
        String wantSpaced = " " + String.join(" ", want) + " ";
        for (String qualifier : QUALIFIERS) {
            String word = " " + qualifier + " ";
            if (spaced.contains(word) && !wantSpaced.contains(word)) {
                return QUALIFIED;
            }
        }
        return PLAIN;
    }

    private static boolean holdsInOrder(List<String> have, List<String> want) {
        int next = 0;
        for (String word : have) {
            if (next < want.size() && sameWord(word, want.get(next))) {
                next++;
            }
        }
        return next == want.size();
    }

    private static boolean sameWord(String have, String want) {
        return have.equals(want) || have.equals(want + "s") || have.equals(want + "es");
    }

    /** A name's words in lower case, split at anything that is not a letter or a digit. */
    static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int index = 0; index <= name.length(); index++) {
            char ch = index < name.length() ? name.charAt(index) : ' ';
            String lower = plain(String.valueOf(ch));
            if (!lower.isEmpty() && ch != '\'') {
                word.append(lower);
            } else if (ch != '\'' && word.length() > 0) {
                words.add(word.toString());
                word.setLength(0);
            }
        }
        return words;
    }

    /** A name in lower-case letters and digits only, without any case mapping of the platform. */
    static String plain(String name) {
        StringBuilder plain = new StringBuilder(name.length());
        for (int index = 0; index < name.length(); index++) {
            char ch = name.charAt(index);
            if (ch >= 'A' && ch <= 'Z') {
                plain.append((char) (ch + ('a' - 'A')));
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                plain.append(ch);
            }
        }
        return plain.toString();
    }

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
                        strengthEntries(templates.get(index), minutes, profile, picker, used)));
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
            List<PlanEntryInput> flow = mobilityEntries(picker, profile.beginner());
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
            return List.of(FULL_BODY);
        }
        return List.of(UPPER, LOWER, FULL_BODY).subList(0, days);
    }

    private static List<PlanEntryInput> strengthEntries(List<Slot> template, int minutes, Profile profile,
                                                        MovePicker picker, Set<UUID> used) {
        int moves = Math.max(MIN_MOVES, Math.min(MAX_MOVES, minutes / MINUTES_PER_MOVE));
        List<PlanEntryInput> entries = new ArrayList<>();
        for (Slot slot : template) {
            if (entries.size() >= moves) {
                break;
            }
            Optional<Move> move = picker.pick(slot, profile.beginner(), used);
            if (move.isPresent()) {
                used.add(move.get().exerciseId());
                entries.add(strengthEntry(slot, move.get(), profile.beginner(), entries.size() + 1));
            }
        }
        return entries;
    }

    /**
     * The targets of one proposed move. A beginner does 3 x 10. With some experience a barbell compound
     * is 3 x 8, an isolation move 3 x 12 and any other compound 3 x 10. Rest is 90 s after a compound
     * and 60 s otherwise. A plank is held for 45 s. No weight and no tempo are proposed: the person
     * sets their own.
     */
    static PlanEntryInput strengthEntry(Slot slot, Move move, boolean beginner, int position) {
        int rest = slot.compound() ? COMPOUND_REST_SECONDS : OTHER_REST_SECONDS;
        if (plain(move.name()).contains("plank")) {
            return new PlanEntryInput(EntryType.SET, move.exerciseId(), null, null, position, SETS, null, null,
                    HOLD_SECONDS, OTHER_REST_SECONDS, null, null, null, null);
        }
        int reps;
        if (beginner) {
            reps = BEGINNER_REPS;
        } else if (!slot.compound()) {
            reps = ISOLATION_REPS;
        } else {
            reps = move.equipment() == ExerciseEquipment.BARBELL ? BARBELL_COMPOUND_REPS : BEGINNER_REPS;
        }
        return PlanEntryInput.set(move.exerciseId(), position, SETS, reps, null, rest);
    }

    private static List<PlanEntryInput> mobilityEntries(MovePicker picker, boolean beginner) {
        List<PlanEntryInput> entries = new ArrayList<>();
        for (Move move : picker.mobility(MOBILITY_MOVES, beginner)) {
            entries.add(new PlanEntryInput(EntryType.SET, move.exerciseId(), null, null, entries.size() + 1, 2, null,
                    null, MOBILITY_HOLD_SECONDS, 15, null, null, null, null));
        }
        return entries;
    }
}
