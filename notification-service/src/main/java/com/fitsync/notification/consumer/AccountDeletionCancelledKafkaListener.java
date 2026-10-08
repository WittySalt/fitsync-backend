package com.fitsync.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.user.AccountDeletionCancelledPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.account-deletion-cancelled", name = "enabled", havingValue = "true")
public class AccountDeletionCancelledKafkaListener extends AbstractDomainEventKafkaListener<AccountDeletionCancelledPayload> {

    public AccountDeletionCancelledKafkaListener(ObjectMapper objectMapper, DomainEventConsumer<AccountDeletionCancelledPayload> consumer) {
        super(objectMapper, AccountDeletionCancelledPayload.class, "AccountDeletionCancelled", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.notification-service:notification-consumer-group}"
                    + "-account-deletion-cancelled",
            autoStartup = "${fitsync.kafka.listeners.account-deletion-cancelled.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
