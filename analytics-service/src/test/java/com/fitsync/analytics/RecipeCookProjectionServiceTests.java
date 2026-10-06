package com.fitsync.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsProcessedEventEntity;
import com.fitsync.analytics.entity.AnalyticsUserCooksEntity;
import com.fitsync.analytics.messaging.RecipeCookProjectionService;
import com.fitsync.analytics.messaging.RecipeCookedEventConsumerImpl;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserCooksRepository;
import com.fitsync.analytics.service.AnalyticsCacheInvalidationService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.nutrition.RecipeCookedPayload;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** RecipeCooked events become the per-user cook counter, each event once. */
class RecipeCookProjectionServiceTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-06-14T08:00:00Z");

    private final AnalyticsProcessedEventRepository processedEventRepository =
            mock(AnalyticsProcessedEventRepository.class);
    private final AnalyticsUserCooksRepository cooksRepository = mock(AnalyticsUserCooksRepository.class);
    private final AnalyticsCacheInvalidationService cacheInvalidationService =
            mock(AnalyticsCacheInvalidationService.class);
    private final RecipeCookedEventConsumerImpl consumer = new RecipeCookedEventConsumerImpl(
            new RecipeCookProjectionService(processedEventRepository, cooksRepository, cacheInvalidationService,
                    Clock.fixed(NOW, ZoneOffset.UTC)));

    @Test
    void theFirstCookCreatesTheCounterWithItsFinishTime() {
        FitSyncDomainEvent<RecipeCookedPayload> event = event(Instant.parse("2026-06-13T19:00:00Z"));
        when(cooksRepository.findById(USER_ID)).thenReturn(Optional.empty());

        consumer.consume(event);

        AnalyticsUserCooksEntity saved = savedCooks();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getCooks()).isEqualTo(1);
        assertThat(saved.getFirstCookAt()).isEqualTo(Instant.parse("2026-06-13T19:00:00Z"));
        assertThat(saved.getLastCookAt()).isEqualTo(Instant.parse("2026-06-13T19:00:00Z"));
        ArgumentCaptor<AnalyticsProcessedEventEntity> processed =
                ArgumentCaptor.forClass(AnalyticsProcessedEventEntity.class);
        verify(processedEventRepository).save(processed.capture());
        assertThat(processed.getValue().getEventId()).isEqualTo(event.eventId());
        assertThat(processed.getValue().getEventType()).isEqualTo("RecipeCooked");
        verify(cacheInvalidationService).evictForUser(USER_ID);
    }

    @Test
    void aLaterCookAddsOneAndMovesOnlyTheLastCookTime() {
        AnalyticsUserCooksEntity existing = new AnalyticsUserCooksEntity();
        existing.setUserId(USER_ID);
        existing.setCooks(9);
        existing.setFirstCookAt(Instant.parse("2026-05-01T19:00:00Z"));
        existing.setLastCookAt(Instant.parse("2026-06-01T19:00:00Z"));
        when(cooksRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

        consumer.consume(event(Instant.parse("2026-06-13T19:00:00Z")));

        AnalyticsUserCooksEntity saved = savedCooks();
        assertThat(saved.getCooks()).isEqualTo(10);
        assertThat(saved.getFirstCookAt()).isEqualTo(Instant.parse("2026-05-01T19:00:00Z"));
        assertThat(saved.getLastCookAt()).isEqualTo(Instant.parse("2026-06-13T19:00:00Z"));
    }

    @Test
    void anEventAlreadyProcessedChangesNothing() {
        FitSyncDomainEvent<RecipeCookedPayload> event = event(Instant.parse("2026-06-13T19:00:00Z"));
        when(processedEventRepository.existsById(event.eventId())).thenReturn(true);

        consumer.consume(event);

        verify(cooksRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void withoutAFinishTimeTheEventTimeIsUsed() {
        FitSyncDomainEvent<RecipeCookedPayload> event = event(null);
        when(cooksRepository.findById(USER_ID)).thenReturn(Optional.empty());

        consumer.consume(event);

        assertThat(savedCooks().getFirstCookAt()).isEqualTo(event.occurredAt());
    }

    private AnalyticsUserCooksEntity savedCooks() {
        ArgumentCaptor<AnalyticsUserCooksEntity> captor = ArgumentCaptor.forClass(AnalyticsUserCooksEntity.class);
        verify(cooksRepository).save(captor.capture());
        return captor.getValue();
    }

    private static FitSyncDomainEvent<RecipeCookedPayload> event(Instant finishedAt) {
        return new FitSyncDomainEvent<>(UUID.randomUUID(), "RecipeCooked", 1, Instant.parse("2026-06-14T07:00:00Z"),
                "nutrition-service", USER_ID,
                new RecipeCookedPayload(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("2.00"), finishedAt));
    }
}
