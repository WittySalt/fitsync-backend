package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;

public interface WorkoutLoggedEventConsumer {

    void consume(FitSyncDomainEvent<WorkoutLoggedPayload> event);
}
