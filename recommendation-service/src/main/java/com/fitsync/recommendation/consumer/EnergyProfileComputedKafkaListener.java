package com.fitsync.recommendation.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.EnergyProfileComputedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.energy-profile-computed", name = "enabled",
        havingValue = "true")
public class EnergyProfileComputedKafkaListener extends AbstractDomainEventKafkaListener<EnergyProfileComputedPayload> {

    public EnergyProfileComputedKafkaListener(ObjectMapper objectMapper,
                                              DomainEventConsumer<EnergyProfileComputedPayload> consumer) {
        super(objectMapper, EnergyProfileComputedPayload.class, "EnergyProfileComputed", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.recommendation-service:recommendation-consumer-group}"
                    + "-energy-profile-computed",
            autoStartup = "${fitsync.kafka.listeners.energy-profile-computed.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
