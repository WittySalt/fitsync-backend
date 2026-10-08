package com.fitsync.auth.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
