package com.fitsync.analytics.messaging;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitsync.analytics.messaging.entity.OutboxEventEntity;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxDomainEventPublisherTests {

    @Test
    void publishWritesPendingOutboxRow() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        OutboxDomainEventPublisher publisher = new OutboxDomainEventPublisher(objectMapper, repository);

        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID eventId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant occurredAt = Instant.parse("2026-06-18T00:00:00Z");
        FitSyncDomainEvent<StreakMilestoneReachedPayload> event = new FitSyncDomainEvent<>(
                eventId, "StreakMilestoneReached", 1, occurredAt, "analytics-service", userId,
                new StreakMilestoneReachedPayload(7, 7, LocalDate.parse("2026-06-18")));

        publisher.publish("analytics-events", "UserStreak", userId.toString(), event);

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(repository).save(captor.capture());
        OutboxEventEntity saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getTopic()).isEqualTo("analytics-events");
        assertThat(saved.getEventType()).isEqualTo("StreakMilestoneReached");
        assertThat(saved.getAggregateType()).isEqualTo("UserStreak");
        assertThat(saved.getAggregateId()).isEqualTo(userId.toString());
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getDateTimeProduced()).isEqualTo(occurredAt);
        assertThat(saved.getPayload()).contains("StreakMilestoneReached").contains("\"milestone\":7");
    }
}
