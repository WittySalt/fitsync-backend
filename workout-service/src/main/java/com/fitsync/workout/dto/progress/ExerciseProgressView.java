package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The page of one exercise ({@code GET /api/v1/workouts/progress/exercises/{exerciseId}}): the
 * estimated best single over time with records marked, and every session's sets as logged.
 * {@code firstSeen}, {@code sessions} and {@code tiles.records} cover all history; the rest the period.
 */
@Schema(example = """
        {
          "exerciseId": "7f1c1a52-6c1f-4c55-9a43-1f0b8e0c2d11", "name": "Deadlift",
          "primaryGroup": "BACK", "secondaryGroups": ["LEGS", "GLUTES"], "equipment": "BARBELL",
          "firstSeen": "2026-08-07", "sessions": 14,
          "period": {"from": "2026-09-06", "to": "2026-10-03"},
          "tiles": {"e1rm": {"value": 93.3, "changeKg": 11.6, "since": "2026-09-08"},
                    "heaviestSet": {"weightKg": 80.0, "reps": 5, "date": "2026-10-02"},
                    "bestVolume": {"kg": 1175.0, "date": "2026-10-02"}, "records": 5, "recordsInPeriod": 3},
          "series": {"e1rm": [{"date": "2026-10-02", "value": 93.3, "isRecord": true}],
                     "heaviest": [], "volume": [], "reps": []},
          "history": [{"sessionId": "c0a8012e-0000-4000-8000-000000000001", "date": "2026-10-02",
                       "sets": [{"weightKg": 80.0, "reps": 5}], "record": true}],
          "summary": [{"figure": "e1RM 81.7 → 93.3 kg", "text": "e1RM 81.7 → 93.3 kg estimated best single",
                       "trend": "UP"}]
        }
        """)
public record ExerciseProgressView(
        UUID exerciseId,
        String name,
        MuscleGroup primaryGroup,
        List<MuscleGroup> secondaryGroups,
        ExerciseEquipment equipment,
        LocalDate firstSeen,
        int sessions,
        PeriodRange period,
        Tiles tiles,
        Series series,
        List<HistoryEntry> history,
        List<SummaryLine> summary) {

    public ExerciseProgressView {
        secondaryGroups = secondaryGroups == null ? List.of() : List.copyOf(secondaryGroups);
        history = history == null ? List.of() : List.copyOf(history);
        summary = summary == null ? List.of() : List.copyOf(summary);
    }

    @Override
    public List<MuscleGroup> secondaryGroups() {
        return List.copyOf(secondaryGroups);
    }

    @Override
    public List<HistoryEntry> history() {
        return List.copyOf(history);
    }

    @Override
    public List<SummaryLine> summary() {
        return List.copyOf(summary);
    }

    /** Tiles of the period; {@code records} counts every record ever set, {@code recordsInPeriod} those in the period. */
    public record Tiles(E1rmTile e1rm, SetFigure heaviestSet, VolumeFigure bestVolume, int records, int recordsInPeriod) {
    }

    /** The latest estimated best single in the period, its change since the first one and that first date. */
    public record E1rmTile(BigDecimal value, BigDecimal changeKg, LocalDate since) {
    }

    public record SetFigure(BigDecimal weightKg, int reps, LocalDate date) {
    }

    public record VolumeFigure(BigDecimal kg, LocalDate date) {
    }

    /** One point per session in the period: best single, heaviest weight, volume (kg × reps) and total reps. */
    public record Series(List<SeriesPoint> e1rm, List<SeriesPoint> heaviest, List<SeriesPoint> volume,
                         List<SeriesPoint> reps) {

        public Series {
            e1rm = e1rm == null ? List.of() : List.copyOf(e1rm);
            heaviest = heaviest == null ? List.of() : List.copyOf(heaviest);
            volume = volume == null ? List.of() : List.copyOf(volume);
            reps = reps == null ? List.of() : List.copyOf(reps);
        }

        @Override
        public List<SeriesPoint> e1rm() {
            return List.copyOf(e1rm);
        }

        @Override
        public List<SeriesPoint> heaviest() {
            return List.copyOf(heaviest);
        }

        @Override
        public List<SeriesPoint> volume() {
            return List.copyOf(volume);
        }

        @Override
        public List<SeriesPoint> reps() {
            return List.copyOf(reps);
        }
    }

    public record HistoryEntry(java.util.UUID sessionId, LocalDate date, List<LoggedSet> sets, boolean record) {

        public HistoryEntry {
            sets = sets == null ? List.of() : List.copyOf(sets);
        }

        @Override
        public List<LoggedSet> sets() {
            return List.copyOf(sets);
        }
    }

    public record LoggedSet(BigDecimal weightKg, Integer reps) {
    }
}
