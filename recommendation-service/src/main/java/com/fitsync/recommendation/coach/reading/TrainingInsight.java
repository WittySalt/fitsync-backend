package com.fitsync.recommendation.coach.reading;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * workout-service's own training-insight contract, read exactly as it defines it.
 *
 * <p>Every field but the identifying ones is nullable, because the underlying week can be thin: no
 * lifting session, no new record, no dominant muscle group. {@link ReadingFactsBuilder} is what
 * turns "nullable" into "included only when present"; this record just carries what came back.
 *
 * <p>Unknown properties are ignored rather than rejected. workout-service owns this contract and can
 * add fields to it; a reading built from a strict subset of them should not start failing the day it
 * does.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingInsight(
        String timezone,
        LocalDate referenceDate,
        String period,
        LocalDate periodStart,
        LocalDate periodEnd,
        Integer sessions,
        Integer liftingSessions,
        BigDecimal volumePerSessionKg,
        BigDecimal earlierVolumePerSessionKg,
        Integer minutesPerLiftingSession,
        Integer earlierMinutesPerLiftingSession,
        @JsonProperty("record") RecordInfo personalRecord,
        MostTrainedInfo mostTrained,
        List<LiftProgress> lifts,
        CardioInfo cardio,
        MobilityInfo mobility,
        SportInfo sport,
        List<BestSingle> bestSingles) {

    /** period value meaning "nothing recent enough to talk about". */
    public static final String PERIOD_NONE = "NONE";

    public TrainingInsight {
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
        bestSingles = bestSingles == null ? List.of() : List.copyOf(bestSingles);
    }

    /** The shape without the main lifts' estimated best singles, which the weekly insight carries. */
    public TrainingInsight(String timezone, LocalDate referenceDate, String period, LocalDate periodStart,
                           LocalDate periodEnd, Integer sessions, Integer liftingSessions,
                           BigDecimal volumePerSessionKg, BigDecimal earlierVolumePerSessionKg,
                           Integer minutesPerLiftingSession, Integer earlierMinutesPerLiftingSession,
                           RecordInfo personalRecord, MostTrainedInfo mostTrained, List<LiftProgress> lifts,
                           CardioInfo cardio, MobilityInfo mobility, SportInfo sport) {
        this(timezone, referenceDate, period, periodStart, periodEnd, sessions, liftingSessions,
                volumePerSessionKg, earlierVolumePerSessionKg, minutesPerLiftingSession,
                earlierMinutesPerLiftingSession, personalRecord, mostTrained, lifts, cardio, mobility, sport,
                List.of());
    }

    /** The same insight with the main lifts' estimated best singles from the weekly insight. */
    public TrainingInsight withBestSingles(List<BestSingle> singles) {
        return new TrainingInsight(timezone, referenceDate, period, periodStart, periodEnd, sessions,
                liftingSessions, volumePerSessionKg, earlierVolumePerSessionKg, minutesPerLiftingSession,
                earlierMinutesPerLiftingSession, personalRecord, mostTrained, lifts, cardio, mobility, sport, singles);
    }

    // Same re-wrapping as lifts(), for the same findsecbugs reason.
    @Override
    public List<BestSingle> bestSingles() {
        return List.copyOf(bestSingles);
    }

    /**
     * One main lift's estimated best single at the start and the end of the weekly insight's four
     * weeks, and the change as a whole percent. The coach reads strength this way, never as weight moved.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BestSingle(String name, BigDecimal e1rmStart, BigDecimal e1rmNow, Integer changePct) {
    }

    /** The shape from before cardio, mobility and sport were reported; those groups read as absent. */
    public TrainingInsight(String timezone, LocalDate referenceDate, String period, LocalDate periodStart,
                           LocalDate periodEnd, Integer sessions, Integer liftingSessions,
                           BigDecimal volumePerSessionKg, BigDecimal earlierVolumePerSessionKg,
                           Integer minutesPerLiftingSession, Integer earlierMinutesPerLiftingSession,
                           RecordInfo personalRecord, MostTrainedInfo mostTrained, List<LiftProgress> lifts) {
        this(timezone, referenceDate, period, periodStart, periodEnd, sessions, liftingSessions,
                volumePerSessionKg, earlierVolumePerSessionKg, minutesPerLiftingSession,
                earlierMinutesPerLiftingSession, personalRecord, mostTrained, lifts, null, null, null, List.of());
    }

    // Re-wraps in an immutable copy on every call, the same belt-and-braces pattern
    // CoachMessageResponse.toolsUsed() uses elsewhere in this module: List.copyOf in the compact
    // constructor above already makes the stored reference unmodifiable, but this is what actually
    // satisfies findsecbugs' EI_EXPOSE_REP here.
    @Override
    public List<LiftProgress> lifts() {
        return List.copyOf(lifts);
    }

    /** A new best on one exercise, and what stood before it. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecordInfo(String exerciseName, BigDecimal weightKg, LocalDate achievedOn, BigDecimal previousBestKg) {
    }

    /** The muscle group with the most sessions against it in the period, and one example exercise. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MostTrainedInfo(String muscleGroup, Integer sessions, List<String> exerciseNames) {
        public MostTrainedInfo {
            exerciseNames = exerciseNames == null ? List.of() : List.copyOf(exerciseNames);
        }
    }

    /** One exercise's weight progression across the period, passed through to the client unchanged. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiftProgress(String exerciseName, BigDecimal fromKg, LocalDate fromDate, BigDecimal toKg, LocalDate toDate) {
    }

    /**
     * The period's cardio sessions. The distance and pace values are null when no session of the
     * period recorded a distance, and each earlier value is null when the week before had none.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardioInfo(Integer sessions, Integer earlierSessions,
                             BigDecimal distancePerSessionKm, BigDecimal earlierDistancePerSessionKm,
                             BigDecimal paceSecondsPerKm, BigDecimal earlierPaceSecondsPerKm,
                             Integer minutesPerSession, Integer earlierMinutesPerSession) {
    }

    /** The period's mobility sessions and their total minutes. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MobilityInfo(Integer sessions, Integer minutes, Integer earlierSessions, Integer earlierMinutes) {
    }

    /** The period's sport sessions, in total and by activity. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SportInfo(Integer sessions, Integer minutes, List<SportActivity> activities) {
        public SportInfo {
            activities = activities == null ? List.of() : List.copyOf(activities);
        }

        // Same re-wrapping as TrainingInsight.lifts(), for the same findsecbugs reason.
        @Override
        public List<SportActivity> activities() {
            return List.copyOf(activities);
        }
    }

    /** One sport activity's sessions and minutes in the period. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SportActivity(String activity, String label, Integer sessions, Integer minutes) {
    }
}
