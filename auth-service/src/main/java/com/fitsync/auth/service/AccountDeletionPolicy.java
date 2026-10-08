package com.fitsync.backend.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.fitsync.backend.config.AccountDeletionProperties;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.exception.CodedApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Decides what a requested account deletion means for an account: it is refused for sign-in at once, it can be
 * cancelled until the grace period ends, and after that it stays refused for good.
 */
@Component
public class AccountDeletionPolicy {

    public static final String PENDING_CODE = "ACCOUNT_DELETION_PENDING";
    public static final String FINAL_CODE = "ACCOUNT_DELETION_FINAL";

    private final Duration gracePeriod;

    public AccountDeletionPolicy(AccountDeletionProperties properties) {
        this.gracePeriod = Duration.ofDays(properties.graceDays());
    }

    public Instant scheduledFor(Instant requestedAt) {
        return requestedAt == null ? null : requestedAt.plus(gracePeriod);
    }

    public boolean gracePeriodEnded(AppUser user, Instant now) {
        Instant scheduledFor = scheduledFor(user.getAccountDeletionRequestedAt());
        return scheduledFor != null && !now.isBefore(scheduledFor);
    }

    /**
     * Refuses an account that has asked to be deleted. This only stops new tokens from being issued: an
     * application token issued before the request stays valid until it expires, because tokens are not revoked.
     */
    public void requireNoPendingDeletion(AppUser user) {
        Instant requestedAt = user.getAccountDeletionRequestedAt();
        if (requestedAt != null) {
            throw new CodedApiException(
                    HttpStatus.FORBIDDEN,
                    PENDING_CODE,
                    "This account has been scheduled for deletion",
                    instants(requestedAt));
        }
    }

    public CodedApiException deletionFinal(AppUser user) {
        return new CodedApiException(
                HttpStatus.CONFLICT,
                FINAL_CODE,
                "The grace period for this account deletion has ended",
                instants(user.getAccountDeletionRequestedAt()));
    }

    private Map<String, String> instants(Instant requestedAt) {
        return Map.of(
                "requestedAt", requestedAt.toString(),
                "scheduledFor", scheduledFor(requestedAt).toString());
    }
}
