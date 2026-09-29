package com.fitsync.workout.domain;

/**
 * The specific sport or activity behind a SPORT-kind session. Each activity carries its own
 * display label plus the effort/measure pair that decides how it is counted and rendered.
 */
public enum SportActivity {
    BASKETBALL("Basketball", SportEffort.CARDIO, SportMeasure.TIME),
    FOOTBALL("Football", SportEffort.CARDIO, SportMeasure.TIME),
    BADMINTON("Badminton", SportEffort.CARDIO, SportMeasure.TIME),
    TENNIS("Tennis", SportEffort.CARDIO, SportMeasure.TIME),
    TABLE_TENNIS("Table tennis", SportEffort.CARDIO, SportMeasure.TIME),
    VOLLEYBALL("Volleyball", SportEffort.CARDIO, SportMeasure.TIME),
    SWIMMING("Swimming", SportEffort.CARDIO, SportMeasure.TIME_AND_DISTANCE),
    CYCLING("Cycling", SportEffort.CARDIO, SportMeasure.TIME_AND_DISTANCE),
    HIKING("Hiking", SportEffort.CARDIO, SportMeasure.TIME_AND_DISTANCE),
    DANCE("Dance", SportEffort.CARDIO, SportMeasure.TIME),
    MARTIAL_ARTS("Martial arts", SportEffort.CARDIO, SportMeasure.TIME),
    CLIMBING("Climbing", SportEffort.STRENGTH, SportMeasure.TIME),
    GOLF("Golf", SportEffort.SKILL, SportMeasure.TIME),
    BOWLING("Bowling", SportEffort.SKILL, SportMeasure.TIME),
    OTHER("Other", SportEffort.SKILL, SportMeasure.TIME);

    private final String label;
    private final SportEffort effort;
    private final SportMeasure measure;

    SportActivity(String label, SportEffort effort, SportMeasure measure) {
        this.label = label;
        this.effort = effort;
        this.measure = measure;
    }

    public String label() {
        return label;
    }

    public SportEffort effort() {
        return effort;
    }

    public SportMeasure measure() {
        return measure;
    }
}
