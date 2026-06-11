package com.fitsync.user.messaging;

public interface OutboxEventRelay {

    int publishPendingBatch(int batchSize);
}
