package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.SessionKind;
import com.fitsync.workout.domain.SportActivity;

/** Small builders for session snapshots in the calculator tests. */
final class Snaps {

    static final UUID DEADLIFT = UUID.fromString("00000000-0000-0000-0000-00000000d001");
    static final UUID SQUAT = UUID.fromString("00000000-0000-0000-0000-00000000d002");
    static final UUID BENCH = UUID.fromString("00000000-0000-0000-0000-00000000d003");
    static final UUID PRESS = UUID.fromString("00000000-0000-0000-0000-00000000d004");
    static final UUID CURL = UUID.fromString("00000000-0000-0000-0000-00000000d005");
    static final UUID TREADMILL = UUID.fromString("00000000-0000-0000-0000-00000000d006");

    private Snaps() {
    }

    static SetSnap set(String weight, int reps) {
        return new SetSnap(new BigDecimal(weight), reps);
    }

    static ExerciseSnap deadlift(SetSnap... sets) {
        return new ExerciseSnap(DEADLIFT, "Deadlift", MuscleGroup.BACK, Set.of(MuscleGroup.LEGS, MuscleGroup.GLUTES),
                ExerciseEquipment.BARBELL, Arrays.asList(sets));
    }

    static ExerciseSnap squat(SetSnap... sets) {
        return new ExerciseSnap(SQUAT, "Squat", MuscleGroup.LEGS, Set.of(MuscleGroup.GLUTES),
                ExerciseEquipment.BARBELL, Arrays.asList(sets));
    }

    static ExerciseSnap bench(SetSnap... sets) {
        return new ExerciseSnap(BENCH, "Bench press", MuscleGroup.CHEST, Set.of(MuscleGroup.ARMS, MuscleGroup.SHOULDERS),
                ExerciseEquipment.BARBELL, Arrays.asList(sets));
    }

    static ExerciseSnap curl(SetSnap... sets) {
        return new ExerciseSnap(CURL, "Bicep curl", MuscleGroup.ARMS, Set.of(), ExerciseEquipment.DUMBBELL,
                Arrays.asList(sets));
    }

    static ExerciseSnap treadmill() {
        return new ExerciseSnap(TREADMILL, "Treadmill", MuscleGroup.CARDIO, Set.of(), ExerciseEquipment.CARDIO_MACHINE,
                List.of());
    }

    static SessionSnap strength(String date, int minutes, Integer effort, ExerciseSnap... exercises) {
        return session(date, "18:00", SessionKind.STRENGTH, null, minutes, effort, null, exercises);
    }

    static SessionSnap cardio(String date, int minutes, Integer effort, String distanceKm) {
        return session(date, "07:00", SessionKind.CARDIO, null, minutes, effort,
                distanceKm == null ? null : new BigDecimal(distanceKm), treadmill());
    }

    static SessionSnap sport(String date, String time, SportActivity sport, int minutes, Integer effort) {
        return session(date, time, SessionKind.SPORT, sport, minutes, effort, null);
    }

    static SessionSnap session(String date, String time, SessionKind kind, SportActivity sport, int minutes,
                               Integer effort, BigDecimal distanceKm, ExerciseSnap... exercises) {
        LocalDate day = LocalDate.parse(date);
        var start = day.atTime(LocalTime.parse(time));
        UUID id = UUID.nameUUIDFromBytes((date + time + kind + sport + minutes).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SessionSnap(id, day, start.toInstant(ZoneOffset.UTC), start, kind, sport, minutes, effort, false,
                null, distanceKm, new ArrayList<>(Arrays.asList(exercises)));
    }
}
