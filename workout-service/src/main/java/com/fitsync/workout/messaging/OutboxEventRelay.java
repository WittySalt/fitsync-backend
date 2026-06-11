package com.fitsync.workout.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
