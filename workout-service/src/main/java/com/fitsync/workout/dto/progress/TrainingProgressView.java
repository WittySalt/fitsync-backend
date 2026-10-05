package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Training page of Progress for one period ({@code GET /api/v1/workouts/progress/training}).
 * Every figure is computed from the caller's completed sessions; {@code previous} is the period of
 * the same length that ends the day before {@code period.from}.
 */
@Schema(example = """
        {
          "period": {"from": "2026-09-06", "to": "2026-10-03"},
          "previous": {"from": "2026-08-09", "to": "2026-09-05"},
          "hero": {
            "sessions": {"value": 15, "previous": 13},
            "minutes": {"value": 710, "previous": 640},
            "load": {"value": 4320, "previous": 3960, "estimatedShare": 0.12},
            "records": {"value": 7, "previous": 4, "exercises": ["Deadlift", "Squat"]}
          },
          "highlights": [
            {"code": "LIFT_UP", "title": "Your deadlift's estimated best single is up 14% in four weeks.",
             "series": [{"label": "2026-09-08", "value": 81.7}, {"label": "2026-10-02", "value": 93.3}],
             "unit": "kg", "context": "+11.6 kg compared with 8 Sept, 3 records in these four weeks"}
          ],
          "activities": [],
          "load": {"weeks": [], "fitness": [], "fatigue": [], "form": "FRESH"},
          "muscles": {"weeksCounted": 4.0, "groups": [], "usualRange": {"low": 10, "high": 20}},
          "lifts": [],
          "calendar": [],
          "records": []
        }
        """)
public record TrainingProgressView(
        PeriodRange period,
        PeriodRange previous,
        Hero hero,
        List<Highlight> highlights,
        List<Activity> activities,
        Load load,
        Muscles muscles,
        List<Lift> lifts,
        List<CalendarDay> calendar,
        List<RecordEntry> records) {

    public TrainingProgressView {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        activities = activities == null ? List.of() : List.copyOf(activities);
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
        calendar = calendar == null ? List.of() : List.copyOf(calendar);
        records = records == null ? List.of() : List.copyOf(records);
    }

    @Override
    public List<Highlight> highlights() {
        return List.copyOf(highlights);
    }

    @Override
    public List<Activity> activities() {
        return List.copyOf(activities);
    }

    @Override
    public List<Lift> lifts() {
        return List.copyOf(lifts);
    }

    @Override
    public List<CalendarDay> calendar() {
        return List.copyOf(calendar);
    }

    @Override
    public List<RecordEntry> records() {
        return List.copyOf(records);
    }

    /**
     * The four figures at the top of the page, each with its value for the previous period. Sessions, minutes
     * and load count the days of the period that are over (today waits until it is done) against the same
     * number of days from the start of the previous period, so the change between them is fair; records count
     * the whole period.
     */
    public record Hero(Count sessions, Count minutes, LoadFigure load, RecordsFigure records) {
    }

    public record Count(long value, long previous) {
    }

    /** Training load, effort × minutes; {@code estimatedShare} is the share of it from estimated efforts. */
    public record LoadFigure(long value, long previous, BigDecimal estimatedShare) {
    }

    /** New estimated best singles set in the period, and the exercises that set them. */
    public record RecordsFigure(int value, int previous, List<String> exercises) {

        public RecordsFigure {
            exercises = exercises == null ? List.of() : List.copyOf(exercises);
        }

        @Override
        public List<String> exercises() {
            return List.copyOf(exercises);
        }
    }

    /** One card under "By activity". */
    public record Activity(String key, String name, int sessions, int minutes,
                           List<MinutesPoint> cumulativeMinutes, List<SummaryLine> summary, String context) {

        public Activity {
            cumulativeMinutes = cumulativeMinutes == null ? List.of() : List.copyOf(cumulativeMinutes);
            summary = summary == null ? List.of() : List.copyOf(summary);
        }

        @Override
        public List<MinutesPoint> cumulativeMinutes() {
            return List.copyOf(cumulativeMinutes);
        }

        @Override
        public List<SummaryLine> summary() {
            return List.copyOf(summary);
        }
    }

    /** The running total of minutes after one session. */
    public record MinutesPoint(LocalDate date, int minutes) {
    }

    /**
     * {@code weeks} holds every week of the chart, the week in progress too; {@code fitness}, {@code fatigue}
     * and {@code form} end at the last finished week. {@code currentWeek} is the week that holds today with its
     * load so far, null when the chart does not reach today.
     */
    public record Load(List<LoadWeek> weeks, List<WeekValue> fitness, List<WeekValue> fatigue, Form form,
                       CurrentLoadWeek currentWeek) {

        public Load {
            weeks = weeks == null ? List.of() : List.copyOf(weeks);
            fitness = fitness == null ? List.of() : List.copyOf(fitness);
            fatigue = fatigue == null ? List.of() : List.copyOf(fatigue);
        }

        @Override
        public List<LoadWeek> weeks() {
            return List.copyOf(weeks);
        }

        @Override
        public List<WeekValue> fitness() {
            return List.copyOf(fitness);
        }

        @Override
        public List<WeekValue> fatigue() {
            return List.copyOf(fatigue);
        }
    }

    /**
     * The week in progress: its Monday and its load, sessions and minutes so far, today included. These are
     * "so far" figures and are never compared.
     */
    public record CurrentLoadWeek(LocalDate weekStart, long soFar, int sessions, long minutes) {
    }

    /** How fitness compares with fatigue, only ever shown in words. */
    public enum Form {
        TIRED,
        BALANCED,
        FRESH
    }

    /** One Monday-to-Sunday week of load; {@code estimated} when any session in it had no effort of its own. */
    public record LoadWeek(LocalDate weekStart, ByKind byKind, long total, boolean estimated) {
    }

    /** Load by session kind; the JSON keys are the kind names. */
    public record ByKind(@JsonProperty("STRENGTH") long strength, @JsonProperty("CARDIO") long cardio,
                         @JsonProperty("SPORT") long sport, @JsonProperty("MOBILITY") long mobility) {

        public long of(SessionKind kind) {
            return switch (kind) {
                case STRENGTH -> strength;
                case CARDIO -> cardio;
                case SPORT -> sport;
                case MOBILITY -> mobility;
            };
        }
    }

    public record WeekValue(LocalDate weekStart, long value) {
    }

    public record Muscles(BigDecimal weeksCounted, List<MuscleGroupSets> groups, UsualRange usualRange) {

        public Muscles {
            groups = groups == null ? List.of() : List.copyOf(groups);
        }

        @Override
        public List<MuscleGroupSets> groups() {
            return List.copyOf(groups);
        }
    }

    public record MuscleGroupSets(MuscleGroup group, BigDecimal setsPerWeek, List<MuscleExercise> exercises) {

        public MuscleGroupSets {
            exercises = exercises == null ? List.of() : List.copyOf(exercises);
        }

        @Override
        public List<MuscleExercise> exercises() {
            return List.copyOf(exercises);
        }
    }

    /** Hard sets the exercise gave the group over the whole period (1 primary, 0.5 secondary). */
    public record MuscleExercise(UUID exerciseId, String name, BigDecimal sets) {
    }

    public record UsualRange(int low, int high) {
    }

    public record Lift(UUID exerciseId, String name, List<SeriesPoint> e1rm, BestSet bestSet, int changePct,
                       Relative relative, BigDecimal bodyWeightKg) {

        public Lift {
            e1rm = e1rm == null ? List.of() : List.copyOf(e1rm);
        }

        @Override
        public List<SeriesPoint> e1rm() {
            return List.copyOf(e1rm);
        }
    }

    public record BestSet(BigDecimal weightKg, int reps, LocalDate date) {
    }

    /** Estimated best single ÷ body weight, its level on the ladder and what the next rung needs. */
    public record Relative(BigDecimal ratio, Level level, Level nextLevel, BigDecimal nextAtKg) {
    }

    public enum Level {
        BEGINNER,
        NOVICE,
        INTERMEDIATE,
        ADVANCED
    }

    /** One trained day: the kind with the most minutes that day, the day's minutes, and whether a record fell. */
    public record CalendarDay(LocalDate date, SessionKind kind, int minutes, boolean record) {
    }

    public record RecordEntry(LocalDate date, UUID exerciseId, String name, BigDecimal value, String unit,
                              BigDecimal delta) {
    }
}
