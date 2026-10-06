package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.InsightPeriod;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.dto.CardioInsightView;
import com.fitsync.workout.dto.ExerciseTopWeightRow;
import com.fitsync.workout.dto.LiftProgressView;
import com.fitsync.workout.dto.MobilityInsightView;
import com.fitsync.workout.dto.MostTrainedView;
import com.fitsync.workout.dto.SportActivityInsightView;
import com.fitsync.workout.dto.SportInsightView;
import com.fitsync.workout.dto.TrainingPageInsightView;
import com.fitsync.workout.dto.TrainingRecordView;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrainingPageInsightServiceImpl implements TrainingPageInsightService {

    /** The lift-progress lookback window: 28 days ending on (and including) the reference date. */
    static final int LIFTS_LOOKBACK_DAYS = 28;

    /** The "earlier window" used for the previous-period comparison is 7 days before periodStart. */
    static final int EARLIER_WINDOW_DAYS = 7;

    /** How many completed sessions the current week needs before it is the week described. */
    static final int MIN_SESSIONS_FOR_CURRENT_WEEK = 3;

    /** mostTrained.exerciseNames is capped so the payload cannot grow unbounded. */
    private static final int MAX_MOST_TRAINED_NAMES = 3;

    /** lifts is capped at the 3 largest moves, per the endpoint's specification. */
    private static final int MAX_LIFTS = 3;

    private final Clock clock;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;

    public TrainingPageInsightServiceImpl(Clock clock,
                                          WorkoutSessionRepository workoutSessionRepository,
                                          WorkoutExerciseLogRepository workoutExerciseLogRepository) {
        this.clock = clock;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingPageInsightView getTrainingPageInsight(CurrentUserPrincipal currentUser,
                                                           String timezone,
                                                           LocalDate date) {
        return getTrainingPageInsight(currentUser, timezone, date, false);
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingPageInsightView getTrainingPageInsight(CurrentUserPrincipal currentUser,
                                                           String timezone,
                                                           LocalDate date,
                                                           boolean thisWeekOnly) {
        ZoneId zoneId = InsightDates.resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        LocalDate referenceDate = date != null ? date : today;

        LocalDate weekStart = InsightDates.mondayOf(referenceDate);
        LocalDate previousWeekStart = weekStart.minusWeeks(1);
        LocalDate previousWeekEnd = weekStart.minusDays(1);

        // A single widened window covers every date range this endpoint needs: the current week,
        // the previous week (for period detection and its "earlier window" comparison), and the
        // 28-day lift-progress lookback. Because weekStart <= referenceDate <= weekStart + 6, the
        // 28-day lookback start (referenceDate - 27) always falls on or before the earliest
        // possible earlier-window start (previousWeekStart - 7), so the lookback start alone is a
        // sufficient lower bound; sessions are then classified into each narrower range in Java.
        LocalDate windowStart = referenceDate.minusDays(LIFTS_LOOKBACK_DAYS - 1L);
        List<DatedSession> sessions = loadSessions(currentUser.userId(), zoneId, referenceDate, windowStart);

        long thisWeekSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(weekStart) && !dated.date().isAfter(referenceDate))
                .count();
        boolean hasPreviousWeekSession = sessions.stream()
                .anyMatch(dated -> !dated.date().isBefore(previousWeekStart) && !dated.date().isAfter(previousWeekEnd));

        // A week is described once most of it has happened. On a Tuesday with one session the
        // current week says almost nothing, so the last full week is described until the current
        // one has enough sessions. A user whose first week this is has no last week to fall back
        // on, and the current week is described however little it holds.
        InsightPeriod period;
        LocalDate periodStart;
        LocalDate periodEnd;
        if (thisWeekOnly) {
            // The coach readings describe the current week so far, however little it holds.
            period = thisWeekSessions > 0 ? InsightPeriod.THIS_WEEK : InsightPeriod.NONE;
            periodStart = thisWeekSessions > 0 ? weekStart : null;
            periodEnd = thisWeekSessions > 0 ? referenceDate : null;
        } else if (thisWeekSessions >= MIN_SESSIONS_FOR_CURRENT_WEEK
                || (thisWeekSessions > 0 && !hasPreviousWeekSession)) {
            period = InsightPeriod.THIS_WEEK;
            periodStart = weekStart;
            periodEnd = referenceDate;
        } else if (hasPreviousWeekSession) {
            period = InsightPeriod.PREVIOUS_WEEK;
            periodStart = previousWeekStart;
            periodEnd = previousWeekEnd;
        } else {
            period = InsightPeriod.NONE;
            periodStart = null;
            periodEnd = null;
        }

        if (period == InsightPeriod.NONE) {
            return new TrainingPageInsightView(
                    zoneId.getId(), referenceDate, InsightPeriod.NONE, null, null,
                    0, 0, null, null, null, null, null, null, List.of(), null, null, null);
        }

        LocalDate earlierStart = periodStart.minusDays(EARLIER_WINDOW_DAYS);
        LocalDate earlierEnd = periodStart.minusDays(1);
        LocalDate liftsWindowStart = referenceDate.minusDays(LIFTS_LOOKBACK_DAYS - 1L);

        LocalDate finalPeriodStart = periodStart;
        LocalDate finalPeriodEnd = periodEnd;
        List<DatedSession> periodSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(finalPeriodStart) && !dated.date().isAfter(finalPeriodEnd))
                .toList();
        List<DatedSession> earlierSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(earlierStart) && !dated.date().isAfter(earlierEnd))
                .toList();
        List<DatedSession> liftsWindowSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(liftsWindowStart) && !dated.date().isAfter(referenceDate))
                .toList();

        WindowTotals periodTotals = computeTotals(periodSessions);
        WindowTotals earlierTotals = computeTotals(earlierSessions);

        TrainingRecordView record = computeRecord(currentUser.userId(), zoneId, periodSessions, periodStart);
        MostTrainedView mostTrained = computeMostTrained(periodSessions);
        List<LiftProgressView> lifts = computeLifts(liftsWindowSessions);
        CardioInsightView cardio = computeCardio(periodSessions, earlierSessions);
        MobilityInsightView mobility = computeMobility(periodSessions, earlierSessions);
        SportInsightView sport = computeSport(periodSessions);

        return new TrainingPageInsightView(
                zoneId.getId(),
                referenceDate,
                period,
                periodStart,
                periodEnd,
                periodTotals.sessions,
                periodTotals.liftingSessions,
                periodTotals.volumePerSession(),
                earlierTotals.volumePerSession(),
                periodTotals.minutesPerLiftingSession(),
                earlierTotals.minutesPerLiftingSession(),
                record,
                mostTrained,
                lifts,
                cardio,
                mobility,
                sport);
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
                // session lacking any dating instant cannot be placed on a local date and is skipped.
                continue;
            }
            LocalDate sessionDate = datingInstant.atZone(zoneId).toLocalDate();
            if (sessionDate.isAfter(referenceDate) || sessionDate.isBefore(windowStart)) {
                // The fetch window is widened by a day on each side for the instant/local-date
                // conversion; the precise local-date bounds are enforced here in Java.
                continue;
            }
            sessions.add(new DatedSession(session, sessionDate, datingInstant));
        }
        sessions.sort(Comparator.comparing(DatedSession::instant));
        return sessions;
    }

    private WindowTotals computeTotals(List<DatedSession> sessions) {
        WindowTotals totals = new WindowTotals();
        totals.sessions = sessions.size();
        for (DatedSession dated : sessions) {
            BigDecimal volume = sumVolume(dated.session());
            if (volume.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            totals.liftingSessions++;
            totals.liftingVolumeKg = totals.liftingVolumeKg.add(volume);
            Integer minutes = dated.session().getDurationMinutes();
            if (minutes != null) {
                totals.minutesSum += minutes;
                totals.minutesCount++;
            }
        }
        return totals;
    }

    /** A personal best in top set weight reached inside the period, versus the user's history
     * before periodStart. Only the exercises actually logged in the period are candidates. */
    private TrainingRecordView computeRecord(UUID userId, ZoneId zoneId, List<DatedSession> periodSessions, LocalDate periodStart) {
        Map<UUID, ExerciseBest> periodBests = new LinkedHashMap<>();
        for (DatedSession dated : periodSessions) {
            for (SessionEntry log : dated.session().getSetEntries()) {
                BigDecimal topSet = topSetWeight(log);
                if (topSet == null) {
                    continue;
                }
                UUID exerciseId = log.getExercise().getExerciseId();
                ExerciseBest existing = periodBests.get(exerciseId);
                // periodSessions is sorted ascending by instant, so only replacing on a strictly
                // greater weight keeps achievedOn as the first date the best weight was reached.
                if (existing == null || topSet.compareTo(existing.weightKg) > 0) {
                    periodBests.put(exerciseId, new ExerciseBest(topSet, dated.date(), log.getExerciseNameSnapshot()));
                }
            }
        }
        if (periodBests.isEmpty()) {
            return null;
        }

        Instant beforeInstant = periodStart.atStartOfDay(zoneId).toInstant();
        List<ExerciseTopWeightRow> historyRows = workoutExerciseLogRepository.findMaxSetWeightPerExerciseBeforeStartedAt(
                userId, WorkoutSessionStatus.COMPLETED, WorkoutSetStatus.SKIPPED, beforeInstant);
        Map<UUID, BigDecimal> earlierBests = new HashMap<>();
        for (ExerciseTopWeightRow row : historyRows) {
            earlierBests.put(row.exerciseId(), row.maxWeightKg());
        }

        List<RecordCandidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, ExerciseBest> entry : periodBests.entrySet()) {
            BigDecimal earlierBest = earlierBests.get(entry.getKey());
            if (earlierBest == null) {
                // No history before the period for this exercise: it cannot be a record.
                continue;
            }
            ExerciseBest periodBest = entry.getValue();
            if (periodBest.weightKg.compareTo(earlierBest) <= 0) {
                continue;
            }
            BigDecimal increase = periodBest.weightKg.subtract(earlierBest);
            candidates.add(new RecordCandidate(increase, periodBest.achievedOn, periodBest.exerciseName,
                    new TrainingRecordView(periodBest.exerciseName, scale(periodBest.weightKg), periodBest.achievedOn, scale(earlierBest))));
        }
        if (candidates.isEmpty()) {
            return null;
        }

        // Largest increase wins; ties go to the latest achievedOn, then to the exercise name in
        // alphabetical order (reverseOrder on the name so the max-element pick favours "A" over "B").
        Comparator<RecordCandidate> comparator = Comparator
                .comparing((RecordCandidate c) -> c.increase)
                .thenComparing(c -> c.achievedOn)
                .thenComparing((RecordCandidate c) -> c.exerciseName, Comparator.reverseOrder());
        return candidates.stream().max(comparator).map(c -> c.view).orElse(null);
    }

    /** The muscle group trained by the most distinct sessions in the period. */
    private MostTrainedView computeMostTrained(List<DatedSession> periodSessions) {
        Map<MuscleGroup, Integer> sessionCounts = new EnumMap<>(MuscleGroup.class);
        Map<MuscleGroup, LinkedHashSet<String>> namesByGroup = new EnumMap<>(MuscleGroup.class);
        boolean anyExerciseLog = false;

        for (DatedSession dated : periodSessions) {
            Set<MuscleGroup> groupsInSession = EnumSet.noneOf(MuscleGroup.class);
            for (SessionEntry log : dated.session().getSetEntries()) {
                anyExerciseLog = true;
                MuscleGroup group = log.getExercise().getPrimaryMuscleGroup();
                groupsInSession.add(group);
                LinkedHashSet<String> names = namesByGroup.computeIfAbsent(group, g -> new LinkedHashSet<>());
                if (names.size() < MAX_MOST_TRAINED_NAMES) {
                    names.add(log.getExerciseNameSnapshot());
                }
            }
            for (MuscleGroup group : groupsInSession) {
                sessionCounts.merge(group, 1, Integer::sum);
            }
        }

        if (!anyExerciseLog) {
            return null;
        }

        MuscleGroup best = null;
        int bestCount = -1;
        for (MuscleGroup group : MuscleGroup.values()) {
            // Iterating in enum declaration order and requiring a strictly greater count makes
            // ties resolve to the earlier-declared muscle group, per the endpoint's specification.
            Integer count = sessionCounts.get(group);
            if (count != null && count > bestCount) {
                bestCount = count;
                best = group;
            }
        }
        if (best == null) {
            return null;
        }
        return new MostTrainedView(best, bestCount, List.copyOf(namesByGroup.getOrDefault(best, new LinkedHashSet<>())));
    }

    /** Top-set weight progress over the 28-day lifts window, for exercises logged with a weight in
     * at least two different completed sessions in that window and whose weight actually changed. */
    private List<LiftProgressView> computeLifts(List<DatedSession> liftsWindowSessions) {
        Map<UUID, List<ExercisePoint>> pointsByExercise = new LinkedHashMap<>();
        Map<UUID, String> namesByExercise = new HashMap<>();

        for (DatedSession dated : liftsWindowSessions) {
            for (SessionEntry log : dated.session().getSetEntries()) {
                BigDecimal topSet = topSetWeight(log);
                if (topSet == null) {
                    continue;
                }
                UUID exerciseId = log.getExercise().getExerciseId();
                pointsByExercise.computeIfAbsent(exerciseId, id -> new ArrayList<>())
                        .add(new ExercisePoint(topSet, dated.date()));
                namesByExercise.putIfAbsent(exerciseId, log.getExerciseNameSnapshot());
            }
        }

        List<LiftCandidate> candidates = new ArrayList<>();
        for (Map.Entry<UUID, List<ExercisePoint>> entry : pointsByExercise.entrySet()) {
            List<ExercisePoint> points = entry.getValue();
            if (points.size() < 2) {
                continue;
            }
            // liftsWindowSessions is sorted ascending by instant, so the first and last points are
            // the earliest and latest sessions in which this exercise was logged with a weight.
            ExercisePoint from = points.get(0);
            ExercisePoint to = points.get(points.size() - 1);
            if (to.weightKg.compareTo(from.weightKg) == 0) {
                continue;
            }
            BigDecimal change = to.weightKg.subtract(from.weightKg).abs();
            candidates.add(new LiftCandidate(change, namesByExercise.get(entry.getKey()), from, to));
        }

        candidates.sort(Comparator.comparing((LiftCandidate c) -> c.change).reversed()
                .thenComparing(c -> c.exerciseName));

        return candidates.stream()
                .limit(MAX_LIFTS)
                .map(c -> new LiftProgressView(c.exerciseName, scale(c.from.weightKg), c.from.date, scale(c.to.weightKg), c.to.date))
                .toList();
    }

    /** The cardio group: null when the period has no cardio session, whatever the earlier week
     * holds. */
    private CardioInsightView computeCardio(List<DatedSession> periodSessions, List<DatedSession> earlierSessions) {
        List<WorkoutSessionEntity> periodCardio = sessionsOfKind(periodSessions, SessionKind.CARDIO);
        if (periodCardio.isEmpty()) {
            return null;
        }
        List<WorkoutSessionEntity> earlierCardio = sessionsOfKind(earlierSessions, SessionKind.CARDIO);
        CardioTotals periodTotals = computeCardioTotals(periodCardio);
        CardioTotals earlierTotals = computeCardioTotals(earlierCardio);
        return new CardioInsightView(
                periodCardio.size(),
                earlierCardio.size(),
                periodTotals.distancePerSession(),
                earlierTotals.distancePerSession(),
                periodTotals.paceSecondsPerKm(),
                earlierTotals.paceSecondsPerKm(),
                periodTotals.minutesPerSession(),
                earlierTotals.minutesPerSession());
    }

    private CardioTotals computeCardioTotals(List<WorkoutSessionEntity> cardioSessions) {
        CardioTotals totals = new CardioTotals();
        for (WorkoutSessionEntity session : cardioSessions) {
            BigDecimal distance = session.getDistanceKm();
            if (distance != null) {
                totals.distanceSum = totals.distanceSum.add(distance);
                totals.distanceCount++;
                if (distance.compareTo(BigDecimal.ZERO) > 0) {
                    totals.paceDistanceSum = totals.paceDistanceSum.add(distance);
                    Integer minutes = session.getDurationMinutes();
                    totals.paceDurationSeconds += minutes == null ? 0L : minutes * 60L;
                }
            }
            Integer minutes = session.getDurationMinutes();
            if (minutes != null) {
                totals.minutesSum += minutes;
                totals.minutesCount++;
            }
        }
        return totals;
    }

    /** The mobility group: null when the period has no mobility session. */
    private MobilityInsightView computeMobility(List<DatedSession> periodSessions, List<DatedSession> earlierSessions) {
        List<WorkoutSessionEntity> periodMobility = sessionsOfKind(periodSessions, SessionKind.MOBILITY);
        if (periodMobility.isEmpty()) {
            return null;
        }
        List<WorkoutSessionEntity> earlierMobility = sessionsOfKind(earlierSessions, SessionKind.MOBILITY);
        return new MobilityInsightView(
                periodMobility.size(),
                sumMinutes(periodMobility),
                earlierMobility.size(),
                sumMinutes(earlierMobility));
    }

    /** The sport group: null when the period has no sport session. A sport session without an
     * activity is reported under {@link SportActivity#OTHER}. Activities are sorted by minutes
     * descending, then by label, so the busiest activity leads. */
    private SportInsightView computeSport(List<DatedSession> periodSessions) {
        List<WorkoutSessionEntity> periodSport = sessionsOfKind(periodSessions, SessionKind.SPORT);
        if (periodSport.isEmpty()) {
            return null;
        }
        Map<SportActivity, Integer> sessionsByActivity = new EnumMap<>(SportActivity.class);
        Map<SportActivity, Integer> minutesByActivity = new EnumMap<>(SportActivity.class);
        for (WorkoutSessionEntity session : periodSport) {
            SportActivity activity = session.getSportActivity() == null ? SportActivity.OTHER : session.getSportActivity();
            sessionsByActivity.merge(activity, 1, Integer::sum);
            Integer minutes = session.getDurationMinutes();
            int minutesToAdd = minutes == null ? 0 : minutes.intValue();
            minutesByActivity.merge(activity, minutesToAdd, Integer::sum);
        }
        List<SportActivityInsightView> activities = sessionsByActivity.entrySet().stream()
                .map(entry -> new SportActivityInsightView(
                        entry.getKey(), entry.getKey().label(), entry.getValue(), minutesByActivity.get(entry.getKey())))
                .sorted(Comparator.comparing(SportActivityInsightView::minutes).reversed()
                        .thenComparing(SportActivityInsightView::label))
                .toList();
        return new SportInsightView(periodSport.size(), sumMinutes(periodSport), activities);
    }

    private List<WorkoutSessionEntity> sessionsOfKind(List<DatedSession> sessions, SessionKind kind) {
        return sessions.stream()
                .map(DatedSession::session)
                .filter(session -> session.getKind() == kind)
                .toList();
    }

    private int sumMinutes(List<WorkoutSessionEntity> sessions) {
        int total = 0;
        for (WorkoutSessionEntity session : sessions) {
            Integer minutes = session.getDurationMinutes();
            if (minutes != null) {
                total += minutes;
            }
        }
        return total;
    }

    /** A per-window accumulator for cardio distance, pace and duration. Pace only accumulates over
     * sessions with a strictly positive distance, so a session logged with a distance of zero never
     * divides by zero. */
    private static final class CardioTotals {
        private BigDecimal distanceSum = BigDecimal.ZERO;
        private int distanceCount;
        private BigDecimal paceDistanceSum = BigDecimal.ZERO;
        private long paceDurationSeconds;
        private int minutesSum;
        private int minutesCount;

        BigDecimal distancePerSession() {
            if (distanceCount == 0) {
                return null;
            }
            return distanceSum.divide(BigDecimal.valueOf(distanceCount), 2, RoundingMode.HALF_UP);
        }

        Integer paceSecondsPerKm() {
            if (paceDistanceSum.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return BigDecimal.valueOf(paceDurationSeconds).divide(paceDistanceSum, 0, RoundingMode.HALF_UP).intValueExact();
        }

        Integer minutesPerSession() {
            return TrainingPageInsightServiceImpl.averageMinutes(minutesSum, minutesCount);
        }
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

    /**
     * The heaviest set that was performed. A set counts when it was logged and was not skipped. A
     * session started from a plan carries the plan's target weights on sets that were never done,
     * and those must not read as lifts. The status is not required to be DONE, because imported
     * and seeded sessions are logged without one.
     */
    static BigDecimal topSetWeight(SessionEntry log) {
        BigDecimal max = null;
        for (WorkoutExerciseSetLogEntity set : log.getSets()) {
            BigDecimal weight = set.getWeightKg();
            if (weight == null || !set.isLogged() || set.getStatus() == WorkoutSetStatus.SKIPPED) {
                continue;
            }
            if (max == null || weight.compareTo(max) > 0) {
                max = weight;
            }
        }
        return max;
    }

    static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Total lifting volume divided by the lifting-session count, scale 2 HALF_UP; null when there
     * are no lifting sessions to divide by. A pure function so it is unit-testable without Spring. */
    static BigDecimal volumePerSession(BigDecimal liftingVolumeKg, int liftingSessions) {
        if (liftingSessions == 0) {
            return null;
        }
        return liftingVolumeKg.divide(BigDecimal.valueOf(liftingSessions), 2, RoundingMode.HALF_UP);
    }

    /** The average of minutesSum over minutesCount samples, rounded half up to a whole number;
     * null when there is nothing to average. A pure function so it is unit-testable without Spring. */
    static Integer averageMinutes(int minutesSum, int minutesCount) {
        if (minutesCount == 0) {
            return null;
        }
        BigDecimal average = BigDecimal.valueOf(minutesSum).divide(BigDecimal.valueOf(minutesCount), 0, RoundingMode.HALF_UP);
        return average.intValueExact();
    }

    /** A per-request accumulator for one window's session count, lifting-session volume and
     * duration. Volume/duration are only accumulated from sessions with positive total volume. */
    private static final class WindowTotals {
        private int sessions;
        private int liftingSessions;
        private BigDecimal liftingVolumeKg = BigDecimal.ZERO;
        private int minutesSum;
        private int minutesCount;

        BigDecimal volumePerSession() {
            return TrainingPageInsightServiceImpl.volumePerSession(liftingVolumeKg, liftingSessions);
        }

        Integer minutesPerLiftingSession() {
            return TrainingPageInsightServiceImpl.averageMinutes(minutesSum, minutesCount);
        }
    }

    private record DatedSession(WorkoutSessionEntity session, LocalDate date, Instant instant) {
    }

    private record ExerciseBest(BigDecimal weightKg, LocalDate achievedOn, String exerciseName) {
    }

    private record RecordCandidate(BigDecimal increase, LocalDate achievedOn, String exerciseName, TrainingRecordView view) {
    }

    private record ExercisePoint(BigDecimal weightKg, LocalDate date) {
    }

    private record LiftCandidate(BigDecimal change, String exerciseName, ExercisePoint from, ExercisePoint to) {
    }
}
