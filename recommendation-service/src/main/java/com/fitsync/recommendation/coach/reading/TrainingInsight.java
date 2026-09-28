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
        List<LiftProgress> lifts) {

    /** period value meaning "nothing recent enough to talk about". */
    public static final String PERIOD_NONE = "NONE";

    public TrainingInsight {
        lifts = lifts == null ? List.of() : List.copyOf(lifts);
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
}
