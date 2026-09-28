package com.fitsync.recommendation.coach.reading;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

    /** The stored reading for a user and a page, if there is one. */
    @Transactional(readOnly = true)
    Optional<CoachPageReadingEntity> find(UUID userId, ReadingSurface surface) {
        return repository.findByUserIdAndSurface(userId, surface);
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
        Optional<CoachPageReadingEntity> existing = repository.findByUserIdAndSurface(userId, surface);
        CoachPageReadingEntity row = existing.orElseGet(CoachPageReadingEntity::new);
        if (existing.isEmpty()) {
            row.setId(UUID.randomUUID());
            row.setUserId(userId);
            row.setSurface(surface);
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
            return repository.findByUserIdAndSurface(userId, surface).orElseThrow(() -> raceLost);
        }
    }

    /** Records a validated reading. Only applies if the row is still for the hash it was claimed for. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persistAccepted(UUID userId, ReadingSurface surface, String factsHash,
                         String headline, String body, List<Integer> citations, Instant now) {
        repository.findByUserIdAndSurface(userId, surface)
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
        repository.findByUserIdAndSurface(userId, surface)
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
     * equality check on a value with "hash" in its name regardless, and {@code WeeklyNarrativeService}
     * already established the fix for exactly this pattern in this module: compare the UTF-8 bytes
     * with a constant-time comparison instead of leaving the finding unaddressed.
     */
    static boolean hashesMatch(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
