package com.fitsync.workout.progress;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.fitsync.workout.dto.progress.ActivityProgressView;
import com.fitsync.workout.dto.progress.ExerciseProgressView;
import com.fitsync.workout.dto.progress.TrainingProgressView;
import com.fitsync.workout.progress.TrainingProgressCalculator.CardFacts;
import com.fitsync.workout.security.CurrentUserPrincipal;

/**
 * The period reads of the Progress Training page, its activity pages and its exercise pages. Every
 * read is scoped to the caller; a period's dates are inclusive and local to {@code zone}.
 */
public interface TrainingProgressService {

    /** @param bodyWeightKg the body weight for relative strength, or null for no ratios */
    TrainingProgressView getTraining(CurrentUserPrincipal currentUser, ZoneId zone, ProgressPeriod period,
                                     BigDecimal bodyWeightKg);

    ActivityProgressView getActivity(CurrentUserPrincipal currentUser, ZoneId zone, ActivityKey key,
                                     ProgressPeriod period);

    ExerciseProgressView getExercise(CurrentUserPrincipal currentUser, ZoneId zone, UUID exerciseId,
                                     ProgressPeriod period);

    /** The main lifts and activity summaries of the 28 days ending on {@code referenceDate}, for the weekly card. */
    CardFacts getCardFacts(UUID userId, ZoneId zone, LocalDate referenceDate);
}
