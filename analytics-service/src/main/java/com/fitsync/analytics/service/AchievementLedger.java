package com.fitsync.analytics.service;

import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.entity.AnalyticsUserAchievementEntity;
import com.fitsync.analytics.entity.AnalyticsUserAchievementKey;
import com.fitsync.analytics.repository.AnalyticsUserAchievementRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The stored badges. Rows are only ever added: a badge earned once keeps its first date for good, so a
 * row that is already there is left as it is.
 *
 * <p>Two reads of the same user can race to add the same new badge (the Me page asks for the badges and
 * the footprint at once). Each row is therefore inserted in a transaction of its own, and an insert
 * that hits the primary key means the other read stored it first, which is fine: the caller re-reads
 * the ledger and shows the date that was stored.
 */
@Service
public class AchievementLedger {

    private final AnalyticsUserAchievementRepository repository;
    private final TransactionTemplate perRow;

    public AchievementLedger(AnalyticsUserAchievementRepository repository,
                             PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.perRow = new TransactionTemplate(transactionManager);
        this.perRow.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<AnalyticsUserAchievementEntity> stored(UUID userId) {
        return repository.findByUserId(userId);
    }

    /** Inserts each row that is not stored yet; a row stored meanwhile by another read is skipped. */
    public void add(List<AnalyticsUserAchievementEntity> earned) {
        for (AnalyticsUserAchievementEntity row : earned) {
            try {
                perRow.executeWithoutResult(status -> {
                    if (!repository.existsById(new AnalyticsUserAchievementKey(row.getUserId(), row.getCode()))) {
                        repository.saveAndFlush(row);
                    }
                });
            } catch (DataIntegrityViolationException alreadyStored) {
                // Another read stored this badge first; its date stands.
            }
        }
    }
}
