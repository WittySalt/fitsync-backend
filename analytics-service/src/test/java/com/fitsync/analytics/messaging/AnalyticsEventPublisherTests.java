package com.fitsync.analytics.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.contracts.events.DomainEventPublisher;
import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnalyticsEventPublisherTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final Clock clock = Clock.fixed(Instant.parse("2026-06-18T00:05:00Z"), ZoneOffset.UTC);

    private UUID deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void publishStreakMilestoneEmitsDeterministicEventWhenNotAlreadyPublished() {
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.existsById(any())).thenReturn(false);
        AnalyticsEventPublisher publisher =
                new AnalyticsEventPublisher(domainEventPublisher, repository, clock, "analytics-events");

        publisher.publishStreakMilestone(USER_ID, 30, 30, LocalDate.parse("2026-06-18"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<FitSyncDomainEvent<StreakMilestoneReachedPayload>> captor =
                ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(domainEventPublisher).publish(eq("analytics-events"), eq("UserStreak"), eq(USER_ID.toString()),
                captor.capture());
        FitSyncDomainEvent<StreakMilestoneReachedPayload> event = captor.getValue();
        assertThat(event.eventId()).isEqualTo(deterministicId(USER_ID + ":streak:30"));
        assertThat(event.eventType()).isEqualTo("StreakMilestoneReached");
        assertThat(event.userId()).isEqualTo(USER_ID);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-06-18T00:05:00Z"));
        assertThat(event.payload().milestone()).isEqualTo(30);
        assertThat(event.payload().streakDays()).isEqualTo(30);
    }

    @Test
    void publishStreakMilestoneSkipsWhenAlreadyPublished() {
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.existsById(any())).thenReturn(true);
        AnalyticsEventPublisher publisher =
                new AnalyticsEventPublisher(domainEventPublisher, repository, clock, "analytics-events");

        publisher.publishStreakMilestone(USER_ID, 7, 7, LocalDate.parse("2026-06-18"));

        verifyNoInteractions(domainEventPublisher);
    }

    @Test
    void publishWeeklyRecapEmitsDeterministicEventPerWeek() {
        DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.existsById(any())).thenReturn(false);
        AnalyticsEventPublisher publisher =
                new AnalyticsEventPublisher(domainEventPublisher, repository, clock, "analytics-events");
        WeeklyRecapGeneratedPayload payload = new WeeklyRecapGeneratedPayload(
                LocalDate.parse("2026-06-08"), LocalDate.parse("2026-06-14"), 4, 210, 1800, 12600, 5);

        publisher.publishWeeklyRecap(USER_ID, payload);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<FitSyncDomainEvent<WeeklyRecapGeneratedPayload>> captor =
                ArgumentCaptor.forClass(FitSyncDomainEvent.class);
        verify(domainEventPublisher).publish(eq("analytics-events"), eq("UserWeeklyRecap"), eq(USER_ID.toString()),
                captor.capture());
        FitSyncDomainEvent<WeeklyRecapGeneratedPayload> event = captor.getValue();
        assertThat(event.eventId()).isEqualTo(deterministicId(USER_ID + ":weekly-recap:2026-06-08"));
        assertThat(event.eventType()).isEqualTo("WeeklyRecapGenerated");
        assertThat(event.payload().workoutCount()).isEqualTo(4);
    }
}
