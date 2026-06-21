package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import org.springframework.stereotype.Service;

@Service
public class WorkoutLoggedEventConsumerImpl implements DomainEventConsumer<WorkoutLoggedPayload> {

    private final WorkoutProjectionService projectionService;

    public WorkoutLoggedEventConsumerImpl(WorkoutProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<WorkoutLoggedPayload> event) {
        WorkoutLoggedPayload payload = event.payload();
        projectionService.upsertWorkout(
                event,
                payload == null ? null : payload.sessionId(),
                payload == null ? null : payload.startedAt(),
                payload == null ? null : payload.durationMinutes(),
                payload == null ? null : payload.caloriesBurned(),
                payload == null ? null : payload.exerciseCount());
    }
}
