package com.fitsync.analytics;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fitsync.analytics.messaging.AccountDeletedEventConsumerImpl;
import com.fitsync.analytics.messaging.AccountDeletionProjectionService;
import com.fitsync.analytics.messaging.UserProfileUpdatedEventConsumerImpl;
import com.fitsync.analytics.messaging.UserTimezoneProjectionService;
import com.fitsync.contracts.events.FitSyncDomainEvent;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import org.junit.jupiter.api.Test;

class AccountAndProfileEventConsumerTests {

    private static final Instant AT = Instant.parse("2026-06-25T04:00:00Z");

    @Test
    void accountDeletedConsumerDelegatesToProjectionService() {
        AccountDeletionProjectionService projection = mock(AccountDeletionProjectionService.class);
        AccountDeletedEventConsumerImpl consumer = new AccountDeletedEventConsumerImpl(projection);
        FitSyncDomainEvent<AccountDeletedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(), "AccountDeleted", 1, AT, "user-service", UUID.randomUUID(),
                new AccountDeletedPayload(AT, "USER_REQUESTED"));

        consumer.consume(event);

        verify(projection).deleteUserData(event);
    }

    @Test
    void profileUpdatedConsumerProjectsTimezoneFromPayload() {
        UserTimezoneProjectionService projection = mock(UserTimezoneProjectionService.class);
        UserProfileUpdatedEventConsumerImpl consumer = new UserProfileUpdatedEventConsumerImpl(projection);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(), "UserProfileUpdated", 1, AT, "user-service", UUID.randomUUID(),
                new UserProfileUpdatedPayload("Ruixian", new BigDecimal("175.0"), "MODERATELY_ACTIVE",
                        true, List.of("timezone"), "Asia/Singapore"));

        consumer.consume(event);

        verify(projection).projectTimezone(event, "Asia/Singapore");
    }

    @Test
    void profileUpdatedConsumerHandlesNullPayload() {
        UserTimezoneProjectionService projection = mock(UserTimezoneProjectionService.class);
        UserProfileUpdatedEventConsumerImpl consumer = new UserProfileUpdatedEventConsumerImpl(projection);
        FitSyncDomainEvent<UserProfileUpdatedPayload> event = new FitSyncDomainEvent<>(
                UUID.randomUUID(), "UserProfileUpdated", 1, AT, "user-service", UUID.randomUUID(), null);

        consumer.consume(event);

        verify(projection).projectTimezone(eq(event), eq((String) null));
    }
}
