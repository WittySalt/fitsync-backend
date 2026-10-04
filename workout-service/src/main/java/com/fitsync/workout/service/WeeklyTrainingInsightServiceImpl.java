package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutPlanStatus;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.dto.MuscleGroupInsightView;
import com.fitsync.workout.dto.WeeklyTrainingInsightView;
import com.fitsync.workout.dto.WeeklyVolumeView;
import com.fitsync.workout.dto.progress.ActivitySummaryView;
import com.fitsync.workout.dto.progress.LiftChangeView;
import com.fitsync.workout.dto.progress.SeriesPoint;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutPlanDayEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.entity.PlanEntry;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.progress.TrainingProgressCalculator;
import com.fitsync.workout.progress.TrainingProgressService;
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyTrainingInsightServiceImpl implements WeeklyTrainingInsightService {

    /** The muscle-group lookback window: 28 days ending on (and including) the reference date. */
    static final int LOOKBACK_DAYS = 28;

    /** exerciseNames is capped so the payload cannot grow unbounded for a heavily varied week. */
    private static final int MAX_EXERCISE_NAMES = 5;

    /** The habit rule looks four full weeks back from the start of the reference week. */
    private static final int HABIT_LOOKBACK_WEEKS = 4;

    /** lastOfKind looks back at most this many days for a prior session of the same kind. */
    private static final int LAST_OF_KIND_LOOKBACK_DAYS = 90;

    private static final List<WorkoutSessionStatus> DAY_KIND_STATUSES =
            List.of(WorkoutSessionStatus.COMPLETED, WorkoutSessionStatus.IN_PROGRESS);

    private final Clock clock;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;
    private final TrainingProgressService trainingProgressService;

    public WeeklyTrainingInsightServiceImpl(Clock clock,
                                            WorkoutSessionRepository workoutSessionRepository,
                                            WorkoutPlanRepository workoutPlanRepository,
                                            WorkoutExerciseLogRepository workoutExerciseLogRepository,
                                            TrainingProgressService trainingProgressService) {
        this.clock = clock;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
        this.trainingProgressService = trainingProgressService;
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyTrainingInsightView getWeeklyTrainingInsight(CurrentUserPrincipal currentUser,
                                                              String timezone,
                                                              LocalDate date) {
        ZoneId zoneId = resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        LocalDate referenceDate = date != null ? date : today;

        LocalDate weekStart = mondayOf(referenceDate);
        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        LocalDate previousWeekEnd = weekStart.minusDays(1);
        LocalDate weekBeforePreviousStart = weekStart.minusWeeks(2);
        LocalDate weekBeforePreviousEnd = previousWeekStart.minusDays(1);
        LocalDate lookbackStart = referenceDate.minusDays(LOOKBACK_DAYS - 1L);
        LocalDate windowStart = lookbackStart.isBefore(weekBeforePreviousStart) ? lookbackStart : weekBeforePreviousStart;

        List<DatedSession> sessions = loadSessions(currentUser.userId(), zoneId, referenceDate, windowStart);

        WeeklyTotals thisWeek = new WeeklyTotals();
        WeeklyTotals previousWeek = new WeeklyTotals();
        WeeklyTotals previousWeekSameDays = new WeeklyTotals();
        // Last week up to the same weekday as the reference date: the same number of elapsed days.
        LocalDate previousWeekSameDaysEnd = previousWeekStart.plusDays(
                java.time.temporal.ChronoUnit.DAYS.between(weekStart, referenceDate));
        BigDecimal weekBeforePreviousKg = BigDecimal.ZERO;

        for (DatedSession dated : sessions) {
            LocalDate sessionDate = dated.date();
            BigDecimal sessionVolume = sumVolume(dated.session());
            int minutes = dated.session().getDurationMinutes() == null ? 0 : dated.session().getDurationMinutes();

            if (!sessionDate.isBefore(weekStart) && !sessionDate.isAfter(referenceDate)) {
                thisWeek.accumulate(minutes, sessionVolume);
            } else if (!sessionDate.isBefore(previousWeekStart) && !sessionDate.isAfter(previousWeekEnd)) {
                previousWeek.accumulate(minutes, sessionVolume);
                if (!sessionDate.isAfter(previousWeekSameDaysEnd)) {
                    previousWeekSameDays.accumulate(minutes, sessionVolume);
                }
            } else if (!sessionDate.isBefore(weekBeforePreviousStart) && !sessionDate.isAfter(weekBeforePreviousEnd)) {
                weekBeforePreviousKg = weekBeforePreviousKg.add(sessionVolume);
            }
        }

        WeeklyVolumeView volume = new WeeklyVolumeView(
                scaleKg(thisWeek.volumeKg),
                scaleKg(previousWeek.volumeKg),
                scaleKg(weekBeforePreviousKg),
                thisWeek.sessionsWithVolume,
                previousWeek.sessionsWithVolume,
                scaleKg(previousWeekSameDays.volumeKg),
                previousWeekSameDays.sessionsWithVolume);

        WorkoutPlanEntity activePlan = findActivePlan(currentUser.userId());
        List<MuscleGroupInsightView> muscleGroups = buildMuscleGroupInsights(
                activePlan, sessions, referenceDate, weekStart);

        WeekByDayCalculator.Result weekByDay = computeWeekByDay(currentUser.userId(), zoneId, referenceDate,
                weekStart, activePlan);

        // The Progress card's lifts and activity lines: the same figures as the Training page's
        // default four weeks, ending on the reference date.
        TrainingProgressCalculator.CardFacts cardFacts =
                trainingProgressService.getCardFacts(currentUser.userId(), zoneId, referenceDate);

        return new WeeklyTrainingInsightView(
                zoneId.getId(),
                referenceDate,
                weekStart,
                weekEnd,
                thisWeek.sessions,
                thisWeek.minutes,
                previousWeek.sessions,
                previousWeek.minutes,
                muscleGroups,
                volume,
                weekByDay.days(),
                weekByDay.today(),
                weekByDay.nextTraining(),
                usualStartTime(sessions, zoneId, lookbackStart, referenceDate),
                previousWeekSameDays.sessions,
                previousWeekSameDays.minutes,
                cardFacts.lifts().stream().map(WeeklyTrainingInsightServiceImpl::toLiftChange).toList(),
                cardFacts.activities().stream()
                        .map(activity -> new ActivitySummaryView(activity.key(), activity.name(), activity.summary()))
                        .toList());
    }

    /** A main lift's first and latest estimated best single in the card's 28 days. */
    static LiftChangeView toLiftChange(TrainingProgressView.Lift lift) {
        List<SeriesPoint> points = lift.e1rm();
        return new LiftChangeView(lift.exerciseId(), lift.name(), points.get(0).value(),
                points.get(points.size() - 1).value(), lift.changePct());
    }

    /** The usual local start time over the 28-day lookback, from the sessions already loaded (no
     * extra query). Sessions are picked by their start instant, in the request's timezone. */
    private String usualStartTime(List<DatedSession> sessions,
                                  ZoneId zoneId,
                                  LocalDate lookbackStart,
                                  LocalDate referenceDate) {
        List<Instant> starts = new ArrayList<>();
        for (DatedSession dated : sessions) {
            Instant startedAt = dated.session().getStartedAt();
            if (startedAt == null) {
                continue;
            }
            LocalDate startDate = startedAt.atZone(zoneId).toLocalDate();
            if (!startDate.isBefore(lookbackStart) && !startDate.isAfter(referenceDate)) {
                starts.add(startedAt);
            }
        }
        return UsualStartTime.compute(starts, zoneId);
    }

    /** Resolves the day-by-day breakdown, today's detail and the next training day. Loads its own,
     * wider window of sessions (both completed and in-progress) in a single query, separate from
     * the completed-only window {@link #loadSessions} uses for volume and muscle groups, since
     * neither window is wide enough to cover the other's need. */
    private WeekByDayCalculator.Result computeWeekByDay(UUID userId,
                                                         ZoneId zoneId,
                                                         LocalDate referenceDate,
                                                         LocalDate weekStart,
                                                         WorkoutPlanEntity activePlan) {
        LocalDate habitLookbackStart = weekStart.minusWeeks(HABIT_LOOKBACK_WEEKS);
        LocalDate lastOfKindLookbackStart = referenceDate.minusDays(LAST_OF_KIND_LOOKBACK_DAYS);
        LocalDate windowStart = habitLookbackStart.isBefore(lastOfKindLookbackStart)
                ? habitLookbackStart : lastOfKindLookbackStart;

        List<WeekByDayCalculator.SessionSnapshot> sessions =
                loadDayKindSessions(userId, zoneId, referenceDate, windowStart);
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = buildPlanDaySnapshots(activePlan);

        // The names of the last session of today's kind come from one bounded query, and only when
        // such a session exists.
        return WeekByDayCalculator.compute(sessions, planDays, referenceDate,
                sessionId -> workoutExerciseLogRepository.findExerciseNames(userId, sessionId,
                        PageRequest.of(0, WeekByDayCalculator.MAX_LAST_OF_KIND_EXERCISE_NAMES)));
    }

    private List<WeekByDayCalculator.SessionSnapshot> loadDayKindSessions(UUID userId,
                                                                           ZoneId zoneId,
                                                                           LocalDate referenceDate,
                                                                           LocalDate windowStart) {
        Instant queryFrom = windowStart.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant queryTo = referenceDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<WorkoutSessionEntity> rawSessions = workoutSessionRepository
                .findByUserIdAndStatusInAndStartedAtBetween(userId, DAY_KIND_STATUSES, queryFrom, queryTo);

        List<WeekByDayCalculator.SessionSnapshot> sessions = new ArrayList<>();
        for (WorkoutSessionEntity session : rawSessions) {
            Instant datingInstant = session.getStatus() == WorkoutSessionStatus.COMPLETED
                    ? session.getCompletedAt() : session.getStartedAt();
            if (datingInstant == null) {
                // Defensive: a COMPLETED session always has completedAt in this domain, but a
                // session lacking any dating instant cannot be placed on a day and is skipped.
                continue;
            }
            LocalDate sessionDate = datingInstant.atZone(zoneId).toLocalDate();
            if (sessionDate.isAfter(referenceDate) || sessionDate.isBefore(windowStart)) {
                continue;
            }
            sessions.add(new WeekByDayCalculator.SessionSnapshot(
                    session.getSessionId(),
                    session.getName(),
                    session.getKind(),
                    session.getSportActivity(),
                    session.getStatus(),
                    session.getDurationMinutes(),
                    session.getDistanceKm(),
                    session.getPlanDayId(),
                    session.getStartedAt(),
                    sessionDate));
        }
        return sessions;
    }

    private List<WeekByDayCalculator.PlanDaySnapshot> buildPlanDaySnapshots(WorkoutPlanEntity activePlan) {
        if (activePlan == null || activePlan.getDays().isEmpty()) {
            return List.of();
        }
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = new ArrayList<>();
        for (WorkoutPlanDayEntity day : activePlan.getDays()) {
            int moves = 0;
            for (PlanEntry planExercise : activePlan.getSetEntries()) {
                if (day.getPlanDayId().equals(planDayIdOf(planExercise))) {
                    moves++;
                }
            }
            planDays.add(new WeekByDayCalculator.PlanDaySnapshot(
                    day.getPlanDayId(),
                    day.getDayOfWeek(),
                    day.getKind(),
                    day.getTitle(),
                    day.getSportActivity(),
                    day.getEstimatedDurationMinutes(),
                    moves));
        }
        return planDays;
    }

    private UUID planDayIdOf(PlanEntry planExercise) {
        return planExercise.getPlanDay() == null ? null : planExercise.getPlanDay().getPlanDayId();
    }

    /** The most recently updated ACTIVE plan for this user, or null when there is none. Several
     * ACTIVE plans should not normally exist, but if they do, this is the same one
     * getActivePlan() would return. */
    private WorkoutPlanEntity findActivePlan(UUID userId) {
        List<WorkoutPlanEntity> activePlans = workoutPlanRepository
                .findByUserIdAndStatusOrderByDateTimeUpdatedDesc(userId, WorkoutPlanStatus.ACTIVE);
        return activePlans.isEmpty() ? null : activePlans.get(0);
    }

    private List<DatedSession> loadSessions(UUID userId, ZoneId zoneId, LocalDate referenceDate, LocalDate windowStart) {
        Instant queryFrom = windowStart.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant queryTo = referenceDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        List<WorkoutSessionEntity> rawSessions = workoutSessionRepository.findByUserIdAndStatusAndStartedAtBetween(
                userId, WorkoutSessionStatus.COMPLETED, queryFrom, queryTo);

        List<DatedSession> sessions = new ArrayList<>();
        for (WorkoutSessionEntity session : rawSessions) {
            Instant datingInstant = session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
            if (datingInstant == null) {
                // Defensive: a COMPLETED session always has completedAt in this domain, but a
                // session lacking any dating instant cannot be placed in a week and is skipped.
                continue;
            }
            LocalDate sessionDate = datingInstant.atZone(zoneId).toLocalDate();
            if (sessionDate.isAfter(referenceDate) || sessionDate.isBefore(windowStart)) {
                // The reference day is "today": later sessions and anything outside the fetch
                // window (widened by a day on each side for the instant/local-date conversion)
                // are excluded here, in Java, against the precise local date.
                continue;
            }
            sessions.add(new DatedSession(session, sessionDate, datingInstant));
        }
        sessions.sort(Comparator.comparing(DatedSession::instant).reversed());
        return sessions;
    }

    private List<MuscleGroupInsightView> buildMuscleGroupInsights(WorkoutPlanEntity activePlan,
                                                                   List<DatedSession> sessions,
                                                                   LocalDate referenceDate,
                                                                   LocalDate weekStart) {
        LocalDate lookbackStart = referenceDate.minusDays(LOOKBACK_DAYS - 1L);
        LocalDate yesterday = referenceDate.minusDays(1);

        // sessions is sorted newest-first already; restrict to the 28-day lookback window used
        // for every per-muscle-group signal except the this-week counters, which use weekStart.
        List<DatedSession> lookbackSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(lookbackStart))
                .toList();

        Set<MuscleGroup> activePlanGroups = activePlanMuscleGroups(activePlan);
        Set<MuscleGroup> trainedGroups = EnumSet.noneOf(MuscleGroup.class);
        for (DatedSession dated : lookbackSessions) {
            trainedGroups.addAll(trainedMuscleGroups(dated.session()));
        }

        Set<MuscleGroup> allGroups = EnumSet.noneOf(MuscleGroup.class);
        allGroups.addAll(activePlanGroups);
        allGroups.addAll(trainedGroups);

        List<MuscleGroupInsightView> result = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            if (!allGroups.contains(group)) {
                continue;
            }
            result.add(buildMuscleGroupInsight(group, lookbackSessions, activePlanGroups, referenceDate, weekStart, yesterday));
        }
        return result;
    }

    private MuscleGroupInsightView buildMuscleGroupInsight(MuscleGroup group,
                                                            List<DatedSession> lookbackSessions,
                                                            Set<MuscleGroup> activePlanGroups,
                                                            LocalDate referenceDate,
                                                            LocalDate weekStart,
                                                            LocalDate yesterday) {
        int sessionsThisWeek = 0;
        boolean trainedYesterday = false;
        LocalDate lastTrainedOn = null;
        Set<String> exerciseNames = new LinkedHashSet<>();

        // Ascending (oldest-first) view of this week for a stable "first-seen" exercise name order.
        List<DatedSession> thisWeekAscending = lookbackSessions.stream()
                .filter(dated -> !dated.date().isBefore(weekStart) && !dated.date().isAfter(referenceDate))
                .sorted(Comparator.comparing(DatedSession::instant))
                .toList();

        for (DatedSession dated : thisWeekAscending) {
            if (!trainsGroup(dated.session(), group)) {
                continue;
            }
            sessionsThisWeek++;
            for (SessionEntry log : dated.session().getSetEntries()) {
                if (log.getExercise().getPrimaryMuscleGroup() == group && exerciseNames.size() < MAX_EXERCISE_NAMES) {
                    exerciseNames.add(log.getExerciseNameSnapshot());
                }
            }
        }

        for (DatedSession dated : lookbackSessions) {
            if (!trainsGroup(dated.session(), group)) {
                continue;
            }
            if (lastTrainedOn == null) {
                // lookbackSessions is newest-first, so the first match is the latest date.
                lastTrainedOn = dated.date();
            }
            if (dated.date().isEqual(yesterday)) {
                trainedYesterday = true;
            }
        }

        int consecutiveSessions = 0;
        for (DatedSession dated : lookbackSessions) {
            if (!trainsGroup(dated.session(), group)) {
                break;
            }
            consecutiveSessions++;
        }

        return new MuscleGroupInsightView(
                group,
                sessionsThisWeek,
                activePlanGroups.contains(group),
                trainedYesterday,
                lastTrainedOn,
                consecutiveSessions,
                List.copyOf(exerciseNames));
    }

    private Set<MuscleGroup> activePlanMuscleGroups(WorkoutPlanEntity activePlan) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        if (activePlan == null) {
            return groups;
        }
        activePlan.getSetEntries().forEach(planExercise ->
                groups.add(planExercise.getExercise().getPrimaryMuscleGroup()));
        return groups;
    }

    /** The primary muscle groups of the exercises a session logged, in enum order. Shared with the
     * monthly insight so both describe a session's muscles the same way. */
    static Set<MuscleGroup> trainedMuscleGroups(WorkoutSessionEntity session) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        for (SessionEntry log : session.getSetEntries()) {
            groups.add(log.getExercise().getPrimaryMuscleGroup());
        }
        return groups;
    }

    private boolean trainsGroup(WorkoutSessionEntity session, MuscleGroup group) {
        for (SessionEntry log : session.getSetEntries()) {
            if (log.getExercise().getPrimaryMuscleGroup() == group) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal sumVolume(WorkoutSessionEntity session) {
        BigDecimal total = BigDecimal.ZERO;
        for (SessionEntry log : session.getSetEntries()) {
            if (log.getTotalVolumeKg() != null) {
                total = total.add(log.getTotalVolumeKg());
            }
        }
        return total;
    }

    private BigDecimal scaleKg(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Resolves the caller-supplied IANA zone id, defaulting to UTC; never returns null. Delegates
     * to {@link InsightDates} so this logic is shared, unchanged, with the training page insight. */
    static ZoneId resolveZone(String timezone) {
        return InsightDates.resolveZone(timezone);
    }

    /** The Monday of the week containing the given date. Delegates to {@link InsightDates}. */
    static LocalDate mondayOf(LocalDate date) {
        return InsightDates.mondayOf(date);
    }

    /** A per-request accumulator for a single week's session count, minutes, and volume. */
    private static final class WeeklyTotals {
        private int sessions;
        private int minutes;
        private BigDecimal volumeKg = BigDecimal.ZERO;
        private int sessionsWithVolume;

        void accumulate(int sessionMinutes, BigDecimal sessionVolumeKg) {
            sessions++;
            minutes += sessionMinutes;
            volumeKg = volumeKg.add(sessionVolumeKg);
            if (sessionVolumeKg.compareTo(BigDecimal.ZERO) > 0) {
                sessionsWithVolume++;
            }
        }
    }

    private record DatedSession(WorkoutSessionEntity session, LocalDate date, Instant instant) {
    }
}
