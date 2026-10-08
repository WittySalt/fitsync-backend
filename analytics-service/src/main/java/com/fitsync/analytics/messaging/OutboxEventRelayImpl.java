package com.fitsync.analytics.messaging;

import java.time.Instant;
import java.util.List;

import com.fitsync.persistence.outbox.OutboxRetryPolicy;
import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends due outbox events to Kafka, oldest first, and marks each one published or failed. A failed event keeps
 * its error and is tried again later with backoff until it runs out of attempts ({@link OutboxRetryPolicy}).
 */
@Service
public class OutboxEventRelayImpl implements OutboxEventRelay {


    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventProducer kafkaEventProducer;

    public OutboxEventRelayImpl(OutboxEventRepository outboxEventRepository,
                               KafkaEventProducer kafkaEventProducer) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaEventProducer = kafkaEventProducer;
    }

    @Override
    @Transactional
    public int publishPendingBatch(int batchSize) {
        Instant now = Instant.now();
        List<OutboxEventEntity> due = outboxEventRepository.findDueForPublishing(
                OutboxEventStatus.PENDING, OutboxEventStatus.FAILED, OutboxRetryPolicy.MAX_ATTEMPTS, now,
                PageRequest.of(0, Math.max(1, batchSize)));
        int publishedCount = 0;
        for (OutboxEventEntity event : due) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            try {
                kafkaEventProducer.send(event);
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setDateTimePublished(Instant.now());
                event.setErrorMessage(null);
                event.setNextAttemptAt(null);
                publishedCount++;
            } catch (RuntimeException ex) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setErrorMessage(OutboxRetryPolicy.truncateError(ex.getMessage()));
                event.setNextAttemptAt(OutboxRetryPolicy.nextAttemptAt(event.getAttemptCount(), now));
            }
        }
        return publishedCount;
    }
}
