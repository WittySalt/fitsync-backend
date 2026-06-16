package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;

public interface BodyMetricsRecordedEventConsumer {

    void consume(FitSyncDomainEvent<BodyMetricsRecordedPayload> event);
}
