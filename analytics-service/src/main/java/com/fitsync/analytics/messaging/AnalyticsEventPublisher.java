package com.fitsync.analytics.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fitsync.analytics.messaging.repository.OutboxEventRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds and publishes analytics-derived domain events through the transactional outbox. Event ids
 * are deterministic (derived from the user + milestone / week), so re-running the rollup or weekly
 * job never emits the same milestone or recap twice; a pre-check against the outbox keeps the insert
 * idempotent.
 */
@Component
public class AnalyticsEventPublisher {

    private static final String SOURCE = "analytics-service";
    private static final int EVENT_VERSION = 1;
    private static final String STREAK_EVENT_TYPE = "StreakMilestoneReached";
    private static final String STREAK_AGGREGATE_TYPE = "UserStreak";
    private static final String WEEKLY_RECAP_EVENT_TYPE = "WeeklyRecapGenerated";
    private static final String WEEKLY_RECAP_AGGREGATE_TYPE = "UserWeeklyRecap";

    private final DomainEventPublisher domainEventPublisher;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;
    private final String topic;

    public AnalyticsEventPublisher(DomainEventPublisher domainEventPublisher,
                                   OutboxEventRepository outboxEventRepository,
                                   Clock clock,
                                   @Value("${fitsync.kafka.topics.analytics-events:analytics-events}") String topic) {
        this.domainEventPublisher = domainEventPublisher;
        this.outboxEventRepository = outboxEventRepository;
        this.clock = clock;
        this.topic = topic;
    }

    public void publishStreakMilestone(UUID userId, int streakDays, int milestone, LocalDate achievedDate) {
        UUID eventId = deterministicId(userId + ":streak:" + milestone);
        if (outboxEventRepository.existsById(eventId)) {
            return;
        }
        FitSyncDomainEvent<StreakMilestoneReachedPayload> event = new FitSyncDomainEvent<>(
                eventId,
                STREAK_EVENT_TYPE,
                EVENT_VERSION,
                Instant.now(clock),
                SOURCE,
                userId,
                new StreakMilestoneReachedPayload(streakDays, milestone, achievedDate));
        domainEventPublisher.publish(topic, STREAK_AGGREGATE_TYPE, userId.toString(), event);
    }

    public void publishWeeklyRecap(UUID userId, WeeklyRecapGeneratedPayload payload) {
        UUID eventId = deterministicId(userId + ":weekly-recap:" + payload.weekStart());
        if (outboxEventRepository.existsById(eventId)) {
            return;
        }
        FitSyncDomainEvent<WeeklyRecapGeneratedPayload> event = new FitSyncDomainEvent<>(
                eventId,
                WEEKLY_RECAP_EVENT_TYPE,
                EVENT_VERSION,
                Instant.now(clock),
                SOURCE,
                userId,
                payload);
        domainEventPublisher.publish(topic, WEEKLY_RECAP_AGGREGATE_TYPE, userId.toString(), event);
    }

    private UUID deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
