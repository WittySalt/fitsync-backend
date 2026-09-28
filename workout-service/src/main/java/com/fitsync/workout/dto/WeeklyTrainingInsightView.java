package com.fitsync.workout.dto;

import java.time.LocalDate;
import java.util.List;

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
          }
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
        WeeklyVolumeView volume) {

    public WeeklyTrainingInsightView {
        muscleGroups = muscleGroups == null ? List.of() : List.copyOf(muscleGroups);
    }

    @Override
    public List<MuscleGroupInsightView> muscleGroups() {
        return List.copyOf(muscleGroups);
    }
}
