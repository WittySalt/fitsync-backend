package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import org.springframework.stereotype.Service;

@Service
public class WorkoutUpdatedEventConsumerImpl implements DomainEventConsumer<WorkoutUpdatedPayload> {

    private final WorkoutProjectionService projectionService;

    public WorkoutUpdatedEventConsumerImpl(WorkoutProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<WorkoutUpdatedPayload> event) {
        WorkoutUpdatedPayload payload = event.payload();
        projectionService.upsertWorkout(
                event,
                payload == null ? null : payload.sessionId(),
                payload == null ? null : payload.startedAt(),
                payload == null ? null : payload.durationMinutes(),
                payload == null ? null : payload.caloriesBurned(),
                payload == null ? null : payload.exerciseCount(),
                payload != null && Boolean.TRUE.equals(payload.caloriesEstimated()));
    }
}
