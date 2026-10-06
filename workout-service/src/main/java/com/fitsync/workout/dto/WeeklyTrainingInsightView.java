package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import com.fitsync.workout.dto.progress.ActivitySummaryView;
import com.fitsync.workout.dto.progress.LiftChangeView;
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
          "previousWeekSameDaysCompletedSessions": 2,
          "previousWeekSameDaysTotalMinutes": 85,
          "muscleGroups": [],
          "volume": {
            "thisWeekKg": 3846.00,
            "previousWeekKg": 1716.00,
            "weekBeforePreviousKg": 0.00,
            "thisWeekSessionsWithVolume": 2,
            "previousWeekSessionsWithVolume": 1,
            "previousWeekSameDaysKg": 1716.00,
            "previousWeekSameDaysSessionsWithVolume": 1
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
          "usualStartTime": "18:30",
          "lifts": [{"exerciseId": "7f1c1a52-6c1f-4c55-9a43-1f0b8e0c2d11", "name": "Deadlift", "e1rmStart": 81.7, "e1rmNow": 93.3, "changePct": 14}],
          "activitySummaries": [{"key": "STRENGTH", "name": "Strength", "summary": [{"figure": "e1RM +14%", "text": "e1RM +14% across one lift", "trend": "UP"}]}],
          "plannedDays": 4,
          "plannedDaysDone": 3
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
        String usualStartTime,
        int previousWeekSameDaysCompletedSessions,
        int previousWeekSameDaysTotalMinutes,
        List<LiftChangeView> lifts,
        List<ActivitySummaryView> activitySummaries,
        Integer plannedDays,
        Integer plannedDaysDone) {

    /**
     * The days of the week the active plan schedules ({@code plannedDays}) and how many of them were
     * trained this week, wherever in the week that happened ({@code plannedDaysDone}). Both are null
     * when no plan is active, so "0 of 0" is never shown.
     */
    public WeeklyTrainingInsightView {
        muscleGroups = muscleGroups == null ? List.of() : List.copyOf(muscleGroups);
        days = days == null ? List.of() : List.copyOf(days);
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
        activitySummaries = activitySummaries == null ? List.of() : List.copyOf(activitySummaries);
    }

    /** The view before the planned-days counts existed; both are null, as for a week without a plan. */
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
            WeeklyVolumeView volume,
            List<DayInsightView> days,
            TodayInsightView today,
            NextTrainingView nextTraining,
            String usualStartTime,
            int previousWeekSameDaysCompletedSessions,
            int previousWeekSameDaysTotalMinutes,
            List<LiftChangeView> lifts,
            List<ActivitySummaryView> activitySummaries) {
        this(timezone, referenceDate, weekStart, weekEnd, completedSessions, totalMinutes,
                previousWeekCompletedSessions, previousWeekTotalMinutes, muscleGroups, volume, days, today,
                nextTraining, usualStartTime, previousWeekSameDaysCompletedSessions,
                previousWeekSameDaysTotalMinutes, lifts, activitySummaries, null, null);
    }

    /** The view before the Progress card's lifts and activity summaries existed; both default to empty. */
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
            WeeklyVolumeView volume,
            List<DayInsightView> days,
            TodayInsightView today,
            NextTrainingView nextTraining,
            String usualStartTime,
            int previousWeekSameDaysCompletedSessions,
            int previousWeekSameDaysTotalMinutes) {
        this(timezone, referenceDate, weekStart, weekEnd, completedSessions, totalMinutes,
                previousWeekCompletedSessions, previousWeekTotalMinutes, muscleGroups, volume, days, today,
                nextTraining, usualStartTime, previousWeekSameDaysCompletedSessions,
                previousWeekSameDaysTotalMinutes, List.of(), List.of());
    }

    /** The view before the same-days figures existed; they default to last week's totals. */
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
            WeeklyVolumeView volume,
            List<DayInsightView> days,
            TodayInsightView today,
            NextTrainingView nextTraining,
            String usualStartTime) {
        this(timezone, referenceDate, weekStart, weekEnd, completedSessions, totalMinutes,
                previousWeekCompletedSessions, previousWeekTotalMinutes, muscleGroups, volume, days, today,
                nextTraining, usualStartTime, previousWeekCompletedSessions, previousWeekTotalMinutes);
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

    @Override
    public List<LiftChangeView> lifts() {
        return List.copyOf(lifts);
    }

    @Override
    public List<ActivitySummaryView> activitySummaries() {
        return List.copyOf(activitySummaries);
    }
}
