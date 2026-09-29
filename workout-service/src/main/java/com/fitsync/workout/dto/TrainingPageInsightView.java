package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fitsync.workout.domain.InsightPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(example = """
        {
          "timezone": "Asia/Singapore",
          "referenceDate": "2026-09-28",
          "period": "PREVIOUS_WEEK",
          "periodStart": "2026-09-21",
          "periodEnd": "2026-09-27",
          "sessions": 4,
          "liftingSessions": 2,
          "volumePerSessionKg": 1923.00,
          "earlierVolumePerSessionKg": 1716.00,
          "minutesPerLiftingSession": 50,
          "earlierMinutesPerLiftingSession": 50,
          "record": {
            "exerciseName": "Dumbbell Bench Press",
            "weightKg": 22.50,
            "achievedOn": "2026-09-21",
            "previousBestKg": 20.00
          },
          "mostTrained": {
            "muscleGroup": "CORE",
            "sessions": 4,
            "exerciseNames": ["Plank Hold"]
          },
          "lifts": [
            { "exerciseName": "Dumbbell Bench Press", "fromKg": 20.00, "fromDate": "2026-09-16", "toKg": 22.50, "toDate": "2026-09-25" }
          ],
          "cardio": { "sessions": 2, "earlierSessions": 2, "distancePerSessionKm": 5.75, "earlierDistancePerSessionKm": 5.50,
                      "paceSecondsPerKm": 365, "earlierPaceSecondsPerKm": 370, "minutesPerSession": 35, "earlierMinutesPerSession": 34 },
          "mobility": { "sessions": 1, "minutes": 20, "earlierSessions": 1, "earlierMinutes": 20 },
          "sport": { "sessions": 1, "minutes": 60, "activities": [ { "activity": "BADMINTON", "label": "Badminton", "sessions": 1, "minutes": 60 } ] }
        }
        """)
public record TrainingPageInsightView(
        String timezone,
        LocalDate referenceDate,
        InsightPeriod period,
        LocalDate periodStart,
        LocalDate periodEnd,
        int sessions,
        int liftingSessions,
        BigDecimal volumePerSessionKg,
        BigDecimal earlierVolumePerSessionKg,
        Integer minutesPerLiftingSession,
        Integer earlierMinutesPerLiftingSession,
        TrainingRecordView record,
        MostTrainedView mostTrained,
        List<LiftProgressView> lifts,
        CardioInsightView cardio,
        MobilityInsightView mobility,
        SportInsightView sport) {

    public TrainingPageInsightView {
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
    }

    @Override
    public List<LiftProgressView> lifts() {
        return List.copyOf(lifts);
    }
}
