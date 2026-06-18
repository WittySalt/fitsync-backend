package com.fitsync.analytics.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import com.fitsync.analytics.repository.AnalyticsUserDashboardSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsDailyRollupService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsDailyRollupService.class);
    private static final int BATCH_SIZE = 200;

    private final AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository;
    private final AnalyticsDailyRollupUserProcessor userProcessor;
    private final Clock clock;

    public AnalyticsDailyRollupService(AnalyticsUserDashboardSnapshotRepository dashboardSnapshotRepository,
                                       AnalyticsDailyRollupUserProcessor userProcessor,
                                       Clock clock) {
        this.dashboardSnapshotRepository = dashboardSnapshotRepository;
        this.userProcessor = userProcessor;
        this.clock = clock;
    }

    @Scheduled(cron = "${fitsync.analytics.rollup.cron}", zone = "UTC")
    public void runDailyRollup() {
        runDailyRollupOnce();
    }

    RollupSummary runDailyRollupOnce() {
        long startedAt = System.nanoTime();
        Instant evaluatedAt = Instant.now(clock);
        LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int processedUsers = 0;
        int failedUsers = 0;
        int page = 0;
        Slice<UUID> userIds;

        do {
            userIds = dashboardSnapshotRepository.findSnapshotUserIds(PageRequest.of(page, BATCH_SIZE));
            for (UUID userId : userIds.getContent()) {
                try {
                    userProcessor.rollupUser(userId, todayUtc, evaluatedAt);
                    processedUsers++;
                } catch (RuntimeException exception) {
                    failedUsers++;
                    log.warn("Analytics daily rollup failed userId={} exceptionType={} exceptionMessage={}",
                            sanitizeForLog(userId),
                            sanitizeForLog(exception.getClass().getName()),
                            sanitizeForLog(exception.getMessage()));
                }
            }
            page++;
        } while (userIds.hasNext());

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.info("Analytics daily rollup completed processedUsers={} failedUsers={} elapsedMs={}",
                processedUsers, failedUsers, elapsedMs);
        return new RollupSummary(processedUsers, failedUsers, elapsedMs);
    }

    record RollupSummary(int processedUsers, int failedUsers, long elapsedMs) {
    }

    private String sanitizeForLog(Object value) {
        return String.valueOf(value)
                .replace("\r", "_")
                .replace("\n", "_");
    }
}
