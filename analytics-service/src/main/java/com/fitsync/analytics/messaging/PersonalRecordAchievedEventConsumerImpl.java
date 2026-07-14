package com.fitsync.analytics.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import org.springframework.stereotype.Service;

@Service
public class PersonalRecordAchievedEventConsumerImpl implements DomainEventConsumer<PersonalRecordAchievedPayload> {

    private final WorkoutRecordsProjectionService projectionService;

    public PersonalRecordAchievedEventConsumerImpl(WorkoutRecordsProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public void consume(FitSyncDomainEvent<PersonalRecordAchievedPayload> event) {
        PersonalRecordAchievedPayload payload = event.payload();
        projectionService.applyPersonalRecords(
                event,
                payload == null ? 0 : payload.records().size(),
                payload == null ? null : payload.achievedAt());
    }
}
