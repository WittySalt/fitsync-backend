package com.fitsync.workout.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.DayKind;
import com.fitsync.workout.domain.DaySource;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;
import com.fitsync.workout.domain.WorkoutSessionStatus;
import com.fitsync.workout.dto.DayInsightView;
import com.fitsync.workout.dto.NextTrainingView;
import com.fitsync.workout.dto.TodayInsightView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct, database-free coverage of the day-kinds rules in {@link WeekByDayCalculator}. The week
 * used throughout is Monday 2026-09-28 to Sunday 2026-10-04.
 */
class WeekByDayCalculatorTests {

    private static final LocalDate MONDAY = LocalDate.parse("2026-09-28");
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);
    private static final LocalDate WEDNESDAY = MONDAY.plusDays(2);
    private static final LocalDate THURSDAY = MONDAY.plusDays(3);
    private static final LocalDate FRIDAY = MONDAY.plusDays(4);
    private static final LocalDate SATURDAY = MONDAY.plusDays(5);
    private static final LocalDate SUNDAY = MONDAY.plusDays(6);

    @Test
    void movedTrainingLeavesTodayFreeAndFindsNextTrainingDay() {
        UUID mondayPlanDayId = UUID.randomUUID();
        UUID thursdayPlanDayId = UUID.randomUUID();
        UUID fridayPlanDayId = UUID.randomUUID();
        UUID saturdayPlanDayId = UUID.randomUUID();
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = List.of(
                planDay(mondayPlanDayId, 1, SessionKind.STRENGTH, "Full body", null, 50, 4),
                planDay(thursdayPlanDayId, 4, SessionKind.CARDIO, "Treadmill run", null, 35, 0),
                planDay(fridayPlanDayId, 5, SessionKind.STRENGTH, "Push", null, 45, 3),
                planDay(saturdayPlanDayId, 6, SessionKind.SPORT, "Badminton match", SportActivity.BADMINTON, 60, 0));

        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.STRENGTH, null, MONDAY, 50, null, mondayPlanDayId));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, planDays, TUESDAY);

        DayInsightView monday = result.days().get(0);
        assertEquals(DayKind.STRENGTH, monday.kind());
        assertEquals(DaySource.DONE, monday.source());
        assertEquals(50, monday.minutes());
        assertTrue(monday.planned().fulfilled());

        DayInsightView tuesday = result.days().get(1);
        assertEquals(DayKind.REST, tuesday.kind());
        assertEquals(DaySource.PLAN, tuesday.source());

        DayInsightView thursday = result.days().get(3);
        assertEquals(DayKind.CARDIO, thursday.kind());
        assertEquals(DaySource.PLAN, thursday.source());

        DayInsightView saturday = result.days().get(5);
        assertEquals(DayKind.SPORT, saturday.kind());
        assertEquals("Badminton", saturday.label());
        assertEquals(DaySource.PLAN, saturday.source());

        DayInsightView sunday = result.days().get(6);
        assertEquals(DayKind.REST, sunday.kind());
        assertEquals(DaySource.PLAN, sunday.source());

        TodayInsightView today = result.today();
        assertEquals(DayKind.REST, today.kind());
        assertTrue(today.carriedOver().isEmpty());

        NextTrainingView nextTraining = result.nextTraining();
        assertEquals(THURSDAY, nextTraining.date());
        assertEquals(DayKind.CARDIO, nextTraining.kind());
        assertEquals("Treadmill run", nextTraining.title());
        assertEquals(35, nextTraining.estimatedMinutes());
    }

    @Test
    void planDayDoneOnAnotherDayOfTheWeekIsRestOnItsOwnDate() {
        UUID mondayPlanDayId = UUID.randomUUID();
        UUID thursdayPlanDayId = UUID.randomUUID();
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = List.of(
                planDay(mondayPlanDayId, 1, SessionKind.STRENGTH, "Full body", null, 50, 4),
                planDay(thursdayPlanDayId, 4, SessionKind.CARDIO, "Treadmill run", null, 35, 0));

        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.CARDIO, null, WEDNESDAY, 30, null, thursdayPlanDayId));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, planDays, THURSDAY);

        DayInsightView wednesday = result.days().get(2);
        assertEquals(DayKind.CARDIO, wednesday.kind());
        assertEquals(DaySource.DONE, wednesday.source());

        DayInsightView thursday = result.days().get(3);
        assertEquals(DayKind.REST, thursday.kind());
        assertEquals(DaySource.PLAN, thursday.source());
        assertTrue(thursday.planned().fulfilled());

        assertEquals(DayKind.REST, result.today().kind());
    }

    @Test
    void unfulfilledPastPlanDayIsCarriedOver() {
        UUID mondayPlanDayId = UUID.randomUUID();
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = List.of(
                planDay(mondayPlanDayId, 1, SessionKind.STRENGTH, "Full body", null, 50, 4));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(List.of(), planDays, TUESDAY);

        DayInsightView monday = result.days().get(0);
        assertEquals(DayKind.STRENGTH, monday.kind());
        assertEquals(DaySource.PLAN, monday.source());
        assertFalse(monday.planned().fulfilled());

        assertEquals(1, result.today().carriedOver().size());
        assertEquals(mondayPlanDayId, result.today().carriedOver().get(0).planDayId());
        assertEquals(1, result.today().carriedOver().get(0).dayOfWeek());
    }

    @Test
    void sessionWithoutPlanDayIdFulfilsOnlyWhenDateAndKindMatch() {
        UUID fridayPlanDayId = UUID.randomUUID();
        UUID saturdayPlanDayId = UUID.randomUUID();
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = List.of(
                planDay(fridayPlanDayId, 5, SessionKind.STRENGTH, "Push", null, 45, 3),
                planDay(saturdayPlanDayId, 6, SessionKind.SPORT, "Badminton match", SportActivity.BADMINTON, 60, 0));

        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.STRENGTH, null, FRIDAY, 45, null, null),
                completedSession(SessionKind.CARDIO, null, SATURDAY, 30, null, null));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, planDays, SUNDAY);

        assertTrue(result.days().get(4).planned().fulfilled(), "same kind, same date without a planDayId fulfils it");
        assertFalse(result.days().get(5).planned().fulfilled(), "a different kind does not fulfil it");
    }

    @Test
    void multipleCompletedSessionsInADaySumMinutesAndPickTheLongestKind() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.MOBILITY, null, WEDNESDAY, 20, null, null),
                completedSession(SessionKind.STRENGTH, null, WEDNESDAY, 50, null, null));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), WEDNESDAY);

        DayInsightView wednesday = result.days().get(2);
        assertEquals(DayKind.STRENGTH, wednesday.kind());
        assertEquals(70, wednesday.minutes());
        assertEquals(2, wednesday.sessions().size());
    }

    @Test
    void inProgressSessionTodayIsReportedAsInProgress() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                inProgressSession(SessionKind.CARDIO, WEDNESDAY));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), WEDNESDAY);

        DayInsightView wednesday = result.days().get(2);
        assertEquals(DayKind.CARDIO, wednesday.kind());
        assertEquals(DaySource.IN_PROGRESS, wednesday.source());
        assertNull(wednesday.minutes());
    }

    @Test
    void habitNeedsThreeOfFourMatchingWeeksAndFallsBackWithoutThem() {
        // Three matching Thursdays out of the last four weeks is enough for a cardio habit.
        List<WeekByDayCalculator.SessionSnapshot> threeOfFour = List.of(
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(1), 30, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(2), 30, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(4), 30, null, null));
        WeekByDayCalculator.Result habitResult = WeekByDayCalculator.compute(threeOfFour, List.of(), MONDAY);
        assertEquals(DayKind.CARDIO, habitResult.days().get(3).kind());
        assertEquals(DaySource.HABIT, habitResult.days().get(3).source());

        // Two matching Thursdays is not enough: no habit, so Thursday (after "today" = Monday)
        // falls back to the open state.
        List<WeekByDayCalculator.SessionSnapshot> twoOfFour = List.of(
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(1), 30, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(2), 30, null, null));
        WeekByDayCalculator.Result noHabitResult = WeekByDayCalculator.compute(twoOfFour, List.of(), MONDAY);
        assertEquals(DayKind.OPEN, noHabitResult.days().get(3).kind());
        assertEquals(DaySource.NONE, noHabitResult.days().get(3).source());
    }

    @Test
    void restHabitNeedsThreeEmptyWeeksAndAFloorOfFourSessionsOverall() {
        // Three of the four Thursdays have nothing, and (with a session on another weekday plus
        // one Thursday) the four-week window holds four completed sessions overall: the single
        // Thursday session is not enough to form its own habit (only one of four weeks), so the
        // habit falls through to rest.
        List<WeekByDayCalculator.SessionSnapshot> sessions = new ArrayList<>(List.of(
                completedSession(SessionKind.STRENGTH, null, THURSDAY.minusWeeks(2).minusDays(1), 40, null, null),
                completedSession(SessionKind.STRENGTH, null, THURSDAY.minusWeeks(3).minusDays(1), 40, null, null),
                completedSession(SessionKind.STRENGTH, null, THURSDAY.minusWeeks(4).minusDays(1), 40, null, null)));
        sessions.add(completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(1), 30, null, null));
        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), MONDAY);
        assertEquals(DayKind.REST, result.days().get(3).kind());
        assertEquals(DaySource.HABIT, result.days().get(3).source());

        // Drop below four total completed sessions in the lookback: no rest habit is declared, so
        // an otherwise historyless weekday falls back to the plain open/rest state.
        List<WeekByDayCalculator.SessionSnapshot> tooFewSessions = List.of(
                completedSession(SessionKind.STRENGTH, null, THURSDAY.minusWeeks(2).minusDays(1), 40, null, null));
        WeekByDayCalculator.Result sparse = WeekByDayCalculator.compute(tooFewSessions, List.of(), MONDAY);
        assertEquals(DayKind.OPEN, sparse.days().get(3).kind());
    }

    @Test
    void noHistoryAtAllIsOpenForTodayAndLaterAndRestForEarlierDaysOfTheWeek() {
        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(List.of(), List.of(), WEDNESDAY);

        assertEquals(DayKind.REST, result.days().get(0).kind());
        assertEquals(DaySource.NONE, result.days().get(0).source());
        assertEquals(DayKind.OPEN, result.days().get(2).kind());
        assertEquals(DaySource.NONE, result.days().get(2).source());
        assertEquals(DayKind.OPEN, result.days().get(6).kind());
    }

    @Test
    void flatPlanLeavesEveryDayUnplannedSoHabitsDecide() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(1), 30, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(2), 30, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusWeeks(3), 30, null, null));

        // A flat plan is represented the same way as no plan at all: an empty day list.
        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), MONDAY);

        for (DayInsightView day : result.days()) {
            assertNull(day.planned());
        }
        assertEquals(DayKind.CARDIO, result.days().get(3).kind());
        assertEquals(DaySource.HABIT, result.days().get(3).source());
    }

    @Test
    void lastOfKindReportsPaceWithinNinetyDaysAndMatchesSportActivity() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = new ArrayList<>();
        sessions.add(completedSession(SessionKind.CARDIO, null, THURSDAY, 35, null, null));
        sessions.add(completedSession(SessionKind.CARDIO, null, THURSDAY.minusDays(3), 50, new BigDecimal("10.00"), null));
        sessions.add(completedSession(SessionKind.CARDIO, null, THURSDAY.minusDays(30), 40, null, null));
        sessions.add(completedSession(SessionKind.CARDIO, null, THURSDAY.minusDays(95), 20, new BigDecimal("5.00"), null));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), THURSDAY);

        assertEquals(THURSDAY.minusDays(3), result.today().lastOfKind().date());
        assertEquals(50, result.today().lastOfKind().minutes());
        assertEquals(0, new BigDecimal("10.00").compareTo(result.today().lastOfKind().distanceKm()));
        assertEquals(300, result.today().lastOfKind().paceSecondsPerKm());
    }

    @Test
    void lastOfKindWithoutDistanceHasNoPace() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.CARDIO, null, THURSDAY, 35, null, null),
                completedSession(SessionKind.CARDIO, null, THURSDAY.minusDays(2), 40, null, null));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), THURSDAY);

        assertNull(result.today().lastOfKind().paceSecondsPerKm());
    }

    @Test
    void lastOfKindForSportMatchesOnlyTheSameActivity() {
        List<WeekByDayCalculator.SessionSnapshot> sessions = List.of(
                completedSession(SessionKind.SPORT, SportActivity.BADMINTON, THURSDAY, 60, null, null),
                completedSession(SessionKind.SPORT, SportActivity.TENNIS, THURSDAY.minusDays(2), 45, null, null),
                completedSession(SessionKind.SPORT, SportActivity.BADMINTON, THURSDAY.minusDays(9), 55, null, null));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(sessions, List.of(), THURSDAY);

        assertEquals(THURSDAY.minusDays(9), result.today().lastOfKind().date());
    }

    @Test
    void nextTrainingCanReachIntoNextWeek() {
        UUID mondayPlanDayId = UUID.randomUUID();
        List<WeekByDayCalculator.PlanDaySnapshot> planDays = List.of(
                planDay(mondayPlanDayId, 1, SessionKind.STRENGTH, "Full body", null, 50, 4));

        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(List.of(), planDays, SUNDAY);

        assertEquals(MONDAY.plusWeeks(1), result.nextTraining().date());
        assertEquals(DayKind.STRENGTH, result.nextTraining().kind());
        assertEquals("Full body", result.nextTraining().title());
    }

    @Test
    void nextTrainingIsNullWhenNothingIsScheduledOrHabitual() {
        WeekByDayCalculator.Result result = WeekByDayCalculator.compute(List.of(), List.of(), MONDAY);

        assertNull(result.nextTraining());
    }

    private static WeekByDayCalculator.PlanDaySnapshot planDay(UUID planDayId,
                                                               int dayOfWeek,
                                                               SessionKind kind,
                                                               String title,
                                                               SportActivity sportActivity,
                                                               Integer estimatedMinutes,
                                                               int moves) {
        return new WeekByDayCalculator.PlanDaySnapshot(planDayId, dayOfWeek, kind, title, sportActivity, estimatedMinutes, moves);
    }

    private static WeekByDayCalculator.SessionSnapshot completedSession(SessionKind kind,
                                                                        SportActivity sportActivity,
                                                                        LocalDate date,
                                                                        int durationMinutes,
                                                                        BigDecimal distanceKm,
                                                                        UUID planDayId) {
        Instant startedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        return new WeekByDayCalculator.SessionSnapshot(UUID.randomUUID(), "Session", kind, sportActivity,
                WorkoutSessionStatus.COMPLETED, durationMinutes, distanceKm, planDayId, startedAt, date);
    }

    private static WeekByDayCalculator.SessionSnapshot inProgressSession(SessionKind kind, LocalDate date) {
        Instant startedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        return new WeekByDayCalculator.SessionSnapshot(UUID.randomUUID(), "Session", kind, null,
                WorkoutSessionStatus.IN_PROGRESS, null, null, null, startedAt, date);
    }
}
