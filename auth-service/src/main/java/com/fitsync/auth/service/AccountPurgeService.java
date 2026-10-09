package com.fitsync.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fitsync.auth.config.AccountDeletionProperties;
import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.entity.AuthProvider;
import com.fitsync.auth.identity.IdentityProviderUsers;
import com.fitsync.auth.messaging.DomainEventPublisher;
import com.fitsync.auth.messaging.OutboxEventStatus;
import com.fitsync.auth.messaging.repository.OutboxEventRepository;
import com.fitsync.auth.repository.AppUserRepository;
import com.fitsync.auth.repository.AuthSessionRepository;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Erases accounts whose deletion grace period has ended. A run has two steps.
 *
 * <p>First it claims each due account with a conditional update and, in the same transaction, writes
 * {@code AccountDeleted} to the outbox, so every service that keeps the person's data erases it. The claim only
 * succeeds once per account, which makes the run safe to repeat and to run on several instances at once.
 *
 * <p>Then, for each claimed account whose {@code AccountDeleted} the relay has published, it deletes the Cognito
 * user and then the account row, its sessions and its outbox rows. A Cognito failure leaves the account claimed,
 * and the next run tries again.
 *
 * <p>The account row is deleted outright. Every other user-facing delete in FitSync is a soft delete, but this one
 * carries out the person's right to erasure, so nothing that identifies them may be kept.
 */
@Service
public class AccountPurgeService {

    static final String REASON_GRACE_PERIOD_ENDED = "GRACE_PERIOD_ENDED";

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeService.class);

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final IdentityProviderUsers identityProviderUsers;
    private final TransactionTemplate transactionTemplate;
    private final Duration gracePeriod;
    private final int batchSize;
    private final Clock clock;

    @Autowired
    public AccountPurgeService(AppUserRepository appUserRepository,
                               AuthSessionRepository authSessionRepository,
                               OutboxEventRepository outboxEventRepository,
                               DomainEventPublisher domainEventPublisher,
                               IdentityProviderUsers identityProviderUsers,
                               PlatformTransactionManager transactionManager,
                               AccountDeletionProperties properties) {
        this(appUserRepository, authSessionRepository, outboxEventRepository, domainEventPublisher,
                identityProviderUsers, transactionManager, properties, Clock.systemUTC());
    }

    AccountPurgeService(AppUserRepository appUserRepository,
                        AuthSessionRepository authSessionRepository,
                        OutboxEventRepository outboxEventRepository,
                        DomainEventPublisher domainEventPublisher,
                        IdentityProviderUsers identityProviderUsers,
                        PlatformTransactionManager transactionManager,
                        AccountDeletionProperties properties,
                        Clock clock) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.identityProviderUsers = identityProviderUsers;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.gracePeriod = Duration.ofDays(properties.graceDays());
        this.batchSize = properties.purge().batchSize();
        this.clock = clock;
    }

    /** One full run: claims the accounts that are due, then erases the claimed ones that consumers have heard of. */
    public PurgeRunResult run() {
        int claimed = claimDueAccounts();
        int erased = finishSignalledPurges();
        if (claimed > 0 || erased > 0) {
            log.info("event=account_purge_run claimed={} erased={}", claimed, erased);
        }
        return new PurgeRunResult(claimed, erased);
    }

    /** Claims every due account, announcing each with AccountDeleted; returns how many this run claimed. */
    int claimDueAccounts() {
        Instant now = now();
        Instant cutoff = now.minus(gracePeriod);
        List<UUID> due = appUserRepository.findIdsDueForPurge(cutoff, PageRequest.of(0, batchSize));
        int claimed = 0;
        for (UUID userId : due) {
            Boolean won = transactionTemplate.execute(status -> claim(userId, cutoff, now));
            if (Boolean.TRUE.equals(won)) {
                claimed++;
                log.info("event=account_purge_claimed userId={}", forLog(userId));
            }
        }
        return claimed;
    }

    private boolean claim(UUID userId, Instant cutoff, Instant now) {
        if (appUserRepository.claimForPurge(userId, cutoff, now) != 1) {
            // Cancelled meanwhile, or claimed by another instance.
            return false;
        }
        AppUser user = appUserRepository.findById(userId).orElseThrow();
        UUID eventId = UUID.randomUUID();
        domainEventPublisher.publish("user-events", "AppUser", userId.toString(), new FitSyncDomainEvent<>(
                eventId,
                "AccountDeleted",
                1,
                now,
                "auth-service",
                userId,
                new AccountDeletedPayload(now, REASON_GRACE_PERIOD_ENDED)));
        user.setAccountPurgeEventId(eventId);
        appUserRepository.save(user);
        return true;
    }

    /** Erases the claimed accounts whose AccountDeleted has been published; returns how many were erased. */
    int finishSignalledPurges() {
        int erased = 0;
        for (AppUser user : appUserRepository.findPurgesInProgress(PageRequest.of(0, batchSize))) {
            if (!signalled(user)) {
                continue;
            }
            UUID userId = user.getId();
            try {
                deleteIdentityProviderUser(user);
            } catch (RuntimeException ex) {
                // Logged without the exception message, which can carry the subject; tried again next run.
                log.warn("event=account_purge_identity_provider_failed userId={} error={}",
                        forLog(userId), forLog(ex.getClass().getSimpleName()));
                continue;
            }
            transactionTemplate.executeWithoutResult(status -> {
                authSessionRepository.purgeAllByUserId(userId);
                outboxEventRepository.purgeAllByUserId(userId);
                appUserRepository.purgeClaimed(userId);
            });
            erased++;
            log.info("event=account_purge_erased userId={}", forLog(userId));
        }
        return erased;
    }

    private boolean signalled(AppUser user) {
        UUID eventId = user.getAccountPurgeEventId();
        return eventId != null && outboxEventRepository.findById(eventId)
                .map(event -> event.getStatus() == OutboxEventStatus.PUBLISHED)
                .orElse(false);
    }

    private void deleteIdentityProviderUser(AppUser user) {
        if (user.getAuthProvider() != AuthProvider.COGNITO || !StringUtils.hasText(user.getExternalSubject())) {
            return;
        }
        if (!identityProviderUsers.delete(user.getExternalSubject())) {
            log.warn("event=account_purge_identity_provider_skipped userId={} reason=cognito_delete_disabled",
                    forLog(user.getId()));
        }
    }

    /** Only ids and class names are logged; line breaks are replaced all the same (CWE-117). */
    private static String forLog(Object value) {
        return String.valueOf(value).replace("\r", "_").replace("\n", "_");
    }

    /** Microsecond precision, as the database keeps it, so the stored and published times agree. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** How many accounts a run claimed and how many it erased. */
    public record PurgeRunResult(int claimed, int erased) {
    }
}
