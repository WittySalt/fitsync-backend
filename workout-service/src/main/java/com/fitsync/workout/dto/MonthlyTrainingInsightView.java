package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "timezone": "Asia/Singapore",
          "month": "2026-09",
          "monthStart": "2026-09-01",
          "monthEnd": "2026-09-30",
          "referenceDate": "2026-10-01",
          "firstSessionDate": "2026-08-03",
          "days": [
            { "date": "2026-09-03", "kind": "CARDIO", "label": "Cardio", "sessions": 1, "minutes": 35,
              "muscles": ["CARDIO"], "record": false }
          ],
          "weeks": [
            { "weekStart": "2026-08-31", "weekEnd": "2026-09-06", "sessions": 4, "minutes": 195,
              "strengthSessions": 2, "cardioSessions": 1, "mobilitySessions": 0, "sportSessions": 1, "complete": true }
          ],
          "totals": { "sessions": 16, "minutes": 780, "records": 3, "longestStreakDays": 3 },
          "previousTotals": { "sessions": 15, "minutes": 720, "records": 2, "longestStreakDays": 2 },
          "muscles": [ { "group": "CHEST", "label": "Chest", "sessions": 8 } ]
        }
        """)
public record MonthlyTrainingInsightView(
        String timezone,
        String month,
        LocalDate monthStart,
        LocalDate monthEnd,
        LocalDate referenceDate,
        LocalDate firstSessionDate,
        List<MonthlyDayView> days,
        List<MonthlyWeekView> weeks,
        MonthlyTotalsView totals,
        MonthlyTotalsView previousTotals,
        List<MonthlyMuscleView> muscles) {

    public MonthlyTrainingInsightView {
        days = days == null ? List.of() : List.copyOf(days);
        weeks = weeks == null ? List.of() : List.copyOf(weeks);
        muscles = muscles == null ? List.of() : List.copyOf(muscles);
    }

    @Override
    public List<MonthlyDayView> days() {
        return List.copyOf(days);
    }

    @Override
    public List<MonthlyWeekView> weeks() {
        return List.copyOf(weeks);
    }

    @Override
    public List<MonthlyMuscleView> muscles() {
        return List.copyOf(muscles);
    }
}
