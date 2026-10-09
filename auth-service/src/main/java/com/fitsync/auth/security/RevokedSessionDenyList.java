package com.fitsync.auth.security;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the gateway that a session's access tokens must no longer be accepted, by writing
 * {@code fitsync:revoked-sid:<sid>} to Redis for as long as such a token can still be unexpired (the access-token
 * lifetime plus a minute of clock skew).
 *
 * <p>The database row is the source of truth; this list only shortens the time a revoked device keeps working.
 * The keys are written after the revoking transaction commits, so a rollback never blocks a live session. When
 * Redis cannot be reached the revocation still holds in the database and the device is signed out at its next
 * refresh, at most one access-token lifetime later. A warning is logged at most once a minute.
 */
@Component
public class RevokedSessionDenyList {

    public static final String KEY_PREFIX = "fitsync:revoked-sid:";

    private static final Logger LOGGER = LoggerFactory.getLogger(RevokedSessionDenyList.class);
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
    private static final long WARN_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final Duration entryTtl;
    private final AtomicLong lastWarningAt = new AtomicLong();

    public RevokedSessionDenyList(ObjectProvider<StringRedisTemplate> redisTemplateProvider, JwtService jwtService) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.entryTtl = jwtService.accessTokenLifetime().plus(CLOCK_SKEW);
    }

    /** Adds the sessions once the current transaction commits, or at once when there is none. */
    public void addAfterCommit(Collection<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        List<UUID> ids = List.copyOf(sessionIds);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(ids);
                }
            });
        } else {
            write(ids);
        }
    }

    public Duration entryTtl() {
        return entryTtl;
    }

    private void write(List<UUID> ids) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            warn();
            return;
        }
        try {
            for (UUID id : ids) {
                redis.opsForValue().set(KEY_PREFIX + id, "1", entryTtl);
            }
        } catch (RuntimeException ex) {
            // Redis being down must not fail a sign-out; the database revocation already holds.
            warn();
        }
    }

    /** A fixed message: no token, id or exception text goes into the log line. */
    private void warn() {
        long now = System.currentTimeMillis();
        long last = lastWarningAt.get();
        if (now - last >= WARN_INTERVAL_MILLIS && lastWarningAt.compareAndSet(last, now)) {
            LOGGER.warn("Could not write revoked sessions to the Redis deny-list; they stay usable at the gateway"
                    + " until their access tokens expire (at most one access-token lifetime)");
        }
    }
}
