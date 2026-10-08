package com.fitsync.notification.handler;

import java.time.Instant;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.notification.entity.RecipientEntity;
import com.fitsync.notification.service.RecipientStore;

/**
 * Remembers when the user last worked out, which is all E1 needs to stay quiet on a day the
 * training already happened. The latest time wins, so events arriving out of order or twice
 * change nothing. A cancelled or merely planned session is not a workout.
 */
@Service
public class WorkoutLoggedHandler implements DomainEventConsumer<WorkoutLoggedPayload> {

    private static final Set<String> NOT_A_WORKOUT = Set.of("CANCELLED", "PLANNED");

    private final RecipientStore recipients;

    public WorkoutLoggedHandler(RecipientStore recipients) {
        this.recipients = recipients;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<WorkoutLoggedPayload> event) {
        WorkoutLoggedPayload payload = event.payload();
        if (event.userId() == null || payload == null) {
            return;
        }
        String status = Text.upper(payload.status(), 20);
        if (status != null && NOT_A_WORKOUT.contains(status)) {
            return;
        }
        Instant at = payload.completedAt() != null ? payload.completedAt()
                : payload.startedAt() != null ? payload.startedAt() : event.occurredAt();
        if (at == null) {
            return;
        }
        RecipientEntity recipient = recipients.getOrCreate(event.userId());
        if (recipient.getLastWorkoutAt() == null || at.isAfter(recipient.getLastWorkoutAt())) {
            recipient.setLastWorkoutAt(at);
            recipients.save(recipient);
        }
    }
}
