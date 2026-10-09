package com.fitsync.user.messaging;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import com.fitsync.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erases the person's data in user-service when their account is deleted: every row, the avatar images, and the
 * outbox rows of their events, which carry profile facts. Receiving the event twice erases nothing more, so a
 * redelivery is harmless.
 */
@Component
public class AccountDeletedEventConsumer implements DomainEventConsumer<AccountDeletedPayload> {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletedEventConsumer.class);

    // Resolved through the provider when an event arrives (SpotBugs EI_EXPOSE_REP2 on constructor injection).
    private final ObjectProvider<UserService> userService;
    private final OutboxEventRepository outboxEventRepository;

    public AccountDeletedEventConsumer(ObjectProvider<UserService> userService,
                                       OutboxEventRepository outboxEventRepository) {
        this.userService = userService;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<AccountDeletedPayload> event) {
        if (event.userId() == null) {
            return;
        }
        userService.getObject().purgeAccountData(event.userId());
        outboxEventRepository.purgeAllByUserId(event.userId());
        log.info("event=user_account_erased userId={} eventId={}",
                String.valueOf(event.userId()).replace("\r", "_").replace("\n", "_"),
                String.valueOf(event.eventId()).replace("\r", "_").replace("\n", "_"));
    }
}
