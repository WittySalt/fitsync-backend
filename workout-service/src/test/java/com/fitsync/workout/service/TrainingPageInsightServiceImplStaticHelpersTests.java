package com.fitsync.workout.service;

import java.math.BigDecimal;

import com.fitsync.workout.domain.WorkoutSetStatus;
import com.fitsync.workout.entity.SessionEntry;
import com.fitsync.workout.entity.WorkoutExerciseSetLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit coverage for {@link TrainingPageInsightServiceImpl}'s pure arithmetic helpers. These run
 * with no Spring context, independently of persistence.
 */
class TrainingPageInsightServiceImplStaticHelpersTests {

    @Test
    void topSetWeightReturnsNullWhenNoSetHasAWeight() {
        SessionEntry log = logWithSets(setWithWeight(null), setWithWeight(null));
        assertNull(TrainingPageInsightServiceImpl.topSetWeight(log));
    }

    @Test
    void topSetWeightIgnoresNullWeightsAmongOtherSets() {
        SessionEntry log = logWithSets(setWithWeight(null), setWithWeight(new BigDecimal("18.00")));
        assertEquals(0, new BigDecimal("18.00").compareTo(TrainingPageInsightServiceImpl.topSetWeight(log)));
    }

    @Test
    void topSetWeightReturnsTheHeaviestSet() {
        SessionEntry log = logWithSets(
                setWithWeight(new BigDecimal("16.00")),
                setWithWeight(new BigDecimal("22.50")),
                setWithWeight(new BigDecimal("18.00")));
        assertEquals(0, new BigDecimal("22.50").compareTo(TrainingPageInsightServiceImpl.topSetWeight(log)));
    }

    @Test
    void topSetWeightLeavesOutSetsThatWereNeverPerformed() {
        SessionEntry log = logWithSets(
                set(new BigDecimal("40.00"), false, WorkoutSetStatus.PENDING),
                set(new BigDecimal("35.00"), true, WorkoutSetStatus.SKIPPED),
                set(new BigDecimal("20.00"), true, WorkoutSetStatus.DONE));
        assertEquals(0, new BigDecimal("20.00").compareTo(TrainingPageInsightServiceImpl.topSetWeight(log)));
    }

    @Test
    void topSetWeightCountsALoggedSetWithoutAStatusOfDone() {
        // Imported and seeded sessions are logged with the default status.
        SessionEntry log = logWithSets(set(new BigDecimal("22.50"), true, WorkoutSetStatus.PENDING));
        assertEquals(0, new BigDecimal("22.50").compareTo(TrainingPageInsightServiceImpl.topSetWeight(log)));
    }

    @Test
    void scaleRoundsHalfUpToTwoDecimalPlaces() {
        assertEquals(0, new BigDecimal("22.51").compareTo(TrainingPageInsightServiceImpl.scale(new BigDecimal("22.505"))));
    }

    @Test
    void volumePerSessionReturnsNullWhenThereAreNoLiftingSessions() {
        assertNull(TrainingPageInsightServiceImpl.volumePerSession(BigDecimal.ZERO, 0));
    }

    @Test
    void volumePerSessionDividesAndScalesHalfUp() {
        BigDecimal result = TrainingPageInsightServiceImpl.volumePerSession(new BigDecimal("3846.00"), 2);
        assertEquals(0, new BigDecimal("1923.00").compareTo(result));
    }

    @Test
    void averageMinutesReturnsNullWhenThereIsNothingToAverage() {
        assertNull(TrainingPageInsightServiceImpl.averageMinutes(0, 0));
    }

    @Test
    void averageMinutesRoundsHalfUpToAWholeNumber() {
        assertEquals(51, TrainingPageInsightServiceImpl.averageMinutes(101, 2));
    }

    private SessionEntry logWithSets(WorkoutExerciseSetLogEntity... sets) {
        SessionEntry log = new SessionEntry();
        for (WorkoutExerciseSetLogEntity set : sets) {
            log.getSets().add(set);
        }
        return log;
    }

    private WorkoutExerciseSetLogEntity setWithWeight(BigDecimal weightKg) {
        return set(weightKg, true, WorkoutSetStatus.DONE);
    }

    private WorkoutExerciseSetLogEntity set(BigDecimal weightKg, boolean logged, WorkoutSetStatus status) {
        WorkoutExerciseSetLogEntity set = new WorkoutExerciseSetLogEntity();
        set.setWeightKg(weightKg);
        // The entity's setters keep the two fields in step. A row written by an import or a seed
        // script is loaded as stored, so the fields are set directly to build that case.
        ReflectionTestUtils.setField(set, "logged", logged);
        ReflectionTestUtils.setField(set, "status", status);
        return set;
    }
}
