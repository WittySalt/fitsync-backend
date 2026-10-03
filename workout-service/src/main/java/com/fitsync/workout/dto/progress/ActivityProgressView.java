package com.fitsync.workout.dto.progress;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The page of one activity ({@code GET /api/v1/workouts/progress/activities/{key}}). The key is
 * STRENGTH, CARDIO, MOBILITY or SPORT:&lt;activity&gt;; its sessions open the existing session page.
 */
@Schema(example = """
        {
          "key": "SPORT:BADMINTON", "name": "Badminton", "kind": "SPORT",
          "period": {"from": "2026-09-06", "to": "2026-10-03"},
          "previous": {"from": "2026-08-09", "to": "2026-09-05"},
          "tiles": {"minutes": 245, "sessions": 4, "weeksInARow": 4,
                    "longestSession": {"minutes": 65, "date": "2026-09-26"},
                    "usualTime": {"weekday": "SUNDAY", "hour": 15},
                    "effort": {"average": 6.0, "first": 5, "last": 7}},
          "weekly": [{"weekStart": "2026-09-28", "minutes": 0, "sessions": 0, "current": true}],
          "sessions": [],
          "whenGrid": [{"weekday": "SUNDAY", "hour": 15, "count": 3}],
          "summary": [{"figure": "Once a week,", "text": "Once a week, 4 weeks in a row", "trend": "STEADY"}],
          "context": "+1 h 10 min on the 4 weeks before"
        }
        """)
public record ActivityProgressView(
        String key,
        String name,
        SessionKind kind,
        PeriodRange period,
        PeriodRange previous,
        Tiles tiles,
        List<Week> weekly,
        List<Session> sessions,
        List<WhenCell> whenGrid,
        List<SummaryLine> summary,
        String context) {

    public ActivityProgressView {
        weekly = weekly == null ? List.of() : List.copyOf(weekly);
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        whenGrid = whenGrid == null ? List.of() : List.copyOf(whenGrid);
        summary = summary == null ? List.of() : List.copyOf(summary);
    }

    @Override
    public List<Week> weekly() {
        return List.copyOf(weekly);
    }

    @Override
    public List<Session> sessions() {
        return List.copyOf(sessions);
    }

    @Override
    public List<WhenCell> whenGrid() {
        return List.copyOf(whenGrid);
    }

    @Override
    public List<SummaryLine> summary() {
        return List.copyOf(summary);
    }

    public record Tiles(int minutes, int sessions, int weeksInARow, Longest longestSession, UsualTime usualTime,
                        Effort effort) {
    }

    public record Longest(int minutes, LocalDate date) {
    }

    /** The weekday most sessions fell on and the usual start hour on it; null with fewer than three sessions. */
    public record UsualTime(DayOfWeek weekday, int hour) {
    }

    /** Effort from 1 to 10 over the sessions that have one; first and last in date order. */
    public record Effort(BigDecimal average, Integer first, Integer last) {
    }

    public record Week(LocalDate weekStart, int minutes, int sessions, boolean current) {
    }

    public record Session(UUID sessionId, Instant startedAt, int minutes, Integer effort, boolean estimatedEffort,
                          Integer calories, BigDecimal distanceKm, Integer paceSecPerKm) {
    }

    public record WhenCell(DayOfWeek weekday, int hour, int count) {
    }
}
