package com.fitsync.analytics.messaging;

import java.time.Instant;
import java.util.List;

import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends the oldest pending outbox events to Kafka one at a time and marks each one published or failed. A
 * failed event is not picked up again.
 */
@Service
public class OutboxEventRelayImpl implements OutboxEventRelay {

    /** Longest error message kept on a failed row. */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

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
        List<OutboxEventEntity> pending = outboxEventRepository
                .findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING);
        int limit = Math.min(batchSize, pending.size());
        int publishedCount = 0;
        for (int i = 0; i < limit; i++) {
            OutboxEventEntity event = pending.get(i);
            try {
                kafkaEventProducer.send(event);
                event.setStatus(OutboxEventStatus.PUBLISHED);
                event.setDateTimePublished(Instant.now());
                event.setErrorMessage(null);
                publishedCount++;
            } catch (RuntimeException ex) {
                event.setStatus(OutboxEventStatus.FAILED);
                event.setErrorMessage(truncate(ex.getMessage()));
            }
        }
        return publishedCount;
    }

    private String truncate(String message) {
        if (message == null || message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
