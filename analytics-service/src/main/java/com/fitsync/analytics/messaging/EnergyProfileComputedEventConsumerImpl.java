package com.fitsync.analytics.messaging;

import java.time.Clock;
import java.time.Instant;

import com.fitsync.analytics.entity.AnalyticsUserEnergyProfileEntity;
import com.fitsync.analytics.repository.AnalyticsProcessedEventRepository;
import com.fitsync.analytics.repository.AnalyticsUserEnergyProfileRepository;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps every EnergyProfileComputed event as a history row so the energy view can find the BMR that
 * was valid on a given day. Only derived numbers arrive here, never birth date or sex (EV-3).
 */
@Service
public class EnergyProfileComputedEventConsumerImpl implements DomainEventConsumer<EnergyProfileComputedPayload> {

    private final AnalyticsProcessedEventRepository processedEventRepository;
    private final AnalyticsUserEnergyProfileRepository energyProfileRepository;
    private final Clock clock;

    public EnergyProfileComputedEventConsumerImpl(AnalyticsProcessedEventRepository processedEventRepository,
                                                  AnalyticsUserEnergyProfileRepository energyProfileRepository,
                                                  Clock clock) {
        this.processedEventRepository = processedEventRepository;
        this.energyProfileRepository = energyProfileRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<EnergyProfileComputedPayload> event) {
        if (event == null || event.userId() == null || processedEventRepository.existsById(event.eventId())) {
            return;
        }
        EnergyProfileComputedPayload payload = event.payload();
        Instant computedAt = payload == null || payload.computedAt() == null
                ? event.occurredAt()
                : payload.computedAt();
        if (payload != null && payload.bmrCalories() != null && payload.bmrCalories() > 0 && computedAt != null) {
            AnalyticsUserEnergyProfileEntity entity = new AnalyticsUserEnergyProfileEntity();
            entity.setEventId(event.eventId());
            entity.setUserId(event.userId());
            entity.setBmrKcal(payload.bmrCalories());
            entity.setEstimated(payload.estimated());
            entity.setComputedAt(computedAt);
            energyProfileRepository.save(entity);
        }
        ProcessedEvents.record(processedEventRepository, event, clock);
    }
}
