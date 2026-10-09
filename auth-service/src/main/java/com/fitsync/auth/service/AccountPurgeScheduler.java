package com.fitsync.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the account purge once when the service is ready and then every day, unless
 * {@code fitsync.account-deletion.purge.enabled} is false. A run that fails is logged and the next one starts over;
 * the purge itself is safe to repeat.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.account-deletion.purge", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class AccountPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AccountPurgeScheduler.class);

    // Resolved at run time through the provider, so the scheduler does not hold the service itself
    // (SpotBugs EI_EXPOSE_REP2 on constructor injection; see the spotbugs-sast-hygiene skill).
    private final ObjectProvider<AccountPurgeService> accountPurgeService;

    public AccountPurgeScheduler(ObjectProvider<AccountPurgeService> accountPurgeService) {
        this.accountPurgeService = accountPurgeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        runSafely();
    }

    @Scheduled(cron = "${fitsync.account-deletion.purge.cron:0 30 3 * * *}", zone = "UTC")
    public void runDaily() {
        runSafely();
    }

    private void runSafely() {
        try {
            accountPurgeService.getObject().run();
        } catch (RuntimeException ex) {
            log.warn("event=account_purge_run_failed error={}", ex.getClass().getSimpleName());
        }
    }
}
