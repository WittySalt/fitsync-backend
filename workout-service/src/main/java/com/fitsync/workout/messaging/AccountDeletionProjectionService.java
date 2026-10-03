package com.fitsync.workout.messaging;

import java.util.UUID;

import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountDeletionProjectionService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final ExerciseRepository exerciseRepository;

    public AccountDeletionProjectionService(WorkoutSessionRepository workoutSessionRepository,
                                            WorkoutPlanRepository workoutPlanRepository,
                                            ExerciseRepository exerciseRepository) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional
    public void deleteUserData(UUID userId) {
        if (userId == null) {
            return;
        }
        // The purge takes deleted sessions too, which the entity's own queries no longer see.
        workoutSessionRepository.purgeAllByUserId(userId);
        workoutPlanRepository.deleteAll(workoutPlanRepository.findByUserIdOrderByDateTimeUpdatedDesc(userId));
        exerciseRepository.deleteAll(exerciseRepository.findByOwnerUserIdOrderByNameAsc(userId));
    }
}
