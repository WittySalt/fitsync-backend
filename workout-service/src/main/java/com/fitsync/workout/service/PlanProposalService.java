package com.fitsync.workout.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.EntryType;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.GoalFocus;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutType;
import com.fitsync.workout.dto.PlanCoverageView;
import com.fitsync.workout.dto.PlanDraftRequest;
import com.fitsync.workout.dto.PlanEntryInput;
import com.fitsync.workout.dto.PlanEntryView;
import com.fitsync.workout.dto.PlanProposalRequest;
import com.fitsync.workout.dto.WorkoutPlanDayInput;
import com.fitsync.workout.dto.WorkoutPlanDayView;
import com.fitsync.workout.dto.WorkoutPlanView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the default week for the plan editor (spec 2.2) from the profile the client sends, the
 * person's habit days from their last eight weeks of sessions, and the shared catalogue. Stateless:
 * nothing is saved; the answer is a draft plan the editor fills and saves with the plan API.
 */
@Service
public class PlanProposalService {

    static final int HABIT_WEEKS = 8;
    private static final Map<String, Set<ExerciseEquipment>> EQUIPMENT = Map.of(
            "NONE", EnumSet.noneOf(ExerciseEquipment.class),
            "DUMBBELLS", EnumSet.of(ExerciseEquipment.DUMBBELL),
            "DUMBBELL", EnumSet.of(ExerciseEquipment.DUMBBELL),
            "BARBELL", EnumSet.of(ExerciseEquipment.BARBELL),
            "BANDS", EnumSet.of(ExerciseEquipment.BAND),
            "BAND", EnumSet.of(ExerciseEquipment.BAND),
            "PULL_UP_BAR", EnumSet.of(ExerciseEquipment.OTHER),
            "KETTLEBELL", EnumSet.of(ExerciseEquipment.KETTLEBELL),
            "MACHINES", EnumSet.of(ExerciseEquipment.MACHINE, ExerciseEquipment.CABLE),
            "MACHINE", EnumSet.of(ExerciseEquipment.MACHINE, ExerciseEquipment.CABLE));

    private static final Set<String> FOCUS_AREAS = Set.of("FULL_BODY", "UPPER", "LOWER", "CORE", "MOBILITY",
            "CARDIO", "STRENGTH");

    private final Clock clock;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final PlanCoverageService planCoverageService;

    public PlanProposalService(Clock clock,
                               ExerciseRepository exerciseRepository,
                               WorkoutSessionRepository workoutSessionRepository,
                               PlanCoverageService planCoverageService) {
        this.clock = clock;
        this.exerciseRepository = exerciseRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.planCoverageService = planCoverageService;
    }

    /** The draft plan: days, entries and coverage. The plan has no id until it is saved. */
    @Transactional(readOnly = true)
    public WorkoutPlanView propose(UUID userId, PlanProposalRequest request, String timezone) {
        WorkoutDifficulty level = levelOf(request.fitnessLevel());
        Set<String> focus = new LinkedHashSet<>();
        request.focusAreas().forEach(area -> {
            String known = canonical(area, FOCUS_AREAS);
            if (known != null) {
                focus.add(known);
            }
        });
        PlanProposalPlanner.Profile profile = new PlanProposalPlanner.Profile(level, request.weeklyWorkoutDays(),
                request.preferredDurationMinutes(), focus);

        CataloguePicker picker = new CataloguePicker(level, equipmentOf(request.equipment()));
        List<WorkoutPlanDayInput> days = PlanProposalPlanner.propose(profile, habits(userId, timezone), picker);

        PlanCoverageView coverage = planCoverageService.forDraft(userId,
                new PlanDraftRequest(days, profile.days(), profile.minutes()));
        List<WorkoutPlanDayView> dayViews = days.stream().map(day -> toDayView(day, picker.chosen)).toList();
        return new WorkoutPlanView(null, PlanProposalPlanner.nameOf(days), null, GoalFocus.GENERAL_FITNESS,
                workoutTypeOf(days), level, profile.minutes(), null, List.of(), null, null, dayViews, days.size(),
                coverage);
    }

    private List<HabitDays.Habit> habits(UUID userId, String timezone) {
        ZoneId zone = InsightDates.resolveZone(timezone);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant from = today.minusWeeks(HABIT_WEEKS).atStartOfDay(zone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(zone).toInstant();
        List<HabitDays.SessionDay> sessions = new ArrayList<>();
        for (WorkoutSessionEntity session : workoutSessionRepository.findByUserIdAndStatusInAndStartedAtBetween(
                userId, List.of(WorkoutSessionStatus.COMPLETED), from, to)) {
            Instant dating = session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
            int minutes = session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
            sessions.add(new HabitDays.SessionDay(dating.atZone(zone).toLocalDate(), session.getKind(),
                    session.getSportActivity(), minutes));
        }
        return HabitDays.find(sessions, today);
    }

    static WorkoutDifficulty levelOf(String code) {
        if (code == null || code.isBlank()) {
            return WorkoutDifficulty.BEGINNER;
        }
        for (WorkoutDifficulty level : WorkoutDifficulty.values()) {
            if (sameCode(level.name(), code)) {
                return level;
            }
        }
        return WorkoutDifficulty.BEGINNER;
    }

    /** The catalogue equipment a person can use: bodyweight always, plus what they own; everything without a list. */
    static List<ExerciseEquipment> equipmentOf(List<String> codes) {
        if (codes.isEmpty() || codes.stream().anyMatch(code -> sameCode("FULL_GYM", code))) {
            return List.of(ExerciseEquipment.values());
        }
        Set<ExerciseEquipment> allowed = EnumSet.of(ExerciseEquipment.BODYWEIGHT, ExerciseEquipment.NONE);
        for (String code : codes) {
            String known = canonical(code, EQUIPMENT.keySet());
            if (known != null) {
                allowed.addAll(EQUIPMENT.get(known));
            }
        }
        return new ArrayList<>(allowed);
    }

    /** Two codes are the same in any case; compared by the case-insensitive order, without changing case. */
    private static boolean sameCode(String known, String sent) {
        return sent != null && String.CASE_INSENSITIVE_ORDER.compare(known, sent) == 0;
    }

    /** The known code a sent one matches in any case, or null. Compared without changing case. */
    private static String canonical(String code, Set<String> known) {
        for (String candidate : known) {
            if (sameCode(candidate, code)) {
                return candidate;
            }
        }
        return null;
    }

    static List<WorkoutDifficulty> levelsUpTo(WorkoutDifficulty level) {
        return switch (level) {
            case BEGINNER -> List.of(WorkoutDifficulty.BEGINNER);
            case INTERMEDIATE -> List.of(WorkoutDifficulty.BEGINNER, WorkoutDifficulty.INTERMEDIATE);
            case ADVANCED -> List.of(WorkoutDifficulty.values());
        };
    }

    private static WorkoutType workoutTypeOf(List<WorkoutPlanDayInput> days) {
        Set<SessionKind> kinds = EnumSet.noneOf(SessionKind.class);
        days.forEach(day -> kinds.add(day.kind()));
        if (kinds.size() == 1) {
            return switch (kinds.iterator().next()) {
                case STRENGTH -> WorkoutType.STRENGTH;
                case CARDIO -> WorkoutType.CARDIO;
                case MOBILITY -> WorkoutType.MOBILITY;
                case SPORT -> WorkoutType.SPORTS;
            };
        }
        return WorkoutType.CUSTOM;
    }

    private static WorkoutPlanDayView toDayView(WorkoutPlanDayInput day, Map<UUID, ExerciseEntity> chosen) {
        List<PlanEntryView> entries = new ArrayList<>();
        for (PlanEntryInput entry : day.entries()) {
            ExerciseEntity exercise = entry.exerciseId() == null ? null : chosen.get(entry.exerciseId());
            boolean set = entry.type() == EntryType.SET;
            entries.add(new PlanEntryView(null, entry.type(), entry.position(), entry.exerciseId(),
                    exercise == null ? null : exercise.getName(), entry.kind(),
                    set ? null : PlanEntryRules.labelOf(entry.kind(), entry.label()),
                    set ? entry.setSeconds() : entry.plannedSeconds(),
                    entry.rounds(), entry.targetSets(), entry.targetReps(), entry.targetWeightKg(),
                    set ? entry.setSeconds() : null, null, entry.restSeconds(), entry.tempoSeconds(), entry.notes(),
                    exercise == null || exercise.getImages().isEmpty() ? null : exercise.getImages().get(0).getUrl()));
        }
        return new WorkoutPlanDayView(null, day.dayOfWeek(), day.kind(), day.title(), day.sportActivity(),
                day.sportActivity() == null ? null : day.sportActivity().label(), day.estimatedDurationMinutes(),
                List.of(), day.targetKind(), day.targetValue(), day.location(), day.effortTarget(), entries);
    }

    /**
     * Picks from the shared catalogue at the person's level and with their equipment. Within a group a
     * move whose name matches an earlier hint ranks higher; then moves with images, then shorter names.
     */
    private final class CataloguePicker implements PlanProposalPlanner.MovePicker {

        private final WorkoutDifficulty level;
        private final List<ExerciseEquipment> equipment;
        private final Map<UUID, ExerciseEntity> chosen = new HashMap<>();
        private Map<MuscleGroup, List<ExerciseEntity>> strengthByGroup;

        CataloguePicker(WorkoutDifficulty level, List<ExerciseEquipment> equipment) {
            this.level = level;
            this.equipment = equipment;
        }

        @Override
        public Optional<PlanProposalPlanner.Move> pick(MuscleGroup group, List<String> hints, int variant,
                                                       Set<UUID> used) {
            List<ExerciseEntity> candidates = strength().getOrDefault(group, List.of()).stream()
                    .filter(exercise -> !used.contains(exercise.getExerciseId()))
                    .sorted(Comparator.comparingInt((ExerciseEntity exercise) -> hintRank(exercise, hints))
                            .thenComparing(exercise -> exercise.getImages().isEmpty())
                            .thenComparingInt(exercise -> exercise.getName().length())
                            .thenComparing(ExerciseEntity::getName))
                    .toList();
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            int bestRank = hintRank(candidates.get(0), hints);
            List<ExerciseEntity> best = candidates.stream()
                    .filter(exercise -> hintRank(exercise, hints) == bestRank)
                    .toList();
            ExerciseEntity pick = best.get(Math.min(variant, best.size() - 1));
            chosen.put(pick.getExerciseId(), pick);
            return Optional.of(new PlanProposalPlanner.Move(pick.getExerciseId(), pick.getName()));
        }

        @Override
        public List<PlanProposalPlanner.Move> mobility(int count) {
            List<ExerciseEntity> candidates = exerciseRepository.findProposalCandidates(ExerciseStatus.ACTIVE,
                            ExerciseType.MOBILITY, levelsUpTo(level), equipment).stream()
                    .sorted(Comparator.comparing((ExerciseEntity exercise) -> exercise.getImages().isEmpty())
                            .thenComparingInt(exercise -> exercise.getName().length())
                            .thenComparing(ExerciseEntity::getName))
                    .toList();
            List<PlanProposalPlanner.Move> moves = new ArrayList<>();
            Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
            // One move per group first, so the flow moves the whole body; then the next best.
            for (ExerciseEntity exercise : candidates) {
                if (moves.size() < count && groups.add(exercise.getPrimaryMuscleGroup())) {
                    moves.add(choose(exercise));
                }
            }
            for (ExerciseEntity exercise : candidates) {
                if (moves.size() < count && !chosen.containsKey(exercise.getExerciseId())) {
                    moves.add(choose(exercise));
                }
            }
            return moves;
        }

        private PlanProposalPlanner.Move choose(ExerciseEntity exercise) {
            chosen.put(exercise.getExerciseId(), exercise);
            return new PlanProposalPlanner.Move(exercise.getExerciseId(), exercise.getName());
        }

        private Map<MuscleGroup, List<ExerciseEntity>> strength() {
            if (strengthByGroup == null) {
                strengthByGroup = new EnumMap<>(MuscleGroup.class);
                for (ExerciseEntity exercise : exerciseRepository.findProposalCandidates(ExerciseStatus.ACTIVE,
                        ExerciseType.STRENGTH, levelsUpTo(level), equipment)) {
                    strengthByGroup.computeIfAbsent(exercise.getPrimaryMuscleGroup(), group -> new ArrayList<>())
                            .add(exercise);
                }
            }
            return strengthByGroup;
        }

        private int hintRank(ExerciseEntity exercise, List<String> hints) {
            String name = exercise.getName().toLowerCase(Locale.ROOT);
            for (int index = 0; index < hints.size(); index++) {
                if (name.contains(hints.get(index))) {
                    return index;
                }
            }
            return hints.size();
        }
    }
}
