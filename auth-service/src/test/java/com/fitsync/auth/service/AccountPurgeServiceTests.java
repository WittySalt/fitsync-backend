package com.fitsync.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.fitsync.auth.entity.AppUser;
import com.fitsync.auth.entity.AuthProvider;
import com.fitsync.auth.entity.AuthSession;
import com.fitsync.auth.identity.IdentityProviderUsers;
import com.fitsync.auth.messaging.OutboxEventStatus;
import com.fitsync.auth.messaging.entity.OutboxEventEntity;
import com.fitsync.auth.messaging.repository.OutboxEventRepository;
import com.fitsync.auth.repository.AppUserRepository;
import com.fitsync.auth.repository.AuthSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The grace-period purge against the real schema (H2 in PostgreSQL mode), with the Cognito call mocked. */
@SpringBootTest(properties = "fitsync.account-deletion.grace-days=30")
class AccountPurgeServiceTests {

    @Autowired
    private AccountPurgeService accountPurgeService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private IdentityProviderUsers identityProviderUsers;

    @BeforeEach
    void setUp() {
        reset(identityProviderUsers);
        when(identityProviderUsers.delete(anyString())).thenReturn(true);
    }

    @Test
    void claimsOnlyRequestsOlderThanTheGracePeriodThatAreStillPending() {
        AppUser expired = cognitoUser(daysAgo(31));
        AppUser withinGrace = cognitoUser(daysAgo(5));
        AppUser neverRequested = cognitoUser(null);
        AppUser cancelled = cognitoUser(daysAgo(40));
        cancelled.setAccountDeletionRequestedAt(null);
        appUserRepository.save(cancelled);

        accountPurgeService.run();

        assertThat(reload(expired).getAccountPurgeStartedAt()).isNotNull();
        assertThat(reload(withinGrace).getAccountPurgeStartedAt()).isNull();
        assertThat(reload(neverRequested).getAccountPurgeStartedAt()).isNull();
        assertThat(reload(cancelled).getAccountPurgeStartedAt()).isNull();
        assertThat(accountDeletedEvents(withinGrace)).isEmpty();
        assertThat(accountDeletedEvents(neverRequested)).isEmpty();
        assertThat(accountDeletedEvents(cancelled)).isEmpty();
    }

    @Test
    void aClaimPublishesAccountDeletedThroughTheOutbox() {
        AppUser user = cognitoUser(daysAgo(31));

        accountPurgeService.run();

        AppUser claimed = reload(user);
        assertThat(accountDeletedEvents(user)).singleElement().satisfies(event -> {
            assertThat(event.getEventId()).isEqualTo(claimed.getAccountPurgeEventId());
            assertThat(event.getTopic()).isEqualTo("user-events");
            assertThat(event.getAggregateId()).isEqualTo(user.getId().toString());
            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
            assertThat(event.getPayload()).contains("\"reason\":\"GRACE_PERIOD_ENDED\"");
            // The event names the account by id only.
            assertThat(event.getPayload()).doesNotContain(user.getEmail());
        });
    }

    @Test
    void repeatedRunsClaimAnAccountOnce() {
        AppUser user = cognitoUser(daysAgo(31));

        accountPurgeService.run();
        accountPurgeService.run();

        assertThat(accountDeletedEvents(user)).hasSize(1);
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        assertThat(inTransaction(() -> appUserRepository.claimForPurge(user.getId(), cutoff, Instant.now()))).isZero();
    }

    @Test
    void theAccountIsKeptUntilAccountDeletedIsPublished() {
        AppUser user = cognitoUser(daysAgo(31));

        accountPurgeService.run();
        accountPurgeService.run();

        assertThat(appUserRepository.findById(user.getId())).isPresent();
        verify(identityProviderUsers, never()).delete(anyString());
    }

    @Test
    void oncePublishedTheCognitoUserAccountSessionsAndOutboxRowsAreErased() {
        AppUser user = cognitoUser(daysAgo(31));
        AuthSession session = session(user);
        accountPurgeService.run();
        markPublished(user);

        accountPurgeService.run();

        verify(identityProviderUsers).delete(user.getExternalSubject());
        assertThat(appUserRepository.findById(user.getId())).isEmpty();
        assertThat(authSessionRepository.findById(session.getId())).isEmpty();
        assertThat(outboxEventRepository.findAll()).noneMatch(event -> user.getId().equals(event.getUserId()));
    }

    @Test
    void aCognitoFailureKeepsTheAccountAndTheNextRunTriesAgain() {
        AppUser user = cognitoUser(daysAgo(31));
        accountPurgeService.run();
        markPublished(user);
        when(identityProviderUsers.delete(user.getExternalSubject())).thenThrow(new IllegalStateException("down"));

        accountPurgeService.run();

        assertThat(appUserRepository.findById(user.getId())).isPresent();
        assertThat(accountDeletedEvents(user)).hasSize(1);

        reset(identityProviderUsers);
        when(identityProviderUsers.delete(anyString())).thenReturn(true);
        accountPurgeService.run();

        verify(identityProviderUsers, times(1)).delete(user.getExternalSubject());
        assertThat(appUserRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void withCognitoDeletionSwitchedOffTheAccountIsStillErased() {
        AppUser user = cognitoUser(daysAgo(31));
        when(identityProviderUsers.delete(anyString())).thenReturn(false);
        accountPurgeService.run();
        markPublished(user);

        accountPurgeService.run();

        assertThat(appUserRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void aLocalAccountIsErasedWithoutCallingCognito() {
        AppUser user = cognitoUser(daysAgo(31));
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setExternalSubject(null);
        appUserRepository.save(user);
        accountPurgeService.run();
        markPublished(user);

        accountPurgeService.run();

        verify(identityProviderUsers, never()).delete(anyString());
        assertThat(appUserRepository.findById(user.getId())).isEmpty();
    }

    @Test
    void anAccountThatWasNotClaimedIsNeverDeletedByTheErasureStatement() {
        AppUser user = cognitoUser(daysAgo(5));

        assertThat(inTransaction(() -> appUserRepository.purgeClaimed(user.getId()))).isZero();
        assertThat(appUserRepository.findById(user.getId())).isPresent();
    }

    private AppUser cognitoUser(Instant deletionRequestedAt) {
        String subject = UUID.randomUUID().toString();
        AppUser user = new AppUser();
        user.setAuthProvider(AuthProvider.COGNITO);
        user.setExternalSubject(subject);
        user.setEmail("purge-" + subject + "@example.com");
        user.setDisplayName("Purge Test");
        user.setPasswordHash("{EXTERNAL}:" + subject);
        user.setAccountDeletionRequestedAt(deletionRequestedAt);
        return appUserRepository.save(user);
    }

    private AuthSession session(AppUser user) {
        Instant now = Instant.now();
        AuthSession session = new AuthSession();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setDeviceLabel("This browser (Chrome)");
        session.setRefreshTokenHash(UUID.randomUUID().toString().replace("-", ""));
        session.setCreatedAt(now);
        session.setLastSeenAt(now);
        session.setExpiresAt(now.plus(Duration.ofDays(30)));
        return authSessionRepository.save(session);
    }

    private void markPublished(AppUser user) {
        OutboxEventEntity event = outboxEventRepository.findById(reload(user).getAccountPurgeEventId()).orElseThrow();
        event.setStatus(OutboxEventStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
    }

    private List<OutboxEventEntity> accountDeletedEvents(AppUser user) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> user.getId().equals(event.getUserId()))
                .filter(event -> "AccountDeleted".equals(event.getEventType()))
                .toList();
    }

    private AppUser reload(AppUser user) {
        return appUserRepository.findById(user.getId()).orElseThrow();
    }

    private int inTransaction(java.util.function.IntSupplier work) {
        Integer result = new TransactionTemplate(transactionManager).execute(status -> work.getAsInt());
        return result == null ? -1 : result;
    }

    private static Instant daysAgo(int days) {
        return Instant.now().minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.MICROS);
    }
}
