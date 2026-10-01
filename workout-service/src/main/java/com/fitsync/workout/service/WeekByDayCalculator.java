package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.dto.CarriedOverView;
import com.fitsync.workout.dto.DayInsightView;
import com.fitsync.workout.dto.DaySessionView;
import com.fitsync.workout.dto.LastOfKindView;
import com.fitsync.workout.dto.NextTrainingView;
import com.fitsync.workout.dto.PlannedDayView;
import com.fitsync.workout.dto.TodayInsightView;

/**
 * Works out the day-by-day shape of a week, today's own detail, and the next training day, from
 * plain, already-loaded data. Kept free of persistence and web concerns so the rules in the
 * day-kinds specification can be exercised directly in a unit test.
 */
final class WeekByDayCalculator {

    /** How many weeks of history the habit rule looks at, and how many of them must agree. */
    private static final int HABIT_LOOKBACK_WEEKS = 4;
    private static final int HABIT_AGREEMENT_THRESHOLD = 3;
    private static final int HABIT_REST_MIN_TOTAL_SESSIONS = 4;
    private static final int LAST_OF_KIND_LOOKBACK_DAYS = 90;
    private static final int MAX_CARRIED_OVER = 3;
    static final int MAX_LAST_OF_KIND_EXERCISE_NAMES = 6;
    private static final int MAX_NEXT_TRAINING_LOOKAHEAD_DAYS = 7;

    private WeekByDayCalculator() {
    }

    /** One already-loaded session, reduced to the fields this calculation needs. {@code
     * localDate} is the day it counts on: a completed session's local completion date, or an
     * in-progress session's local start date. */
    record SessionSnapshot(
            UUID sessionId,
            String name,
            SessionKind kind,
            SportActivity sportActivity,
            WorkoutSessionStatus status,
            Integer durationMinutes,
            BigDecimal distanceKm,
            UUID planDayId,
            Instant startedAt,
            LocalDate localDate) {
    }

    /** One day of the active plan's week, reduced to the fields this calculation needs.
     * {@code moves} is the number of exercises under that day, ignored for a sport day. */
    record PlanDaySnapshot(
            UUID planDayId,
            int dayOfWeek,
            SessionKind kind,
            String title,
            SportActivity sportActivity,
            Integer estimatedMinutes,
            int moves) {
    }

    record Result(List<DayInsightView> days, TodayInsightView today, NextTrainingView nextTraining) {
    }

    /** A day's resolved kind, source and supporting detail; computed once per date of the current
     * week and reused for {@code days}, {@code today} and any next-training candidate that falls
     * inside this same week. */
    private record Determination(
            DayKind kind,
            String label,
            DaySource source,
            Integer minutes,
            SportActivity activity,
            SessionSnapshot primarySession,
            PlannedDayView planned,
            List<DaySessionView> sessionViews) {
    }

    private record HabitResult(DayKind kind, SportActivity activity) {
    }

    static Result compute(List<SessionSnapshot> sessions, List<PlanDaySnapshot> planDays, LocalDate referenceDate) {
        return compute(sessions, planDays, referenceDate, sessionId -> List.of());
    }

    /** {@code exerciseNamesOf} is asked for the names of one session only, the one picked as the
     * last of today's kind, and only when there is one. */
    static Result compute(List<SessionSnapshot> sessions,
                          List<PlanDaySnapshot> planDays,
                          LocalDate referenceDate,
                          Function<UUID, List<String>> exerciseNamesOf) {
        LocalDate weekStart = InsightDates.mondayOf(referenceDate);
        LocalDate weekEnd = weekStart.plusDays(6);
        boolean hasWeek = !planDays.isEmpty();

        Map<Integer, PlanDaySnapshot> planDaysByDayOfWeek = new LinkedHashMap<>();
        for (PlanDaySnapshot planDay : planDays) {
            planDaysByDayOfWeek.put(planDay.dayOfWeek(), planDay);
        }

        FulfilledLookup fulfilled = FulfilledLookup.of(sessions, weekStart, weekEnd);
        Map<Integer, HabitResult> habitCache = new LinkedHashMap<>();

        Map<LocalDate, Determination> determinations = new LinkedHashMap<>();
        List<DayInsightView> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            int dayOfWeek = i + 1;
            List<SessionSnapshot> daySessions = sessionsOn(sessions, date);
            PlanDaySnapshot planDay = hasWeek ? planDaysByDayOfWeek.get(dayOfWeek) : null;
            Determination determination = determine(date, dayOfWeek, daySessions, planDay, hasWeek,
                    fulfilled, referenceDate, habitCache, sessions, weekStart);
            determinations.put(date, determination);
            days.add(new DayInsightView(date, dayOfWeek, determination.kind(), determination.label(),
                    determination.source(), determination.minutes(), determination.sessionViews(),
                    determination.planned()));
        }

        Determination todayDetermination = determinations.get(referenceDate);
        TodayInsightView today = buildToday(referenceDate, todayDetermination, sessions, determinations, weekStart,
                exerciseNamesOf);
        NextTrainingView nextTraining = findNextTraining(referenceDate, weekStart, weekEnd, hasWeek,
                planDaysByDayOfWeek, habitCache, sessions, determinations);

        return new Result(List.copyOf(days), today, nextTraining);
    }

    private static List<SessionSnapshot> sessionsOn(List<SessionSnapshot> sessions, LocalDate date) {
        List<SessionSnapshot> onDate = new ArrayList<>();
        for (SessionSnapshot session : sessions) {
            if (session.localDate().isEqual(date)) {
                onDate.add(session);
            }
        }
        onDate.sort(Comparator.comparing(SessionSnapshot::startedAt));
        return onDate;
    }

    private static Determination determine(LocalDate date,
                                            int dayOfWeek,
                                            List<SessionSnapshot> daySessions,
                                            PlanDaySnapshot planDay,
                                            boolean hasWeek,
                                            FulfilledLookup fulfilled,
                                            LocalDate referenceDate,
                                            Map<Integer, HabitResult> habitCache,
                                            List<SessionSnapshot> allSessions,
                                            LocalDate weekStart) {
        List<DaySessionView> sessionViews = daySessions.stream()
                .map(WeekByDayCalculator::toSessionView)
                .toList();

        PlannedDayView plannedView = null;
        boolean dayFulfilled = false;
        if (planDay != null) {
            dayFulfilled = fulfilled.isFulfilled(planDay, date);
            Integer moves = planDay.kind() == SessionKind.SPORT ? null : planDay.moves();
            plannedView = new PlannedDayView(planDay.planDayId(), planDay.kind(), planDay.title(),
                    planDay.sportActivity(), planDay.estimatedMinutes(), moves, dayFulfilled);
        }

        Optional<SessionSnapshot> primaryCompleted = primaryCompletedSession(daySessions);
        if (primaryCompleted.isPresent()) {
            SessionSnapshot session = primaryCompleted.get();
            DayKind kind = trainingKind(session.kind());
            int totalMinutes = 0;
            boolean anyMinutes = false;
            for (SessionSnapshot completed : completedSessions(daySessions)) {
                if (completed.durationMinutes() != null) {
                    totalMinutes += completed.durationMinutes();
                    anyMinutes = true;
                }
            }
            return new Determination(kind, kindLabel(kind, session.sportActivity()), DaySource.DONE,
                    anyMinutes ? totalMinutes : null, session.sportActivity(), session, plannedView, sessionViews);
        }

        Optional<SessionSnapshot> latestInProgress = latestInProgressSession(daySessions);
        if (latestInProgress.isPresent()) {
            SessionSnapshot session = latestInProgress.get();
            DayKind kind = trainingKind(session.kind());
            return new Determination(kind, kindLabel(kind, session.sportActivity()), DaySource.IN_PROGRESS,
                    null, session.sportActivity(), session, plannedView, sessionViews);
        }

        if (planDay != null && !dayFulfilled) {
            DayKind kind = trainingKind(planDay.kind());
            return new Determination(kind, kindLabel(kind, planDay.sportActivity()), DaySource.PLAN,
                    null, planDay.sportActivity(), null, plannedView, sessionViews);
        }

        if (planDay != null) {
            // Fulfilled elsewhere this week: today's own slot is free.
            return new Determination(DayKind.REST, "Rest", DaySource.PLAN, null, null, null, plannedView, sessionViews);
        }

        if (hasWeek) {
            return new Determination(DayKind.REST, "Rest", DaySource.PLAN, null, null, null, null, sessionViews);
        }

        HabitResult habit = habitCache.computeIfAbsent(dayOfWeek,
                dow -> computeHabit(dow, weekStart, allSessions));
        if (habit != null) {
            String label = kindLabel(habit.kind(), habit.activity());
            return new Determination(habit.kind(), label, DaySource.HABIT, null, habit.activity(), null, null, sessionViews);
        }

        boolean past = date.isBefore(referenceDate);
        DayKind kind = past ? DayKind.REST : DayKind.OPEN;
        String label = past ? "Rest" : "Open";
        return new Determination(kind, label, DaySource.NONE, null, null, null, null, sessionViews);
    }

    private static TodayInsightView buildToday(LocalDate referenceDate,
                                                Determination today,
                                                List<SessionSnapshot> sessions,
                                                Map<LocalDate, Determination> determinations,
                                                LocalDate weekStart,
                                                Function<UUID, List<String>> exerciseNamesOf) {
        UUID planDayId = null;
        String title = null;
        Integer estimatedMinutes = null;
        Integer moves = null;

        if (today.source() == DaySource.PLAN && today.planned() != null && isTraining(today.kind())) {
            PlannedDayView planned = today.planned();
            planDayId = planned.planDayId();
            title = planned.title();
            estimatedMinutes = planned.estimatedMinutes();
            moves = planned.moves();
        } else if ((today.source() == DaySource.DONE || today.source() == DaySource.IN_PROGRESS)
                && today.primarySession() != null) {
            SessionSnapshot session = today.primarySession();
            PlannedDayView matchingPlanDay = session.planDayId() != null && today.planned() != null
                    && today.planned().planDayId() != null && today.planned().planDayId().equals(session.planDayId())
                    ? today.planned()
                    : null;
            if (matchingPlanDay != null) {
                planDayId = matchingPlanDay.planDayId();
                title = matchingPlanDay.title();
                estimatedMinutes = matchingPlanDay.estimatedMinutes();
                moves = matchingPlanDay.moves();
            } else {
                title = session.name();
            }
        }

        LastOfKindView lastOfKind = isTraining(today.kind())
                ? findLastOfKind(referenceDate, today.kind(), today.activity(), sessions, exerciseNamesOf)
                : null;

        List<CarriedOverView> carriedOver = List.of();
        if (today.kind() == DayKind.REST || today.kind() == DayKind.OPEN) {
            carriedOver = collectCarriedOver(referenceDate, weekStart, determinations);
        }

        return new TodayInsightView(referenceDate, today.kind(), today.label(), today.source(),
                planDayId, title, estimatedMinutes, moves, lastOfKind, carriedOver);
    }

    private static List<CarriedOverView> collectCarriedOver(LocalDate referenceDate,
                                                              LocalDate weekStart,
                                                              Map<LocalDate, Determination> determinations) {
        List<CarriedOverView> carriedOver = new ArrayList<>();
        for (LocalDate date = weekStart; date.isBefore(referenceDate); date = date.plusDays(1)) {
            Determination determination = determinations.get(date);
            if (determination == null || determination.planned() == null || determination.planned().fulfilled()) {
                continue;
            }
            PlannedDayView planned = determination.planned();
            int dayOfWeek = date.getDayOfWeek().getValue();
            carriedOver.add(new CarriedOverView(planned.planDayId(), dayOfWeek, planned.kind(), planned.title(),
                    planned.estimatedMinutes()));
            if (carriedOver.size() == MAX_CARRIED_OVER) {
                break;
            }
        }
        return carriedOver;
    }

    private static LastOfKindView findLastOfKind(LocalDate referenceDate,
                                                  DayKind kind,
                                                  SportActivity activity,
                                                  List<SessionSnapshot> sessions,
                                                  Function<UUID, List<String>> exerciseNamesOf) {
        LocalDate earliest = referenceDate.minusDays(LAST_OF_KIND_LOOKBACK_DAYS);
        SessionSnapshot best = null;
        for (SessionSnapshot session : sessions) {
            if (session.status() != WorkoutSessionStatus.COMPLETED) {
                continue;
            }
            if (trainingKind(session.kind()) != kind) {
                continue;
            }
            if (kind == DayKind.SPORT && !Objects.equals(session.sportActivity(), activity)) {
                continue;
            }
            LocalDate date = session.localDate();
            if (!date.isBefore(referenceDate) || date.isBefore(earliest)) {
                continue;
            }
            if (best == null || session.startedAt().isAfter(best.startedAt())) {
                best = session;
            }
        }
        if (best == null) {
            return null;
        }
        Integer paceSecondsPerKm = null;
        if (best.durationMinutes() != null && best.distanceKm() != null
                && best.distanceKm().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalSeconds = BigDecimal.valueOf(best.durationMinutes() * 60L);
            paceSecondsPerKm = totalSeconds.divide(best.distanceKm(), 0, RoundingMode.HALF_UP).intValue();
        }
        List<String> exerciseNames = exerciseNamesOf.apply(best.sessionId()).stream()
                .limit(MAX_LAST_OF_KIND_EXERCISE_NAMES)
                .toList();
        return new LastOfKindView(best.localDate(), best.durationMinutes(), best.distanceKm(), paceSecondsPerKm,
                best.sessionId(), exerciseNames);
    }

    private static NextTrainingView findNextTraining(LocalDate referenceDate,
                                                       LocalDate weekStart,
                                                       LocalDate weekEnd,
                                                       boolean hasWeek,
                                                       Map<Integer, PlanDaySnapshot> planDaysByDayOfWeek,
                                                       Map<Integer, HabitResult> habitCache,
                                                       List<SessionSnapshot> allSessions,
                                                       Map<LocalDate, Determination> determinations) {
        for (int offset = 1; offset <= MAX_NEXT_TRAINING_LOOKAHEAD_DAYS; offset++) {
            LocalDate candidate = referenceDate.plusDays(offset);
            int dayOfWeek = candidate.getDayOfWeek().getValue();

            if (!candidate.isAfter(weekEnd)) {
                Determination determination = determinations.get(candidate);
                if (determination != null && isTraining(determination.kind())) {
                    String title = determination.source() == DaySource.PLAN && determination.planned() != null
                            ? determination.planned().title() : null;
                    Integer estimatedMinutes = determination.source() == DaySource.PLAN && determination.planned() != null
                            ? determination.planned().estimatedMinutes() : null;
                    return new NextTrainingView(candidate, determination.kind(), determination.label(),
                            title, estimatedMinutes);
                }
                continue;
            }

            if (hasWeek) {
                PlanDaySnapshot planDay = planDaysByDayOfWeek.get(dayOfWeek);
                if (planDay != null) {
                    DayKind kind = trainingKind(planDay.kind());
                    return new NextTrainingView(candidate, kind, kindLabel(kind, planDay.sportActivity()),
                            planDay.title(), planDay.estimatedMinutes());
                }
                continue;
            }

            HabitResult habit = habitCache.computeIfAbsent(dayOfWeek,
                    dow -> computeHabit(dow, weekStart, allSessions));
            if (habit != null && isTraining(habit.kind())) {
                return new NextTrainingView(candidate, habit.kind(), kindLabel(habit.kind(), habit.activity()),
                        null, null);
            }
        }
        return null;
    }

    private static HabitResult computeHabit(int dayOfWeek, LocalDate weekStart, List<SessionSnapshot> sessions) {
        Map<SessionKind, Integer> counts = new EnumMap<>(SessionKind.class);
        Map<SessionKind, SessionSnapshot> mostRecentByKind = new EnumMap<>(SessionKind.class);
        int weeksWithNoSession = 0;

        for (int week = 1; week <= HABIT_LOOKBACK_WEEKS; week++) {
            LocalDate date = weekStart.minusWeeks(week).plusDays(dayOfWeek - 1L);
            Optional<SessionSnapshot> rule1 = rule1PrimarySession(sessions, date);
            if (rule1.isEmpty()) {
                weeksWithNoSession++;
                continue;
            }
            SessionSnapshot session = rule1.get();
            counts.merge(session.kind(), 1, Integer::sum);
            SessionSnapshot current = mostRecentByKind.get(session.kind());
            if (current == null || session.startedAt().isAfter(current.startedAt())) {
                mostRecentByKind.put(session.kind(), session);
            }
        }

        for (Map.Entry<SessionKind, Integer> entry : counts.entrySet()) {
            if (entry.getValue() >= HABIT_AGREEMENT_THRESHOLD) {
                SessionSnapshot representative = mostRecentByKind.get(entry.getKey());
                DayKind kind = trainingKind(entry.getKey());
                SportActivity activity = kind == DayKind.SPORT ? representative.sportActivity() : null;
                return new HabitResult(kind, activity);
            }
        }

        if (weeksWithNoSession >= HABIT_AGREEMENT_THRESHOLD
                && totalCompletedInLookback(sessions, weekStart) >= HABIT_REST_MIN_TOTAL_SESSIONS) {
            return new HabitResult(DayKind.REST, null);
        }
        return null;
    }

    private static int totalCompletedInLookback(List<SessionSnapshot> sessions, LocalDate weekStart) {
        LocalDate lookbackStart = weekStart.minusWeeks(HABIT_LOOKBACK_WEEKS);
        LocalDate lookbackEnd = weekStart.minusDays(1);
        int total = 0;
        for (SessionSnapshot session : sessions) {
            if (session.status() != WorkoutSessionStatus.COMPLETED) {
                continue;
            }
            LocalDate date = session.localDate();
            if (!date.isBefore(lookbackStart) && !date.isAfter(lookbackEnd)) {
                total++;
            }
        }
        return total;
    }

    /** The kind a single date resolves to under rule 1 only: the completed session with the
     * longest duration, ties broken by the earliest start. Empty when there is no completed
     * session on that date. */
    private static Optional<SessionSnapshot> rule1PrimarySession(List<SessionSnapshot> sessions, LocalDate date) {
        return primaryCompletedSession(sessionsOn(sessions, date));
    }

    /** The completed session that names a day: the longest one, ties broken by the earliest start. */
    static Optional<SessionSnapshot> primaryCompletedSession(List<SessionSnapshot> daySessions) {
        SessionSnapshot best = null;
        for (SessionSnapshot session : daySessions) {
            if (session.status() != WorkoutSessionStatus.COMPLETED) {
                continue;
            }
            if (best == null || isLonger(session, best)) {
                best = session;
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isLonger(SessionSnapshot candidate, SessionSnapshot current) {
        int candidateMinutes = candidate.durationMinutes() == null ? -1 : candidate.durationMinutes();
        int currentMinutes = current.durationMinutes() == null ? -1 : current.durationMinutes();
        if (candidateMinutes != currentMinutes) {
            return candidateMinutes > currentMinutes;
        }
        return candidate.startedAt().isBefore(current.startedAt());
    }

    private static Optional<SessionSnapshot> latestInProgressSession(List<SessionSnapshot> daySessions) {
        SessionSnapshot latest = null;
        for (SessionSnapshot session : daySessions) {
            if (session.status() != WorkoutSessionStatus.IN_PROGRESS) {
                continue;
            }
            if (latest == null || session.startedAt().isAfter(latest.startedAt())) {
                latest = session;
            }
        }
        return Optional.ofNullable(latest);
    }

    private static List<SessionSnapshot> completedSessions(List<SessionSnapshot> daySessions) {
        return daySessions.stream().filter(s -> s.status() == WorkoutSessionStatus.COMPLETED).toList();
    }

    private static DaySessionView toSessionView(SessionSnapshot session) {
        DayKind kind = trainingKind(session.kind());
        return new DaySessionView(session.sessionId(), session.kind(), kindLabel(kind, session.sportActivity()),
                session.sportActivity(), session.status(), session.durationMinutes(), session.distanceKm(),
                session.planDayId());
    }

    private static boolean isTraining(DayKind kind) {
        return kind == DayKind.STRENGTH || kind == DayKind.CARDIO || kind == DayKind.MOBILITY || kind == DayKind.SPORT;
    }

    static DayKind trainingKind(SessionKind kind) {
        return switch (kind) {
            case STRENGTH -> DayKind.STRENGTH;
            case CARDIO -> DayKind.CARDIO;
            case MOBILITY -> DayKind.MOBILITY;
            case SPORT -> DayKind.SPORT;
        };
    }

    static String kindLabel(DayKind kind, SportActivity activity) {
        return switch (kind) {
            case STRENGTH -> "Strength";
            case CARDIO -> "Cardio";
            case MOBILITY -> "Mobility";
            case SPORT -> activity != null ? activity.label() : "Sport";
            case REST -> "Rest";
            case OPEN -> "Open";
        };
    }

    /** Tells whether a plan day's occurrence this week has already been trained, either through
     * its own {@code planDayId} or, for a session logged without one, by date and kind. */
    private static final class FulfilledLookup {
        private final Set<UUID> completedPlanDayIds;
        private final Map<LocalDate, Set<SessionKind>> kindsWithoutPlanDayIdByDate;

        private FulfilledLookup(Set<UUID> completedPlanDayIds, Map<LocalDate, Set<SessionKind>> kindsWithoutPlanDayIdByDate) {
            this.completedPlanDayIds = completedPlanDayIds;
            this.kindsWithoutPlanDayIdByDate = kindsWithoutPlanDayIdByDate;
        }

        static FulfilledLookup of(List<SessionSnapshot> sessions, LocalDate weekStart, LocalDate weekEnd) {
            Set<UUID> completedPlanDayIds = new HashSet<>();
            Map<LocalDate, Set<SessionKind>> kindsByDate = new LinkedHashMap<>();
            for (SessionSnapshot session : sessions) {
                if (session.status() != WorkoutSessionStatus.COMPLETED) {
                    continue;
                }
                LocalDate date = session.localDate();
                if (date.isBefore(weekStart) || date.isAfter(weekEnd)) {
                    continue;
                }
                if (session.planDayId() != null) {
                    completedPlanDayIds.add(session.planDayId());
                } else {
                    kindsByDate.computeIfAbsent(date, d -> EnumSet.noneOf(SessionKind.class)).add(session.kind());
                }
            }
            return new FulfilledLookup(completedPlanDayIds, kindsByDate);
        }

        boolean isFulfilled(PlanDaySnapshot planDay, LocalDate ownDate) {
            if (completedPlanDayIds.contains(planDay.planDayId())) {
                return true;
            }
            Set<SessionKind> kindsOnDate = kindsWithoutPlanDayIdByDate.get(ownDate);
            return kindsOnDate != null && kindsOnDate.contains(planDay.kind());
        }
    }
}
