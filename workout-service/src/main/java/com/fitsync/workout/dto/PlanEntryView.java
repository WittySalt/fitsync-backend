package com.fitsync.workout.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SegmentKind;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseSecondaryMuscleEntity;

/**
 * One entry of a plan day as it is read back. A SET entry carries its exercise and targets; a SEGMENT
 * entry carries its kind, label, planned seconds and rounds, and no exercise.
 */
public record PlanEntryView(
        UUID entryId,
        EntryType entryType,
        Integer position,
        UUID exerciseId,
        String exerciseNameSnapshot,
        SegmentKind kind,
        String label,
        Integer plannedSeconds,
        Integer rounds,
        Integer targetSets,
        Integer targetReps,
        BigDecimal targetWeightKg,
        Integer targetDurationSeconds,
        BigDecimal targetDistanceKm,
        Integer restSeconds,
        BigDecimal tempoSeconds,
        String notes,
        String primaryImageUrl,
        /** The exercise's name, groups and equipment, so a client can show the muscle line under the name. */
        String exerciseName,
        MuscleGroup primaryMuscleGroup,
        List<MuscleGroup> secondaryMuscleGroups,
        ExerciseEquipment equipment,
        String imageUrl,
        /** True only for a move done with the body's own weight, which a client shows as "Body". */
        boolean bodyweight) {

    public PlanEntryView {
        secondaryMuscleGroups = secondaryMuscleGroups == null ? List.of() : List.copyOf(secondaryMuscleGroups);
    }

    /** A view without the exercise's details; {@link #withExercise} adds them. */
    public PlanEntryView(UUID entryId, EntryType entryType, Integer position, UUID exerciseId,
                         String exerciseNameSnapshot, SegmentKind kind, String label, Integer plannedSeconds,
                         Integer rounds, Integer targetSets, Integer targetReps, BigDecimal targetWeightKg,
                         Integer targetDurationSeconds, BigDecimal targetDistanceKm, Integer restSeconds,
                         BigDecimal tempoSeconds, String notes, String primaryImageUrl) {
        this(entryId, entryType, position, exerciseId, exerciseNameSnapshot, kind, label, plannedSeconds, rounds,
                targetSets, targetReps, targetWeightKg, targetDurationSeconds, targetDistanceKm, restSeconds,
                tempoSeconds, notes, primaryImageUrl, null, null, List.of(), null, null, false);
    }

    /** The same view with the name, groups, equipment and image of its exercise; unchanged without one. */
    public static PlanEntryView withExercise(PlanEntryView view, ExerciseEntity exercise) {
        if (exercise == null) {
            return view;
        }
        List<MuscleGroup> secondary = exercise.getSecondaryMuscles().stream()
                .map(ExerciseSecondaryMuscleEntity::getMuscleGroup)
                .filter(group -> group != exercise.getPrimaryMuscleGroup())
                .distinct()
                .toList();
        ExerciseEquipment equipment = exercise.getEquipment();
        String image = exercise.getImages().isEmpty() ? null : exercise.getImages().get(0).getUrl();
        return new PlanEntryView(view.entryId(), view.entryType(), view.position(), view.exerciseId(),
                view.exerciseNameSnapshot(), view.kind(), view.label(), view.plannedSeconds(), view.rounds(),
                view.targetSets(), view.targetReps(), view.targetWeightKg(), view.targetDurationSeconds(),
                view.targetDistanceKm(), view.restSeconds(), view.tempoSeconds(), view.notes(), view.primaryImageUrl(),
                exercise.getName(), exercise.getPrimaryMuscleGroup(), secondary, equipment, image,
                equipment == ExerciseEquipment.BODYWEIGHT || equipment == ExerciseEquipment.NONE);
    }

    @Override
    public List<MuscleGroup> secondaryMuscleGroups() {
        return List.copyOf(secondaryMuscleGroups);
    }
}
