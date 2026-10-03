package com.fitsync.workout;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fitsync.workout.entity.ExerciseEntity;
import com.fitsync.workout.entity.WorkoutPlanEntity;
import com.fitsync.workout.messaging.AccountDeletionProjectionService;
import com.fitsync.workout.repository.ExerciseRepository;
import com.fitsync.workout.repository.WorkoutPlanRepository;
import com.fitsync.workout.repository.WorkoutSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AccountDeletionProjectionServiceTests {

    @Test
    void deletesUserWorkoutDataBeforeOwnedExercises() {
        WorkoutSessionRepository sessionRepository = mock(WorkoutSessionRepository.class);
        WorkoutPlanRepository planRepository = mock(WorkoutPlanRepository.class);
        ExerciseRepository exerciseRepository = mock(ExerciseRepository.class);
        AccountDeletionProjectionService service =
                new AccountDeletionProjectionService(sessionRepository, planRepository, exerciseRepository);
        UUID userId = UUID.randomUUID();
        List<WorkoutPlanEntity> plans = List.of(new WorkoutPlanEntity());
        List<ExerciseEntity> exercises = List.of(new ExerciseEntity());
        when(planRepository.findByUserIdOrderByDateTimeUpdatedDesc(userId)).thenReturn(plans);
        when(exerciseRepository.findByOwnerUserIdOrderByNameAsc(userId)).thenReturn(exercises);

        service.deleteUserData(userId);

        InOrder inOrder = inOrder(sessionRepository, planRepository, exerciseRepository);
        // Sessions go by one statement that also takes the deleted ones, before the plans they point to.
        inOrder.verify(sessionRepository).purgeAllByUserId(userId);
        inOrder.verify(planRepository).deleteAll(plans);
        inOrder.verify(exerciseRepository).deleteAll(exercises);
    }
}
