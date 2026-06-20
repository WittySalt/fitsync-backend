package com.fitsync.user.dto;

import java.time.LocalTime;
import java.util.List;

import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Training preference enum fields are serialized as stable code values.",
        example = """
        {
          "fitnessLevel": "200002",
          "trainingLocation": "210002",
          "weeklyWorkoutDays": 4,
          "preferredDurationMinutes": 60,
          "preferredWorkoutTime": "19:30:00",
          "intensityPreference": "220002",
          "equipmentInventory": ["230002", "230004"],
          "focusAreas": ["240001", "240005"]
        }
        """)
public record UpsertTrainingPreferencesRequest(
        @NotNull FitnessLevel fitnessLevel,
        @NotNull TrainingLocation trainingLocation,
        @Min(0) @Max(14) Integer weeklyWorkoutDays,
        @Min(5) @Max(240) Integer preferredDurationMinutes,
        LocalTime preferredWorkoutTime,
        @NotNull IntensityPreference intensityPreference,
        @Size(max = 8) List<@NotNull TrainingEquipment> equipmentInventory,
        @Size(max = 6) List<@NotNull TrainingFocusArea> focusAreas) {

    public UpsertTrainingPreferencesRequest {
        equipmentInventory = equipmentInventory == null ? List.of() : List.copyOf(equipmentInventory);
        focusAreas = focusAreas == null ? List.of() : List.copyOf(focusAreas);
    }

    @Override
    public List<TrainingEquipment> equipmentInventory() {
        return List.copyOf(equipmentInventory);
    }

    @Override
    public List<TrainingFocusArea> focusAreas() {
        return List.copyOf(focusAreas);
    }
}
