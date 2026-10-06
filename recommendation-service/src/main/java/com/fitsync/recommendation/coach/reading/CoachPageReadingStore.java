package com.fitsync.recommendation.coach.reading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only place that writes {@link CoachPageReadingEntity} rows.
 *
 * <p>Every write runs in its own, brand-new transaction ({@link Propagation#REQUIRES_NEW}) rather
 * than joining whatever transaction the caller is in. That is what makes the concurrent-request case
 * safe: two requests racing to start a generation for the same user and surface both attempt to
 * insert the same (user_id, surface) row, the database's unique constraint lets exactly one of them
 * through, and the loser's own short transaction is the only thing that rolls back — it never poisons
 * a longer-lived transaction the caller might be holding open. The loser simply re-reads the row the
 * winner just wrote.
 */
@Component
class CoachPageReadingStore {

    static final Duration ACCEPTED_TTL = Duration.ofDays(7);
    static final Duration REJECTED_TTL = Duration.ofHours(1);
    /** How long an unreachable model is left alone before it is asked again. */
    static final Duration UNREACHABLE_TTL = Duration.ofMinutes(2);

    private final CoachPageReadingRepository repository;

    CoachPageReadingStore(CoachPageReadingRepository repository) {
        this.repository = repository;
    }

    /** The key of a page reading, which keeps one row per user and page. */
    static final String PAGE = "";

    /** The stored reading for a user and a page, if there is one. */
    @Transactional(readOnly = true)
    Optional<CoachPageReadingEntity> find(UUID userId, ReadingSurface surface) {
        return find(userId, surface, PAGE);
    }

    /** The stored reading for a user, a surface and a period, if there is one. */
    @Transactional(readOnly = true)
    Optional<CoachPageReadingEntity> find(UUID userId, ReadingSurface surface, String periodKey) {
        return repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey);
    }

    /** The stored rows of several periods, keyed by period. */
    @Transactional(readOnly = true)
    Map<String, CoachPageReadingEntity> findAll(UUID userId, ReadingSurface surface, Collection<String> periodKeys) {
        Map<String, CoachPageReadingEntity> rows = new HashMap<>();
        for (CoachPageReadingEntity row : repository.findByUserIdAndSurfaceAndPeriodKeyIn(userId, surface, periodKeys)) {
            rows.put(row.getPeriodKey(), row);
        }
        return rows;
    }

    /**
     * Records the facts a weekly review is written from. A row for other facts, or none, becomes a
     * row for these facts with no model text, which is how a changed current week is written again.
     * A row already holding these facts is left exactly as it is.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CoachPageReadingEntity recordFacts(UUID userId, ReadingSurface surface, String periodKey, String factsHash,
                                       String factsJson, Instant now) {
        Optional<CoachPageReadingEntity> existing =
                repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey);
        if (existing.isPresent() && hashesMatch(factsHash, existing.get().getFactsHash())) {
            return existing.get();
        }
        CoachPageReadingEntity row = existing.orElseGet(CoachPageReadingEntity::new);
        if (existing.isEmpty()) {
            row.setId(UUID.randomUUID());
            row.setUserId(userId);
            row.setSurface(surface);
            row.setPeriodKey(periodKey);
        }
        row.setFactsHash(factsHash);
        row.setFactsJson(factsJson);
        row.setOutcome(ReadingOutcome.FACTS_ONLY);
        row.setHeadline(null);
        row.setBody(null);
        row.setCitations(null);
        row.setSource(null);
        row.setStartedAt(now);
        row.setGeneratedAt(null);
        row.setExpiresAt(null);
        try {
            return repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException raceLost) {
            return repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey)
                    .orElseThrow(() -> raceLost);
        }
    }

    /**
     * Claims the row for a fresh generation attempt: creates it if absent, or overwrites it in place
     * if a different hash (or an abandoned attempt for the same hash) is there. Either way the
     * result is a {@code PENDING} row with {@code startedAt = now} for the given hash.
     *
     * <p>On a losing race against another request doing the same thing, the unique constraint fails
     * this transaction and this method returns whatever the winner wrote instead of throwing —
     * exactly the "handle the constraint violation by re-reading the row" behaviour a concurrent
     * caller needs.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CoachPageReadingEntity claimPending(UUID userId, ReadingSurface surface, String factsHash, Instant now) {
        return claimPending(userId, surface, PAGE, factsHash, now);
    }

    /** The claim above, for one period of a surface. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    CoachPageReadingEntity claimPending(UUID userId, ReadingSurface surface, String periodKey, String factsHash,
                                        Instant now) {
        Optional<CoachPageReadingEntity> existing =
                repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey);
        CoachPageReadingEntity row = existing.orElseGet(CoachPageReadingEntity::new);
        if (existing.isEmpty()) {
            row.setId(UUID.randomUUID());
            row.setUserId(userId);
            row.setSurface(surface);
            row.setPeriodKey(periodKey);
        }
        row.setFactsHash(factsHash);
        row.setOutcome(ReadingOutcome.PENDING);
        row.setHeadline(null);
        row.setBody(null);
        row.setCitations(null);
        row.setSource(null);
        row.setStartedAt(now);
        row.setGeneratedAt(null);
        row.setExpiresAt(null);
        try {
            return repository.saveAndFlush(row);
        } catch (DataIntegrityViolationException raceLost) {
            return repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey)
                    .orElseThrow(() -> raceLost);
        }
    }

    /** Records a validated reading. Only applies if the row is still for the hash it was claimed for. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistAccepted(UUID userId, ReadingSurface surface, String factsHash,
                         String headline, String body, List<Integer> citations, Instant now) {
        persistAccepted(userId, surface, PAGE, factsHash, headline, body, citations, now);
    }

    /** The accepted reading above, for one period of a surface. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistAccepted(UUID userId, ReadingSurface surface, String periodKey, String factsHash,
                         String headline, String body, List<Integer> citations, Instant now) {
        repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey)
                .filter(row -> hashesMatch(factsHash, row.getFactsHash()))
                .ifPresent(row -> {
                    row.setOutcome(ReadingOutcome.ACCEPTED);
                    row.setHeadline(headline);
                    row.setBody(body);
                    row.setCitations(citations.stream().map(String::valueOf).collect(Collectors.joining(",")));
                    row.setSource("MODEL");
                    row.setGeneratedAt(now);
                    row.setExpiresAt(now.plus(ACCEPTED_TTL));
                    repository.save(row);
                });
    }

    /**
     * Records that no reading could be kept for these facts, and for how long that stands. A failed
     * check stands for an hour. An unreachable model stands for two minutes.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistRejected(UUID userId, ReadingSurface surface, String factsHash, Instant now, Duration keptFor) {
        persistRejected(userId, surface, PAGE, factsHash, now, keptFor);
    }

    /** The rejection above, for one period of a surface. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistRejected(UUID userId, ReadingSurface surface, String periodKey, String factsHash, Instant now,
                         Duration keptFor) {
        repository.findByUserIdAndSurfaceAndPeriodKey(userId, surface, periodKey)
                .filter(row -> hashesMatch(factsHash, row.getFactsHash()))
                .ifPresent(row -> {
                    row.setOutcome(ReadingOutcome.REJECTED);
                    row.setGeneratedAt(now);
                    row.setExpiresAt(now.plus(keptFor));
                    repository.save(row);
                });
    }

    /**
     * Whether two facts hashes are the same string, compared with {@link MessageDigest#isEqual}
     * rather than {@link String#equals}.
     *
     * <p>Nothing here is a secret a timing attack could extract, but findsecbugs flags any
     * equality check on a value with "hash" in its name regardless, and the fix for exactly this pattern in this module is to compare the UTF-8 bytes
     * with a constant-time comparison instead of leaving the finding unaddressed.
     */
    static boolean hashesMatch(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
