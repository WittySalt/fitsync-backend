package com.fitsync.analytics.messaging;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxEventRelayImplTests {

    private OutboxEventEntity pendingEvent() {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setTopic("analytics-events");
        event.setEventType("StreakMilestoneReached");
        event.setAggregateType("UserStreak");
        event.setAggregateId(UUID.randomUUID().toString());
        event.setEventVersion(1);
        event.setPayload("{}");
        event.setStatus(OutboxEventStatus.PENDING);
        return event;
    }

    @Test
    void publishesPendingEventsAndMarksPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        OutboxEventEntity event = pendingEvent();
        when(repository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));

        int published = new OutboxEventRelayImpl(repository, producer).publishPendingBatch(50);

        assertThat(published).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getDateTimePublished()).isNotNull();
        assertThat(event.getErrorMessage()).isNull();
    }

    @Test
    void marksFailedWhenProducerThrows() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        OutboxEventEntity event = pendingEvent();
        when(repository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka down")).when(producer).send(event);

        int published = new OutboxEventRelayImpl(repository, producer).publishPendingBatch(50);

        assertThat(published).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("kafka down");
    }
}
