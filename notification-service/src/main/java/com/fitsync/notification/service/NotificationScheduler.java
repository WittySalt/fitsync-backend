package com.fitsync.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the tick on a fixed delay, so a slow pass never overlaps the next one. It is a single host
 * job without a lock, like the other scheduled jobs in the project. Tests switch it off and call
 * {@link NotificationTick} themselves, so the real clock never races the test clock.
 */
@Component
@ConditionalOnProperty(prefix = "fitsync.notification.scheduler", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class NotificationScheduler {

    private final NotificationTick tick;

    public NotificationScheduler(NotificationTick tick) {
        this.tick = tick;
    }

    @Scheduled(fixedDelayString = "${fitsync.notification.tick:PT5M}",
            initialDelayString = "${fitsync.notification.tick:PT5M}")
    public void run() {
        tick.run();
    }
}
