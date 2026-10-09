package com.fitsync.user.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.web.messaging.AbstractDomainEventKafkaListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code AccountDeleted} from {@code user-events}. auth-service publishes it when a deletion grace period
 * ends, and this service publishes it itself for {@code DELETE /me}; either way the person's data here is erased.
 * The flag is the one the other consumers of this event use.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.kafka.listeners.account-deleted", name = "enabled", havingValue = "true")
public class AccountDeletedKafkaListener extends AbstractDomainEventKafkaListener<AccountDeletedPayload> {

    public AccountDeletedKafkaListener(ObjectMapper objectMapper, AccountDeletedEventConsumer consumer) {
        super(objectMapper, AccountDeletedPayload.class, "AccountDeleted", consumer);
    }

    @KafkaListener(
            topics = "${fitsync.kafka.topics.user-events:user-events}",
            groupId = "${fitsync.kafka.consumer-groups.user-service:user-service-consumer-group}-account-deleted",
            autoStartup = "${fitsync.kafka.listeners.account-deleted.enabled:false}")
    public void onMessage(String payload,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        handle(payload, topic, key);
    }
}
