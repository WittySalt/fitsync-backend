package com.fitsync.analytics.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The seventeen badges, their themes and paths, in display order (me-achievements-spec.md, section 2), and
 * the three retired badges that are kept only for whoever earned them.
 */
final class BadgeCatalogue {

    static final String SHOWING_UP = "SHOWING_UP";
    static final String GETTING_STRONGER = "GETTING_STRONGER";
    static final String EATING_WELL = "EATING_WELL";
    static final String IN_THE_KITCHEN = "IN_THE_KITCHEN";

    static final String FIRST_WORKOUT = "FIRST_WORKOUT";
    static final String WORKOUTS_10 = "WORKOUTS_10";
    static final String WORKOUTS_50 = "WORKOUTS_50";
    static final String WORKOUTS_100 = "WORKOUTS_100";
    static final String FULL_WEEK = "FULL_WEEK";
    static final String FOUR_FULL_WEEKS = "FOUR_FULL_WEEKS";
    static final String FIRST_RECORD = "FIRST_RECORD";
    static final String FIVE_RECORDS = "FIVE_RECORDS";
    static final String BODYWEIGHT_BENCH = "BODYWEIGHT_BENCH";
    static final String FIRST_MEAL = "FIRST_MEAL";
    static final String SEVEN_DAYS_LOGGED = "SEVEN_DAYS_LOGGED";
    static final String THIRTY_DAYS_LOGGED = "THIRTY_DAYS_LOGGED";
    static final String PROTEIN_WEEK = "PROTEIN_WEEK";
    static final String PROTEIN_MONTH = "PROTEIN_MONTH";
    static final String FIRST_COOK = "FIRST_COOK";
    static final String TEN_COOKS = "TEN_COOKS";
    static final String FIFTY_COOKS = "FIFTY_COOKS";

    static final String STREAK_7 = "STREAK_7";
    static final String STREAK_30 = "STREAK_30";
    static final String FIRST_WEIGHT_LOGGED = "FIRST_WEIGHT_LOGGED";

    record ThemeDef(String code, String name, List<PathDef> paths) {
        ThemeDef {
            paths = List.copyOf(paths);
        }

        @Override
        public List<PathDef> paths() {
            return List.copyOf(paths);
        }
    }

    record PathDef(String code, List<BadgeDef> badges) {
        PathDef {
            badges = List.copyOf(badges);
        }

        @Override
        public List<BadgeDef> badges() {
            return List.copyOf(badges);
        }
    }

    record BadgeDef(String code, String theme, String name, String how) {
    }

    static final List<ThemeDef> THEMES = List.of(
            new ThemeDef(SHOWING_UP, "Showing up", List.of(
                    new PathDef("SESSIONS", List.of(
                            badge(FIRST_WORKOUT, SHOWING_UP, "First workout", "Finish one session."),
                            badge(WORKOUTS_10, SHOWING_UP, "10 workouts", "Finish 10 sessions."),
                            badge(WORKOUTS_50, SHOWING_UP, "50 workouts", "Finish 50 sessions."),
                            badge(WORKOUTS_100, SHOWING_UP, "100 workouts", "Finish 100 sessions."))),
                    new PathDef("WEEKS", List.of(
                            badge(FULL_WEEK, SHOWING_UP, "A full week",
                                    "Reach your sessions a week target in one Monday to Sunday week."),
                            badge(FOUR_FULL_WEEKS, SHOWING_UP, "Four full weeks",
                                    "Reach your sessions a week target four weeks in a row."))))),
            new ThemeDef(GETTING_STRONGER, "Getting stronger", List.of(
                    new PathDef("RECORDS", List.of(
                            badge(FIRST_RECORD, GETTING_STRONGER, "First record", "Set a personal record."),
                            badge(FIVE_RECORDS, GETTING_STRONGER, "Five records", "Set five personal records."))),
                    new PathDef("LIFTS", List.of(
                            badge(BODYWEIGHT_BENCH, GETTING_STRONGER, "Bench your body weight",
                                    "Reach an estimated best single on the bench press of your body weight."))))),
            new ThemeDef(EATING_WELL, "Eating well", List.of(
                    new PathDef("LOGGING", List.of(
                            badge(FIRST_MEAL, EATING_WELL, "First meal logged", "Log one meal."),
                            badge(SEVEN_DAYS_LOGGED, EATING_WELL, "7 days logged",
                                    "Log meals seven days in a row. A fast day counts."),
                            badge(THIRTY_DAYS_LOGGED, EATING_WELL, "30 days logged",
                                    "Log meals 30 days in a row. A fast day counts."))),
                    new PathDef("PROTEIN", List.of(
                            badge(PROTEIN_WEEK, EATING_WELL, "Protein week",
                                    "Meet your protein target on every logged day of a week with five or more logged days."),
                            badge(PROTEIN_MONTH, EATING_WELL, "Protein month",
                                    "Meet your protein target on every logged day of a month with 20 or more logged days."))))),
            new ThemeDef(IN_THE_KITCHEN, "In the kitchen", List.of(
                    new PathDef("COOKS", List.of(
                            badge(FIRST_COOK, IN_THE_KITCHEN, "First cook", "Cook one recipe."),
                            badge(TEN_COOKS, IN_THE_KITCHEN, "10 cooks", "Cook recipes 10 times."),
                            badge(FIFTY_COOKS, IN_THE_KITCHEN, "50 cooks", "Cook recipes 50 times."))))));

    static final List<BadgeDef> RETIRED = List.of(
            badge(STREAK_7, SHOWING_UP, "7 days in a row", "Train seven days in a row."),
            badge(STREAK_30, SHOWING_UP, "30 days in a row", "Train 30 days in a row."),
            badge(FIRST_WEIGHT_LOGGED, GETTING_STRONGER, "First weigh-in", "Log your weight once."));

    /** Every catalogue badge in display order. */
    static final List<BadgeDef> ALL = THEMES.stream()
            .flatMap(theme -> theme.paths().stream())
            .flatMap(path -> path.badges().stream())
            .toList();

    static final Map<String, BadgeDef> RETIRED_BY_CODE = RETIRED.stream()
            .collect(Collectors.toUnmodifiableMap(BadgeDef::code, Function.identity()));

    /** When two next badges are equally close, the one that is easiest to act on comes first. */
    static final List<String> TIE_ORDER = List.of(SHOWING_UP, EATING_WELL, IN_THE_KITCHEN, GETTING_STRONGER);

    private BadgeCatalogue() {
    }

    private static BadgeDef badge(String code, String theme, String name, String how) {
        return new BadgeDef(code, theme, name, how);
    }
}
