package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;

public interface WorkoutUpdatedEventConsumer {

    void consume(FitSyncDomainEvent<WorkoutUpdatedPayload> event);
}
