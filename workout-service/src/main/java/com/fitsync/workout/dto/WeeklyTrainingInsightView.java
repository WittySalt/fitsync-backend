package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "timezone": "Asia/Singapore",
          "referenceDate": "2026-09-27",
          "weekStart": "2026-09-21",
          "weekEnd": "2026-09-27",
          "completedSessions": 4,
          "totalMinutes": 170,
          "previousWeekCompletedSessions": 2,
          "previousWeekTotalMinutes": 85,
          "muscleGroups": [],
          "volume": {
            "thisWeekKg": 3846.00,
            "previousWeekKg": 1716.00,
            "weekBeforePreviousKg": 0.00,
            "thisWeekSessionsWithVolume": 2,
            "previousWeekSessionsWithVolume": 1
          },
          "days": [],
          "today": {
            "date": "2026-09-27",
            "kind": "OPEN",
            "label": "Open",
            "source": "NONE",
            "planDayId": null,
            "title": null,
            "estimatedMinutes": null,
            "moves": null,
            "lastOfKind": null,
            "carriedOver": []
          },
          "nextTraining": null,
          "usualStartTime": "18:30"
        }
        """)
public record WeeklyTrainingInsightView(
        String timezone,
        LocalDate referenceDate,
        LocalDate weekStart,
        LocalDate weekEnd,
        int completedSessions,
        int totalMinutes,
        int previousWeekCompletedSessions,
        int previousWeekTotalMinutes,
        List<MuscleGroupInsightView> muscleGroups,
        WeeklyVolumeView volume,
        List<DayInsightView> days,
        TodayInsightView today,
        NextTrainingView nextTraining,
        String usualStartTime) {

    public WeeklyTrainingInsightView {
        muscleGroups = muscleGroups == null ? List.of() : List.copyOf(muscleGroups);
        days = days == null ? List.of() : List.copyOf(days);
    }

    /** A view without a day-by-day breakdown, today's detail or the next training day, used by
     * fixtures that predate the day-kinds feature and are not about those fields. Today defaults
     * to the unresolved "open" state so the invariant that today is never null still holds. */
    public WeeklyTrainingInsightView(
            String timezone,
            LocalDate referenceDate,
            LocalDate weekStart,
            LocalDate weekEnd,
            int completedSessions,
            int totalMinutes,
            int previousWeekCompletedSessions,
            int previousWeekTotalMinutes,
            List<MuscleGroupInsightView> muscleGroups,
            WeeklyVolumeView volume) {
        this(timezone, referenceDate, weekStart, weekEnd, completedSessions, totalMinutes,
                previousWeekCompletedSessions, previousWeekTotalMinutes, muscleGroups, volume,
                defaultDays(weekStart, referenceDate),
                new TodayInsightView(referenceDate, DayKind.OPEN, "Open", DaySource.NONE,
                        null, null, null, null, null, List.of()),
                null,
                null);
    }

    private static List<DayInsightView> defaultDays(LocalDate weekStart, LocalDate referenceDate) {
        List<DayInsightView> defaultDays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            boolean past = date.isBefore(referenceDate);
            DayKind kind = past ? DayKind.REST : DayKind.OPEN;
            String label = past ? "Rest" : "Open";
            defaultDays.add(new DayInsightView(date, i + 1, kind, label, DaySource.NONE, null, List.of(), null));
        }
        return defaultDays;
    }

    @Override
    public List<MuscleGroupInsightView> muscleGroups() {
        return List.copyOf(muscleGroups);
    }

    @Override
    public List<DayInsightView> days() {
        return List.copyOf(days);
    }
}
