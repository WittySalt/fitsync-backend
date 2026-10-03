package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

/**
 * A completed session as the progress figures see it. {@code date} is the local date the session is
 * placed on (completion, else start, in the caller's zone); {@code localStart} is when it began there.
 */
public record SessionSnap(UUID sessionId, LocalDate date, Instant startedAt, LocalDateTime localStart,
                          SessionKind kind, SportActivity sport, int minutes, Integer effort,
                          boolean effortEstimated, Integer calories, BigDecimal distanceKm,
                          List<ExerciseSnap> exercises) {

    /** Default efforts by kind for a session that has none: strength 6, cardio 6, sport 5, mobility 3. */
    static int defaultEffort(SessionKind kind) {
        return switch (kind) {
            case STRENGTH, CARDIO -> 6;
            case SPORT -> 5;
            case MOBILITY -> 3;
        };
    }

    public SessionSnap {
        exercises = exercises == null ? List.of() : List.copyOf(exercises);
    }

    @Override
    public List<ExerciseSnap> exercises() {
        return List.copyOf(exercises);
    }

    /** The key of the activity this session belongs to: STRENGTH, CARDIO, MOBILITY or SPORT:&lt;activity&gt;. */
    public String activityKey() {
        if (kind == SessionKind.SPORT) {
            return "SPORT:" + (sport == null ? SportActivity.OTHER : sport).name();
        }
        return kind.name();
    }

    /** True when the load uses an effort the person did not choose (the server's estimate or the kind default). */
    public boolean loadEstimated() {
        return effort == null || effortEstimated;
    }

    /** Training load: effort × minutes, with the kind's default effort when the session has none. */
    public long load() {
        int used = effort == null ? defaultEffort(kind) : effort;
        return (long) used * minutes;
    }

    /** Seconds per kilometre, or null without a positive distance and duration. */
    public Integer paceSecPerKm() {
        if (distanceKm == null || distanceKm.signum() <= 0 || minutes <= 0) {
            return null;
        }
        return BigDecimal.valueOf(minutes * 60L).divide(distanceKm, 0, RoundingMode.HALF_UP).intValue();
    }
}
