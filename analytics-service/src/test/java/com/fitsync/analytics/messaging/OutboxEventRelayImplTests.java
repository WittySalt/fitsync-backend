package com.fitsync.analytics.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import com.fitsync.persistence.outbox.OutboxRetryPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    private static void returnsDue(OutboxEventRepository repository, List<OutboxEventEntity> events) {
        when(repository.findDueForPublishing(eq(OutboxEventStatus.PENDING), eq(OutboxEventStatus.FAILED),
                eq(OutboxRetryPolicy.MAX_ATTEMPTS), any(Instant.class), any(Pageable.class)))
                .thenReturn(events);
    }

    @Test
    void publishesPendingEventsAndMarksPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        OutboxEventEntity event = pendingEvent();
        returnsDue(repository, List.of(event));

        int published = new OutboxEventRelayImpl(repository, producer).publishPendingBatch(50);

        assertThat(published).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getDateTimePublished()).isNotNull();
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void marksFailedAndSchedulesARetryWhenProducerThrows() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        OutboxEventEntity event = pendingEvent();
        returnsDue(repository, List.of(event));
        doThrow(new IllegalStateException("kafka down")).when(producer).send(event);
        Instant before = Instant.now();

        int published = new OutboxEventRelayImpl(repository, producer).publishPendingBatch(50);

        assertThat(published).isZero();
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getErrorMessage()).contains("kafka down");
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isBetween(before.plus(Duration.ofSeconds(30)),
                Instant.now().plus(Duration.ofSeconds(30)));
    }

    @Test
    void aFailedEventThatIsDueAgainIsPublished() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        OutboxEventEntity event = pendingEvent();
        event.setStatus(OutboxEventStatus.FAILED);
        event.setAttemptCount(3);
        event.setErrorMessage("kafka down");
        event.setNextAttemptAt(Instant.now().minusSeconds(1));
        returnsDue(repository, List.of(event));

        int published = new OutboxEventRelayImpl(repository, producer).publishPendingBatch(50);

        assertThat(published).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getAttemptCount()).isEqualTo(4);
        assertThat(event.getErrorMessage()).isNull();
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void asksForExactlyTheConfiguredBatchSize() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        KafkaEventProducer producer = mock(KafkaEventProducer.class);
        returnsDue(repository, List.of());

        new OutboxEventRelayImpl(repository, producer).publishPendingBatch(250);

        verify(repository).findDueForPublishing(eq(OutboxEventStatus.PENDING), eq(OutboxEventStatus.FAILED),
                eq(OutboxRetryPolicy.MAX_ATTEMPTS), any(Instant.class), eq(PageRequest.of(0, 250)));
    }
}
