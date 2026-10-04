package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.web.error.ResourceNotFoundException;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.ExerciseSecondaryMuscleEntity;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutPlanDayEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.progress.ExerciseProgressCalculator.ExerciseInfo;
import com.fitsync.workout.progress.TrainingProgressCalculator.CardFacts;
import com.fitsync.workout.progress.TrainingProgressCalculator.Inputs;
import com.fitsync.workout.repository.ExerciseBestRow;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.ExerciseSecondaryRow;
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the caller's completed sessions for a window in a fixed number of queries (the sessions, their
 * logs with exercises and sets in chunks, the secondary groups, the best singles before the window) and
 * turns them into snapshots for the pure calculators.
 */
@Service
public class TrainingProgressServiceImpl implements TrainingProgressService {

    /** Fitness needs the five weeks before the first week shown. */
    static final int LOAD_LEAD_WEEKS = TrainingProgressCalculator.FITNESS_WEEKS - 1;
    /** The activity page looks this far back for its run of weeks. */
    static final int STREAK_LOOKBACK_WEEKS = 52;
    /** The weekly card describes the 28 days ending on its reference date. */
    static final int CARD_DAYS = 28;
    private static final int ID_CHUNK = 500;

    private final Clock clock;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    public TrainingProgressServiceImpl(Clock clock,
                                       WorkoutSessionRepository workoutSessionRepository,
                                       WorkoutExerciseLogRepository workoutExerciseLogRepository,
                                       ExerciseRepository exerciseRepository,
                                       WorkoutPlanRepository workoutPlanRepository) {
        this.clock = clock;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
        this.exerciseRepository = exerciseRepository;
        this.workoutPlanRepository = workoutPlanRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingProgressView getTraining(CurrentUserPrincipal currentUser, ZoneId zone, ProgressPeriod period,
                                            BigDecimal bodyWeightKg) {
        return TrainingProgressCalculator.compute(inputs(currentUser.userId(), zone, period,
                StrengthMath.acceptedBodyWeight(bodyWeightKg), true));
    }

    @Override
    @Transactional(readOnly = true)
    public CardFacts getCardFacts(UUID userId, ZoneId zone, LocalDate referenceDate) {
        ProgressPeriod period = new ProgressPeriod(referenceDate.minusDays(CARD_DAYS - 1L), referenceDate);
        return TrainingProgressCalculator.cardFacts(inputs(userId, zone, period, null, false));
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityProgressView getActivity(CurrentUserPrincipal currentUser, ZoneId zone, ActivityKey activityKey,
                                            ProgressPeriod period) {
        ProgressPeriod previous = period.previous();
        LocalDate windowStart = ProgressPeriod.mondayOf(period.to()).minusWeeks(STREAK_LOOKBACK_WEEKS);
        if (previous.from().isBefore(windowStart)) {
            windowStart = ProgressPeriod.mondayOf(previous.from());
        }
        UUID userId = currentUser.userId();
        List<SessionSnap> sessions = loadSessions(userId, zone, windowStart, previous.from(), period.to());
        Map<UUID, BigDecimal> bestBefore = activityKey.kind() == com.fitsync.workout.domain.SessionKind.STRENGTH
                ? bestBefore(userId, zone, previous.from()) : Map.of();
        return ActivityProgressCalculator.compute(activityKey, period, today(zone), sessions, bestBefore);
    }

    @Override
    @Transactional(readOnly = true)
    public ExerciseProgressView getExercise(CurrentUserPrincipal currentUser, ZoneId zone, UUID exerciseId,
                                            ProgressPeriod period) {
        UUID userId = currentUser.userId();
        ExerciseEntity exercise = exerciseRepository.findById(exerciseId)
                .filter(entity -> entity.getOwnerUserId() == null || entity.getOwnerUserId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Exercise not found"));
        List<MuscleGroup> secondary = new ArrayList<>();
        for (ExerciseSecondaryMuscleEntity muscle : exercise.getSecondaryMuscles()) {
            secondary.add(muscle.getMuscleGroup());
        }
        Set<MuscleGroup> secondarySet = secondary.isEmpty() ? Set.of() : EnumSet.copyOf(secondary);

        Map<UUID, List<SessionEntry>> bySession = new LinkedHashMap<>();
        Map<UUID, WorkoutSessionEntity> sessions = new HashMap<>();
        for (SessionEntry log : workoutExerciseLogRepository.findExerciseHistoryWithSets(
                userId, exerciseId, WorkoutSessionStatus.COMPLETED)) {
            WorkoutSessionEntity session = log.getSession();
            sessions.put(session.getSessionId(), session);
            bySession.computeIfAbsent(session.getSessionId(), id -> new ArrayList<>()).add(log);
        }
        List<SessionSnap> history = new ArrayList<>();
        for (Map.Entry<UUID, List<SessionEntry>> entry : bySession.entrySet()) {
            WorkoutSessionEntity session = sessions.get(entry.getKey());
            List<SetSnap> sets = new ArrayList<>();
            String name = exercise.getName();
            for (SessionEntry log : entry.getValue()) {
                sets.addAll(performedSets(log));
                name = log.getExerciseNameSnapshot();
            }
            ExerciseSnap snap = new ExerciseSnap(exerciseId, name, exercise.getPrimaryMuscleGroup(), secondarySet,
                    exercise.getEquipment(), sets);
            SessionSnap dated = snapshot(session, zone, List.of(snap));
            if (dated != null) {
                history.add(dated);
            }
        }
        history.sort(SESSION_ORDER);
        ExerciseInfo info = new ExerciseInfo(exerciseId, exercise.getName(), exercise.getPrimaryMuscleGroup(),
                secondary, exercise.getEquipment());
        return ExerciseProgressCalculator.compute(info, period, history);
    }

    // ------------------------------------------------------------------ loading

    private Inputs inputs(UUID userId, ZoneId zone, ProgressPeriod period, BigDecimal bodyWeightKg,
                          boolean withPlan) {
        ProgressPeriod previous = period.previous();
        LocalDate windowStart = ProgressPeriod.mondayOf(previous.from()).minusWeeks(LOAD_LEAD_WEEKS);
        List<SessionSnap> sessions = loadSessions(userId, zone, windowStart, previous.from(), period.to());
        return new Inputs(period, today(zone), sessions, bestBefore(userId, zone, previous.from()), bodyWeightKg,
                withPlan ? plannedDays(userId) : Set.of());
    }

    private LocalDate today(ZoneId zone) {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    private static final Comparator<SessionSnap> SESSION_ORDER = Comparator.comparing(SessionSnap::date)
            .thenComparing(SessionSnap::startedAt)
            .thenComparing(SessionSnap::sessionId);

    /**
     * The caller's completed sessions placed on dates from {@code windowStart} to {@code to}; sessions
     * from {@code setsFrom} on carry their exercises and performed sets.
     */
    List<SessionSnap> loadSessions(UUID userId, ZoneId zone, LocalDate windowStart, LocalDate setsFrom,
                                   LocalDate to) {
        Instant queryFrom = windowStart.minusDays(1).atStartOfDay(zone).toInstant();
        Instant queryTo = to.plusDays(2).atStartOfDay(zone).toInstant();
        List<WorkoutSessionEntity> raw = workoutSessionRepository.findByUserIdAndStatusInAndStartedAtBetween(
                userId, List.of(WorkoutSessionStatus.COMPLETED), queryFrom, queryTo);

        List<WorkoutSessionEntity> placed = new ArrayList<>();
        List<UUID> withSets = new ArrayList<>();
        for (WorkoutSessionEntity session : raw) {
            LocalDate date = dateOf(session, zone);
            if (date == null || date.isBefore(windowStart) || date.isAfter(to)) {
                continue;
            }
            placed.add(session);
            if (!date.isBefore(setsFrom)) {
                withSets.add(session.getSessionId());
            }
        }

        Map<UUID, List<SessionEntry>> logsBySession = new HashMap<>();
        Set<UUID> exerciseIds = new LinkedHashSet<>();
        for (List<UUID> chunk : chunks(withSets)) {
            for (SessionEntry log : workoutExerciseLogRepository.findWithSetsBySessionIds(userId, chunk)) {
                logsBySession.computeIfAbsent(log.getSession().getSessionId(), id -> new ArrayList<>()).add(log);
                exerciseIds.add(log.getExercise().getExerciseId());
            }
        }
        Map<UUID, Set<MuscleGroup>> secondaryByExercise = new HashMap<>();
        for (List<UUID> chunk : chunks(new ArrayList<>(exerciseIds))) {
            for (ExerciseSecondaryRow row : exerciseRepository.findSecondaryGroups(chunk)) {
                secondaryByExercise.computeIfAbsent(row.exerciseId(), id -> EnumSet.noneOf(MuscleGroup.class))
                        .add(row.muscleGroup());
            }
        }

        List<SessionSnap> snaps = new ArrayList<>();
        for (WorkoutSessionEntity session : placed) {
            List<SessionEntry> logs = new ArrayList<>(logsBySession.getOrDefault(session.getSessionId(),
                    List.of()));
            logs.sort(Comparator.comparing(SessionEntry::getSequence,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            List<ExerciseSnap> exercises = new ArrayList<>();
            for (SessionEntry log : logs) {
                ExerciseEntity exercise = log.getExercise();
                exercises.add(new ExerciseSnap(exercise.getExerciseId(), log.getExerciseNameSnapshot(),
                        exercise.getPrimaryMuscleGroup(),
                        secondaryByExercise.getOrDefault(exercise.getExerciseId(), Set.of()),
                        exercise.getEquipment(), performedSets(log)));
            }
            snaps.add(snapshot(session, zone, exercises));
        }
        snaps.sort(SESSION_ORDER);
        return snaps;
    }

    private Map<UUID, BigDecimal> bestBefore(UUID userId, ZoneId zone, LocalDate before) {
        Map<UUID, BigDecimal> best = new HashMap<>();
        for (ExerciseBestRow row : workoutExerciseLogRepository.findBestE1rmBefore(userId,
                WorkoutSessionStatus.COMPLETED, WorkoutSetStatus.SKIPPED, before.atStartOfDay(zone).toInstant())) {
            if (row.e1rm() != null) {
                best.put(row.exerciseId(), row.e1rm());
            }
        }
        return best;
    }

    private Set<DayOfWeek> plannedDays(UUID userId) {
        List<WorkoutPlanEntity> active = workoutPlanRepository.findByUserIdAndStatusOrderByDateTimeUpdatedDesc(
                userId, WorkoutPlanStatus.ACTIVE);
        if (active.isEmpty()) {
            return Set.of();
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (WorkoutPlanDayEntity day : active.get(0).getDays()) {
            Integer dayOfWeek = day.getDayOfWeek();
            if (dayOfWeek != null && dayOfWeek >= 1 && dayOfWeek <= 7) {
                days.add(DayOfWeek.of(dayOfWeek));
            }
        }
        return days;
    }

    /** A session is placed on the local date it was completed, else started, as the other insights do. */
    static LocalDate dateOf(WorkoutSessionEntity session, ZoneId zone) {
        Instant dating = session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
        return dating == null ? null : dating.atZone(zone).toLocalDate();
    }

    private static SessionSnap snapshot(WorkoutSessionEntity session, ZoneId zone, List<ExerciseSnap> exercises) {
        LocalDate date = dateOf(session, zone);
        if (date == null) {
            return null;
        }
        Instant started = session.getStartedAt() != null ? session.getStartedAt() : session.getCompletedAt();
        int minutes = session.getDurationMinutes() == null ? 0 : Math.max(0, session.getDurationMinutes());
        return new SessionSnap(session.getSessionId(), date, started, started.atZone(zone).toLocalDateTime(),
                session.getKind(), session.getSportActivity(), minutes, session.getPerceivedExertion(),
                session.isEffortEstimated(), session.getCaloriesBurned(), session.getDistanceKm(), exercises);
    }

    /** Sets that were performed: logged and not skipped, in set order. */
    static List<SetSnap> performedSets(SessionEntry log) {
        List<WorkoutExerciseSetLogEntity> sets = new ArrayList<>(log.getSets());
        sets.sort(Comparator.comparing(WorkoutExerciseSetLogEntity::getSetNumber,
                Comparator.nullsLast(Comparator.naturalOrder())));
        List<SetSnap> performed = new ArrayList<>();
        for (WorkoutExerciseSetLogEntity set : sets) {
            if (set.isLogged() && set.getStatus() != WorkoutSetStatus.SKIPPED) {
                performed.add(new SetSnap(set.getWeightKg(), set.getReps()));
            }
        }
        return performed;
    }

    private static List<List<UUID>> chunks(List<UUID> ids) {
        List<List<UUID>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += ID_CHUNK) {
            chunks.add(ids.subList(i, Math.min(ids.size(), i + ID_CHUNK)));
        }
        return chunks;
    }
}
