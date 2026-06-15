package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;

public interface WorkoutDeletedEventConsumer {

    void consume(FitSyncDomainEvent<WorkoutDeletedPayload> event);
}
