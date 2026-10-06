package com.fitsync.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.repository.AnalyticsUserAchievementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** The ledger on the real schema: adding a badge that is already stored never fails and never rewrites it. */
@SpringBootTest
@ActiveProfiles("test")
class AchievementLedgerIntegrationTests {

    @Autowired
    private AchievementLedger ledger;

    @Autowired
    private AnalyticsUserAchievementRepository repository;

    private static AnalyticsUserAchievementEntity row(UUID userId, String earnedOn) {
        AnalyticsUserAchievementEntity row = new AnalyticsUserAchievementEntity();
        row.setUserId(userId);
        row.setCode("WORKOUTS_10");
        row.setEarnedOn(LocalDate.parse(earnedOn));
        row.setCreated(Instant.parse("2026-10-06T04:00:00Z"));
        return row;
    }

    @Test
    void addingTheSameBadgeAgainKeepsTheFirstDate() {
        UUID userId = UUID.randomUUID();

        ledger.add(List.of(row(userId, "2026-09-02")));
        ledger.add(List.of(row(userId, "2026-10-01")));

        assertThat(ledger.stored(userId)).singleElement()
                .satisfies(stored -> assertThat(stored.getEarnedOn()).isEqualTo(LocalDate.parse("2026-09-02")));
    }

    @Test
    void aNewRowForAStoredKeyIsInsertedNotMergedSoItCannotRewriteTheDate() {
        UUID userId = UUID.randomUUID();
        repository.saveAndFlush(row(userId, "2026-09-02"));

        // This is what the ledger's catch relies on when two reads race past the existence check.
        assertThatThrownBy(() -> repository.saveAndFlush(row(userId, "2026-10-01")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(repository.findByUserId(userId)).singleElement()
                .satisfies(stored -> assertThat(stored.getEarnedOn()).isEqualTo(LocalDate.parse("2026-09-02")));
    }
}
