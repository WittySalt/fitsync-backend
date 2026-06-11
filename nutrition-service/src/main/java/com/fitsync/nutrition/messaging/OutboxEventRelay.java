package com.fitsync.nutrition.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
