package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.dto.ExerciseTopWeightRow;
import com.fitsync.workout.dto.MonthlyDayView;
import com.fitsync.workout.dto.MonthlyMuscleView;
import com.fitsync.workout.dto.MonthlyTotalsView;
import com.fitsync.workout.dto.MonthlyTrainingInsightView;
import com.fitsync.workout.dto.MonthlyWeekView;
import com.fitsync.workout.dto.SessionExerciseTopWeightRow;
import com.fitsync.workout.entity.WorkoutSessionEntity;
import com.fitsync.workout.repository.WorkoutExerciseLogRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import com.fitsync.workout.security.CurrentUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the month view of the training page. A request loads the completed sessions of the month
 * and of the month before in one query, the heaviest set of each exercise in each of those
 * sessions in a second, the best weight of each exercise before that range in a third, and the
 * date of the user's first session in a fourth, so the number of queries does not grow with the
 * number of sessions.
 */
@Service
public class MonthlyTrainingInsightServiceImpl implements MonthlyTrainingInsightService {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("uuuu-MM", Locale.ROOT);

    private final Clock clock;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutExerciseLogRepository workoutExerciseLogRepository;

    public MonthlyTrainingInsightServiceImpl(Clock clock,
                                             WorkoutSessionRepository workoutSessionRepository,
                                             WorkoutExerciseLogRepository workoutExerciseLogRepository) {
        this.clock = clock;
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutExerciseLogRepository = workoutExerciseLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MonthlyTrainingInsightView getMonthlyTrainingInsight(CurrentUserPrincipal currentUser,
                                                                String timezone,
                                                                String monthText) {
        ZoneId zoneId = InsightDates.resolveZone(timezone);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zoneId);
        YearMonth month = resolveMonth(monthText, YearMonth.from(today));

        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        LocalDate previousStart = month.minusMonths(1).atDay(1);
        LocalDate previousEnd = monthStart.minusDays(1);
        // Nothing can be completed after today, so the query stops there for the current month.
        LocalDate loadEnd = monthEnd.isAfter(today) ? today : monthEnd;

        UUID userId = currentUser.userId();
        Instant earliest = workoutSessionRepository.findEarliestDatingInstant(userId, WorkoutSessionStatus.COMPLETED);
        LocalDate firstSessionDate = earliest == null ? null : LocalDate.ofInstant(earliest, zoneId);

        List<DatedSession> sessions = loadSessions(userId, zoneId, previousStart, loadEnd);
        Map<LocalDate, Set<UUID>> recordExercisesByDate = sessions.isEmpty()
                ? Map.of()
                : findRecordExercises(userId, zoneId, previousStart, loadEnd, sessions);

        List<DatedSession> monthSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(monthStart) && !dated.date().isAfter(monthEnd))
                .toList();
        List<DatedSession> previousSessions = sessions.stream()
                .filter(dated -> !dated.date().isBefore(previousStart) && !dated.date().isAfter(previousEnd))
                .toList();

        MonthlyTotalsView totals = totalsOf(monthSessions, monthStart, monthEnd, recordExercisesByDate);
        MonthlyTotalsView previousTotals = firstSessionDate == null || !firstSessionDate.isBefore(monthStart)
                ? null
                : totalsOf(previousSessions, previousStart, previousEnd, recordExercisesByDate);

        return new MonthlyTrainingInsightView(
                zoneId.getId(),
                month.format(MONTH_FORMAT),
                monthStart,
                monthEnd,
                today,
                firstSessionDate,
                buildDays(monthSessions, recordExercisesByDate.keySet()),
                buildWeeks(monthSessions, monthStart, monthEnd, today),
                totals,
                previousTotals,
                buildMuscles(monthSessions));
    }

    /** The requested month, or the current one when none is given. A month that does not parse or
     * lies after the current month is rejected, and the offending text is left out of the message. */
    private static YearMonth resolveMonth(String monthText, YearMonth current) {
        if (monthText == null || monthText.isBlank()) {
            return current;
        }
        YearMonth month;
        try {
            month = YearMonth.parse(monthText, MONTH_FORMAT);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("The month must be given as YYYY-MM.");
        }
        if (month.isAfter(current)) {
            throw new IllegalArgumentException("The month cannot be after the current month.");
        }
        return month;
    }

    /** Completed sessions dated inside [start, end] in the caller's zone, oldest first. A session
     * is dated by its completion, falling back to its start, as the weekly insight does. */
    private List<DatedSession> loadSessions(UUID userId, ZoneId zoneId, LocalDate start, LocalDate end) {
        Instant queryFrom = start.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant queryTo = end.plusDays(1).atStartOfDay(zoneId).toInstant();
        List<WorkoutSessionEntity> rawSessions = workoutSessionRepository.findByUserIdAndStatusAndStartedAtBetween(
                userId, WorkoutSessionStatus.COMPLETED, queryFrom, queryTo);

        List<DatedSession> sessions = new ArrayList<>();
        for (WorkoutSessionEntity session : rawSessions) {
            Instant datingInstant = session.getCompletedAt() != null ? session.getCompletedAt() : session.getStartedAt();
            if (datingInstant == null) {
                continue;
            }
            LocalDate date = datingInstant.atZone(zoneId).toLocalDate();
            // The query window is a day wider on each side, so the exact local bounds apply here.
            if (date.isBefore(start) || date.isAfter(end)) {
                continue;
            }
            sessions.add(new DatedSession(session, date, datingInstant));
        }
        sessions.sort(Comparator.comparing(DatedSession::instant));
        return sessions;
    }

    /**
     * The exercises that set a record on each date. A set is a record when it is heavier than the
     * best weight of that exercise in every earlier completed session; an exercise seen for the
     * first time sets the bar and is not a record. The best before the range comes from one
     * grouped query, and the sessions inside the range are walked oldest first so a later session
     * is compared with the earlier ones of the same range too.
     */
    private Map<LocalDate, Set<UUID>> findRecordExercises(UUID userId,
                                                          ZoneId zoneId,
                                                          LocalDate start,
                                                          LocalDate end,
                                                          List<DatedSession> sessions) {
        Instant beforeInstant = start.atStartOfDay(zoneId).toInstant();
        Map<UUID, BigDecimal> bestByExercise = new HashMap<>();
        for (ExerciseTopWeightRow row : workoutExerciseLogRepository.findMaxSetWeightPerExerciseBeforeStartedAt(
                userId, WorkoutSessionStatus.COMPLETED, WorkoutSetStatus.SKIPPED, beforeInstant)) {
            bestByExercise.put(row.exerciseId(), row.maxWeightKg());
        }

        Instant queryFrom = start.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant queryTo = end.plusDays(1).atStartOfDay(zoneId).toInstant();
        Map<UUID, List<SessionExerciseTopWeightRow>> rowsBySession = new HashMap<>();
        for (SessionExerciseTopWeightRow row : workoutExerciseLogRepository.findMaxSetWeightPerSessionExercise(
                userId, WorkoutSessionStatus.COMPLETED, WorkoutSetStatus.SKIPPED, queryFrom, queryTo)) {
            rowsBySession.computeIfAbsent(row.sessionId(), id -> new ArrayList<>()).add(row);
        }

        Map<LocalDate, Set<UUID>> recordExercisesByDate = new HashMap<>();
        for (DatedSession dated : sessions) {
            for (SessionExerciseTopWeightRow row : rowsBySession.getOrDefault(dated.session().getSessionId(), List.of())) {
                BigDecimal earlierBest = bestByExercise.get(row.exerciseId());
                if (earlierBest != null && row.maxWeightKg().compareTo(earlierBest) > 0) {
                    recordExercisesByDate.computeIfAbsent(dated.date(), d -> new HashSet<>()).add(row.exerciseId());
                }
                if (earlierBest == null || row.maxWeightKg().compareTo(earlierBest) > 0) {
                    bestByExercise.put(row.exerciseId(), row.maxWeightKg());
                }
            }
        }
        return recordExercisesByDate;
    }

    /**
     * The headline numbers of the sessions of one month. {@code records} counts the distinct
     * exercise-and-date pairs that set a record, so one exercise improved on two different days
     * counts twice and one improved in two sessions of the same day counts once.
     */
    private MonthlyTotalsView totalsOf(List<DatedSession> sessions,
                                       LocalDate start,
                                       LocalDate end,
                                       Map<LocalDate, Set<UUID>> recordExercisesByDate) {
        int minutes = 0;
        Set<LocalDate> trainedDates = new HashSet<>();
        for (DatedSession dated : sessions) {
            minutes += minutesOf(dated.session());
            trainedDates.add(dated.date());
        }
        int records = 0;
        for (Map.Entry<LocalDate, Set<UUID>> entry : recordExercisesByDate.entrySet()) {
            if (!entry.getKey().isBefore(start) && !entry.getKey().isAfter(end)) {
                records += entry.getValue().size();
            }
        }
        return new MonthlyTotalsView(sessions.size(), minutes, records, longestStreak(trainedDates));
    }

    private static int longestStreak(Set<LocalDate> dates) {
        int longest = 0;
        for (LocalDate date : dates) {
            if (dates.contains(date.minusDays(1))) {
                continue;
            }
            int length = 1;
            while (dates.contains(date.plusDays(length))) {
                length++;
            }
            longest = Math.max(longest, length);
        }
        return longest;
    }

    private List<MonthlyDayView> buildDays(List<DatedSession> monthSessions, Set<LocalDate> recordDays) {
        Map<LocalDate, List<DatedSession>> byDate = new TreeMap<>();
        for (DatedSession dated : monthSessions) {
            byDate.computeIfAbsent(dated.date(), d -> new ArrayList<>()).add(dated);
        }

        List<MonthlyDayView> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DatedSession>> entry : byDate.entrySet()) {
            List<WeekByDayCalculator.SessionSnapshot> snapshots = new ArrayList<>();
            int minutes = 0;
            Set<MuscleGroup> muscles = EnumSet.noneOf(MuscleGroup.class);
            for (DatedSession dated : entry.getValue()) {
                WorkoutSessionEntity session = dated.session();
                snapshots.add(new WeekByDayCalculator.SessionSnapshot(
                        session.getSessionId(), session.getName(), session.getKind(), session.getSportActivity(),
                        session.getStatus(), session.getDurationMinutes(), session.getDistanceKm(),
                        session.getPlanDayId(), session.getStartedAt(), dated.date()));
                minutes += minutesOf(session);
                muscles.addAll(WeeklyTrainingInsightServiceImpl.trainedMuscleGroups(session));
            }
            // Every session here is completed, so a naming session always exists.
            WeekByDayCalculator.SessionSnapshot primary = WeekByDayCalculator.primaryCompletedSession(snapshots)
                    .orElseThrow();
            String label = WeekByDayCalculator.kindLabel(
                    WeekByDayCalculator.trainingKind(primary.kind()), primary.sportActivity());
            days.add(new MonthlyDayView(entry.getKey(), primary.kind(), label, entry.getValue().size(), minutes,
                    List.copyOf(muscles), recordDays.contains(entry.getKey())));
        }
        return days;
    }

    /** Every Monday-to-Sunday week that overlaps the month. The week's own dates are reported,
     * but only sessions on dates inside the month are counted, because the sessions passed in are
     * the month's. */
    private List<MonthlyWeekView> buildWeeks(List<DatedSession> monthSessions,
                                             LocalDate monthStart,
                                             LocalDate monthEnd,
                                             LocalDate today) {
        Map<LocalDate, WeekCounts> countsByWeekStart = new TreeMap<>();
        for (LocalDate weekStart = InsightDates.mondayOf(monthStart); !weekStart.isAfter(monthEnd);
                weekStart = weekStart.plusWeeks(1)) {
            countsByWeekStart.put(weekStart, new WeekCounts());
        }
        for (DatedSession dated : monthSessions) {
            countsByWeekStart.get(InsightDates.mondayOf(dated.date())).add(dated.session());
        }

        List<MonthlyWeekView> weeks = new ArrayList<>();
        for (Map.Entry<LocalDate, WeekCounts> entry : countsByWeekStart.entrySet()) {
            LocalDate weekEnd = entry.getKey().plusDays(6);
            WeekCounts counts = entry.getValue();
            weeks.add(new MonthlyWeekView(entry.getKey(), weekEnd, counts.sessions, counts.minutes,
                    counts.perKind.getOrDefault(SessionKind.STRENGTH, 0),
                    counts.perKind.getOrDefault(SessionKind.CARDIO, 0),
                    counts.perKind.getOrDefault(SessionKind.MOBILITY, 0),
                    counts.perKind.getOrDefault(SessionKind.SPORT, 0),
                    weekEnd.isBefore(today)));
        }
        return weeks;
    }

    private List<MonthlyMuscleView> buildMuscles(List<DatedSession> monthSessions) {
        Map<MuscleGroup, Integer> sessionsByGroup = new EnumMap<>(MuscleGroup.class);
        for (DatedSession dated : monthSessions) {
            for (MuscleGroup group : WeeklyTrainingInsightServiceImpl.trainedMuscleGroups(dated.session())) {
                sessionsByGroup.merge(group, 1, Integer::sum);
            }
        }
        List<MonthlyMuscleView> muscles = new ArrayList<>();
        for (MuscleGroup group : MuscleGroup.values()) {
            Integer count = sessionsByGroup.get(group);
            if (count != null) {
                muscles.add(new MonthlyMuscleView(group, muscleLabel(group), count));
            }
        }
        return muscles;
    }

    /** A readable name for a group, such as "Full body" for FULL_BODY. */
    static String muscleLabel(MuscleGroup group) {
        String words = group.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        return words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private static int minutesOf(WorkoutSessionEntity session) {
        return session.getDurationMinutes() == null ? 0 : session.getDurationMinutes();
    }

    /** A per-week accumulator of session count, minutes and sessions per kind. */
    private static final class WeekCounts {
        private int sessions;
        private int minutes;
        private final Map<SessionKind, Integer> perKind = new EnumMap<>(SessionKind.class);

        void add(WorkoutSessionEntity session) {
            sessions++;
            minutes += minutesOf(session);
            perKind.merge(session.getKind(), 1, Integer::sum);
        }
    }

    private record DatedSession(WorkoutSessionEntity session, LocalDate date, Instant instant) {
    }
}
