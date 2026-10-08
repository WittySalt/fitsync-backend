package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import org.springframework.stereotype.Service;

/** Handles WorkoutDeleted by removing the session's contribution from its day's workout totals. */
@Service
public class WorkoutDeletedEventConsumerImpl implements DomainEventConsumer<WorkoutDeletedPayload> {

    private final WorkoutProjectionService projectionService;

    public WorkoutDeletedEventConsumerImpl(WorkoutProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<WorkoutDeletedPayload> event) {
        WorkoutDeletedPayload payload = event.payload();
        projectionService.deleteWorkout(event, payload == null ? null : payload.sessionId());
    }
}
