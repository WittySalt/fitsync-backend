package com.fitsync.analytics.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
