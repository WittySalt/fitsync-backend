package com.fitsync.user.entity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.persistence.entity.BaseAuditableEntity;
import com.fitsync.user.domain.FitnessLevel;
import com.fitsync.user.domain.IntensityPreference;
import com.fitsync.user.domain.TrainingEquipment;
import com.fitsync.user.domain.TrainingFocusArea;
import com.fitsync.user.domain.TrainingLocation;
import jakarta.persistence.CollectionTable;
import com.fitsync.user.entity.converter.CodeEnumConverters;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_training_preferences")
public class UserTrainingPreferencesEntity extends BaseAuditableEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Convert(converter = CodeEnumConverters.FitnessLevelConverter.class)
    @Column(name = "fitness_level_code", nullable = false, length = 40)
    private FitnessLevel fitnessLevel;

    @Convert(converter = CodeEnumConverters.TrainingLocationConverter.class)
    @Column(name = "training_location_code", nullable = false, length = 40)
    private TrainingLocation trainingLocation;

    @Column(name = "weekly_workout_days")
    private Integer weeklyWorkoutDays;

    @Column(name = "preferred_duration_minutes")
    private Integer preferredDurationMinutes;

    @Column(name = "preferred_workout_time")
    private LocalTime preferredWorkoutTime;

    @Convert(converter = CodeEnumConverters.IntensityPreferenceConverter.class)
    @Column(name = "intensity_preference_code", nullable = false, length = 40)
    private IntensityPreference intensityPreference;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_training_preference_equipment", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "equipment_code", nullable = false, length = 40)
    @Convert(converter = CodeEnumConverters.TrainingEquipmentConverter.class)
    private List<TrainingEquipment> equipmentInventory = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_training_preference_focus_areas", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "focus_area_code", nullable = false, length = 40)
    @Convert(converter = CodeEnumConverters.TrainingFocusAreaConverter.class)
    private List<TrainingFocusArea> focusAreas = new ArrayList<>();

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public FitnessLevel getFitnessLevel() { return fitnessLevel; }
    public void setFitnessLevel(FitnessLevel fitnessLevel) { this.fitnessLevel = fitnessLevel; }
    public TrainingLocation getTrainingLocation() { return trainingLocation; }
    public void setTrainingLocation(TrainingLocation trainingLocation) { this.trainingLocation = trainingLocation; }
    public Integer getWeeklyWorkoutDays() { return weeklyWorkoutDays; }
    public void setWeeklyWorkoutDays(Integer weeklyWorkoutDays) { this.weeklyWorkoutDays = weeklyWorkoutDays; }
    public Integer getPreferredDurationMinutes() { return preferredDurationMinutes; }
    public void setPreferredDurationMinutes(Integer preferredDurationMinutes) { this.preferredDurationMinutes = preferredDurationMinutes; }
    public LocalTime getPreferredWorkoutTime() { return preferredWorkoutTime; }
    public void setPreferredWorkoutTime(LocalTime preferredWorkoutTime) { this.preferredWorkoutTime = preferredWorkoutTime; }
    public IntensityPreference getIntensityPreference() { return intensityPreference; }
    public void setIntensityPreference(IntensityPreference intensityPreference) { this.intensityPreference = intensityPreference; }
    public List<TrainingEquipment> getEquipmentInventory() { return List.copyOf(equipmentInventory); }
    public void setEquipmentInventory(List<TrainingEquipment> equipmentInventory) {
        this.equipmentInventory = equipmentInventory == null ? new ArrayList<>() : new ArrayList<>(equipmentInventory);
    }
    public List<TrainingFocusArea> getFocusAreas() { return List.copyOf(focusAreas); }
    public void setFocusAreas(List<TrainingFocusArea> focusAreas) {
        this.focusAreas = focusAreas == null ? new ArrayList<>() : new ArrayList<>(focusAreas);
    }
}
