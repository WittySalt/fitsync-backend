package com.fitsync.backend.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
