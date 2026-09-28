package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
import com.fitsync.workout.entity.WorkoutExerciseLogEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyTrainingInsightServiceImpl implements WeeklyTrainingInsightService {

    /** The muscle-group lookback window: 28 days ending on (and including) the reference date. */
    static final int LOOKBACK_DAYS = 28;

    /** exerciseNames is capped so the payload cannot grow unbounded for a heavily varied week. */
    private static final int MAX_EXERCISE_NAMES = 5;

    private final Clock clock;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutPlanRepository workoutPlanRepository;

    public WeeklyTrainingInsightServiceImpl(Clock clock,
                                            WorkoutSessionRepository workoutSessionRepository,
                                            WorkoutPlanRepository workoutPlanRepository) {
        this.clock = clock;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutPlanRepository = workoutPlanRepository;
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
        BigDecimal weekBeforePreviousKg = BigDecimal.ZERO;

        for (DatedSession dated : sessions) {
            LocalDate sessionDate = dated.date();
            BigDecimal sessionVolume = sumVolume(dated.session());
            int minutes = dated.session().getDurationMinutes() == null ? 0 : dated.session().getDurationMinutes();

            if (!sessionDate.isBefore(weekStart) && !sessionDate.isAfter(referenceDate)) {
                thisWeek.accumulate(minutes, sessionVolume);
            } else if (!sessionDate.isBefore(previousWeekStart) && !sessionDate.isAfter(previousWeekEnd)) {
                previousWeek.accumulate(minutes, sessionVolume);
            } else if (!sessionDate.isBefore(weekBeforePreviousStart) && !sessionDate.isAfter(weekBeforePreviousEnd)) {
                weekBeforePreviousKg = weekBeforePreviousKg.add(sessionVolume);
            }
        }

        WeeklyVolumeView volume = new WeeklyVolumeView(
                scaleKg(thisWeek.volumeKg),
                scaleKg(previousWeek.volumeKg),
                scaleKg(weekBeforePreviousKg),
                thisWeek.sessionsWithVolume,
                previousWeek.sessionsWithVolume);

        List<MuscleGroupInsightView> muscleGroups = buildMuscleGroupInsights(
                currentUser.userId(), sessions, referenceDate, weekStart);

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
                volume);
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

    private List<MuscleGroupInsightView> buildMuscleGroupInsights(UUID userId,
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

        Set<MuscleGroup> activePlanGroups = activePlanMuscleGroups(userId);
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
            for (WorkoutExerciseLogEntity log : dated.session().getExercises()) {
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

    private Set<MuscleGroup> activePlanMuscleGroups(UUID userId) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        List<WorkoutPlanEntity> activePlans = workoutPlanRepository
                .findByUserIdAndStatusOrderByDateTimeUpdatedDesc(userId, WorkoutPlanStatus.ACTIVE);
        if (activePlans.isEmpty()) {
            return groups;
        }
        // Several ACTIVE plans should not normally exist, but if they do, use the same one
        // getActivePlan() would return: the most recently updated.
        WorkoutPlanEntity activePlan = activePlans.get(0);
        activePlan.getExercises().forEach(planExercise ->
                groups.add(planExercise.getExercise().getPrimaryMuscleGroup()));
        return groups;
    }

    private Set<MuscleGroup> trainedMuscleGroups(WorkoutSessionEntity session) {
        Set<MuscleGroup> groups = EnumSet.noneOf(MuscleGroup.class);
        for (WorkoutExerciseLogEntity log : session.getExercises()) {
            groups.add(log.getExercise().getPrimaryMuscleGroup());
        }
        return groups;
    }

    private boolean trainsGroup(WorkoutSessionEntity session, MuscleGroup group) {
        for (WorkoutExerciseLogEntity log : session.getExercises()) {
            if (log.getExercise().getPrimaryMuscleGroup() == group) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal sumVolume(WorkoutSessionEntity session) {
        BigDecimal total = BigDecimal.ZERO;
        for (WorkoutExerciseLogEntity log : session.getExercises()) {
            if (log.getTotalVolumeKg() != null) {
                total = total.add(log.getTotalVolumeKg());
            }
        }
        return total;
    }

    private BigDecimal scaleKg(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Resolves the caller-supplied IANA zone id, defaulting to UTC; never returns null. */
    static ZoneId resolveZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            // The value is left out of the message, so the response never repeats what was sent.
            throw new IllegalArgumentException("The timezone is not a recognised zone id.");
        }
    }

    /** The Monday of the week containing the given date. */
    static LocalDate mondayOf(LocalDate date) {
        return date.minusDays(date.getDayOfWeek().getValue() - 1L);
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
