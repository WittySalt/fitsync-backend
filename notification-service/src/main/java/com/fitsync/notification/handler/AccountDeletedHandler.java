package com.fitsync.notification.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitsync.contracts.events.DomainEventConsumer;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.notification.repository.EmailRepository;
import com.fitsync.notification.repository.GoalRepository;
import com.fitsync.notification.repository.PlanDayRepository;
import com.fitsync.notification.repository.RecipientRepository;
import com.fitsync.notification.repository.WeightRepository;

/**
 * The account is gone, so everything held about the user goes too: the address, the settings,
 * the plan, goals, weights and the record of every email, including ones still waiting.
 */
@Service
public class AccountDeletedHandler implements DomainEventConsumer<AccountDeletedPayload> {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletedHandler.class);

    private final EmailRepository emails;
    private final PlanDayRepository planDays;
    private final GoalRepository goals;
    private final WeightRepository weights;
    private final RecipientRepository recipients;

    public AccountDeletedHandler(EmailRepository emails, PlanDayRepository planDays, GoalRepository goals,
                                 WeightRepository weights, RecipientRepository recipients) {
        this.emails = emails;
        this.planDays = planDays;
        this.goals = goals;
        this.weights = weights;
        this.recipients = recipients;
    }

    @Override
    @Transactional
    public void consume(FitSyncDomainEvent<AccountDeletedPayload> event) {
        if (event.userId() == null) {
            return;
        }
        emails.deleteByUserId(event.userId());
        planDays.deleteByUserId(event.userId());
        goals.deleteByUserId(event.userId());
        weights.deleteByUserId(event.userId());
        recipients.deleteByUserId(event.userId());
        log.info("event=notification_user_purged eventId={}", event.eventId());
    }
}
