package com.fitsync.workout.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanDraftRequest;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanExerciseInput;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.PlanEntry;
import com.fitsync.workout.entity.WorkoutPlanDayEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.progress.PlanCoverageCalculator;
import com.fitsync.workout.progress.PlanCoverageCalculator.Day;
import com.fitsync.workout.progress.PlanCoverageCalculator.Move;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.ExerciseSecondaryRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads what a plan trains, for a draft the editor holds and for a saved plan, through
 * {@link PlanCoverageCalculator}. The muscle groups of each move come from the catalogue: a draft's
 * exercises are looked up among those the caller may use, and an unknown one is left out.
 */
@Service
public class PlanCoverageService {

    private final ExerciseRepository exerciseRepository;

    public PlanCoverageService(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }

    /** The coverage of a plan being built, against the profile figures it carries. */
    @Transactional(readOnly = true)
    public PlanCoverageView forDraft(UUID userId, PlanDraftRequest request) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (WorkoutPlanDayInput day : request.days()) {
            for (PlanEntryInput entry : day.entries()) {
                if (entry.type() == EntryType.SET && entry.exerciseId() != null) {
                    ids.add(entry.exerciseId());
                }
            }
            for (WorkoutPlanExerciseInput exercise : day.exercises()) {
                ids.add(exercise.exerciseId());
            }
        }
        Map<UUID, ExerciseEntity> exercises = new HashMap<>();
        for (ExerciseEntity exercise : exerciseRepository.findAllById(ids)) {
            boolean usable = exercise.getStatus() == ExerciseStatus.ACTIVE
                    && (exercise.getOwnerUserId() == null || exercise.getOwnerUserId().equals(userId));
            if (usable) {
                exercises.put(exercise.getExerciseId(), exercise);
            }
        }
        Map<UUID, Set<MuscleGroup>> secondary = secondaryGroups(new ArrayList<>(exercises.keySet()));

        List<Day> days = new ArrayList<>();
        for (WorkoutPlanDayInput day : request.days()) {
            List<Move> moves = new ArrayList<>();
            for (PlanEntryInput entry : day.entries()) {
                if (entry.type() == EntryType.SET) {
                    addMove(moves, exercises.get(entry.exerciseId()), secondary, entry.targetSets());
                }
            }
            for (WorkoutPlanExerciseInput exercise : day.exercises()) {
                int sets = exercise.setTargets().isEmpty() ? valueOf(exercise.targetSets()) : exercise.setTargets().size();
                addMove(moves, exercises.get(exercise.exerciseId()), secondary, sets);
            }
            days.add(new Day(day.dayOfWeek(), day.kind(), minutesOf(day), moves));
        }
        return PlanCoverageCalculator.calculate(days, request.profileMinutesPerWeek());
    }

    /** The coverage of a saved plan. A flat plan counts as one strength day. */
    @Transactional(readOnly = true)
    public PlanCoverageView forPlan(WorkoutPlanEntity plan) {
        List<PlanEntry> setEntries = plan.getSetEntries();
        List<UUID> ids = setEntries.stream()
                .filter(entry -> entry.getExercise() != null)
                .map(entry -> entry.getExercise().getExerciseId())
                .distinct()
                .toList();
        Map<UUID, Set<MuscleGroup>> secondary = secondaryGroups(ids);
        Map<UUID, List<Move>> movesByDay = new HashMap<>();
        List<Move> flatMoves = new ArrayList<>();
        for (PlanEntry entry : setEntries) {
            int sets = entry.getSetTargets().isEmpty() ? valueOf(entry.getTargetSets()) : entry.getSetTargets().size();
            List<Move> target = entry.getPlanDay() == null
                    ? flatMoves
                    : movesByDay.computeIfAbsent(entry.getPlanDay().getPlanDayId(), id -> new ArrayList<>());
            addMove(target, entry.getExercise(), secondary, sets);
        }
        List<Day> days = new ArrayList<>();
        if (plan.getDays().isEmpty()) {
            days.add(new Day(null, SessionKind.STRENGTH, plan.getEstimatedDurationMinutes(), flatMoves));
        }
        for (WorkoutPlanDayEntity day : plan.getDays()) {
            days.add(new Day(day.getDayOfWeek(), day.getKind(), day.getEstimatedDurationMinutes(),
                    movesByDay.getOrDefault(day.getPlanDayId(), List.of())));
        }
        return PlanCoverageCalculator.calculate(days, null);
    }

    private Map<UUID, Set<MuscleGroup>> secondaryGroups(List<UUID> ids) {
        Map<UUID, Set<MuscleGroup>> secondary = new HashMap<>();
        if (ids.isEmpty()) {
            return secondary;
        }
        for (ExerciseSecondaryRow row : exerciseRepository.findSecondaryGroups(ids)) {
            secondary.computeIfAbsent(row.exerciseId(), id -> EnumSet.noneOf(MuscleGroup.class)).add(row.muscleGroup());
        }
        return secondary;
    }

    private static void addMove(List<Move> moves, ExerciseEntity exercise, Map<UUID, Set<MuscleGroup>> secondary,
                                Integer sets) {
        if (exercise == null) {
            return;
        }
        moves.add(new Move(exercise.getName(), exercise.getPrimaryMuscleGroup(),
                secondary.getOrDefault(exercise.getExerciseId(), Set.of()), valueOf(sets)));
    }

    private static Integer minutesOf(WorkoutPlanDayInput day) {
        if (day.estimatedDurationMinutes() != null) {
            return day.estimatedDurationMinutes();
        }
        int seconds = PlanEntryRules.totalSeconds(day.entries());
        return seconds > 0 ? Math.round(seconds / 60f) : null;
    }

    private static int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}
