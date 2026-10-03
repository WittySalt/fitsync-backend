package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;

/** One exercise of a session with only the sets that were performed. */
public record ExerciseSnap(UUID exerciseId, String name, MuscleGroup primary, Set<MuscleGroup> secondary,
                           ExerciseEquipment equipment, List<SetSnap> sets) {

    public ExerciseSnap {
        secondary = secondary == null ? Set.of() : Set.copyOf(secondary);
        sets = sets == null ? List.of() : List.copyOf(sets);
    }

    @Override
    public Set<MuscleGroup> secondary() {
        return Set.copyOf(secondary);
    }

    @Override
    public List<SetSnap> sets() {
        return List.copyOf(sets);
    }

    /** The set with the highest estimated best single, or null when no set has weight and reps. */
    public SetSnap bestSet() {
        SetSnap best = null;
        BigDecimal bestValue = null;
        for (SetSnap set : sets) {
            BigDecimal value = set.e1rm();
            if (value != null && (bestValue == null || value.compareTo(bestValue) > 0)) {
                best = set;
                bestValue = value;
            }
        }
        return best;
    }

    /** The session's estimated best single for this exercise: the highest among its sets, or null. */
    public BigDecimal e1rm() {
        SetSnap best = bestSet();
        return best == null ? null : best.e1rm();
    }

    public BigDecimal volume() {
        BigDecimal total = BigDecimal.ZERO;
        for (SetSnap set : sets) {
            total = total.add(set.volume());
        }
        return total;
    }
}
