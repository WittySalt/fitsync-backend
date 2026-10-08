package com.fitsync.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.backend.entity.AppUser;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import com.fitsync.backend.messaging.repository.OutboxEventRepository;
import com.fitsync.backend.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "fitsync.auth.local-accounts-enabled=true",
        "fitsync.account-deletion.grace-days=7"
})
@AutoConfigureMockMvc
class AuthAndUserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void registerReturnsJwtToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new-alice@example.com",
                                  "displayName": "New Alice",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("new-alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void locallyIssuedTokenCanAccessMe() throws Exception {
        registerUser("local-me@example.com", "Local Me", "supersecret");
        String token = loginAndGetAccessToken("local-me@example.com", "supersecret");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("local-me@example.com"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"));
    }

    @Test
    void loginRejectsInvalidPassword() throws Exception {
        registerUser("charlie@example.com", "Charlie", "supersecret");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "charlie@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void securityOverviewRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/security/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapCreatesCognitoUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-1")
                                .claim("email", "cognito-user@example.com")
                                .claim("name", "Cognito User")
                                .claim("email_verified", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cognito-user@example.com"))
                .andExpect(jsonPath("$.displayName").value("Cognito User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-1")
                                .claim("email", "cognito-user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cognito-user@example.com"))
                .andExpect(jsonPath("$.authProvider").value("COGNITO"));

        mockMvc.perform(get("/api/v1/auth/security/overview")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-1")
                                .claim("email", "cognito-user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authProvider").value("COGNITO"))
                .andExpect(jsonPath("$.passwordManagedExternally").value(true))
                .andExpect(jsonPath("$.twoFactorEnabled").value(false))
                .andExpect(jsonPath("$.twoFactorConfigurable").value(true))
                .andExpect(jsonPath("$.recoveryEmail").value("cognito-user@example.com"))
                .andExpect(jsonPath("$.recoveryEmailUpdatable").value(false))
                .andExpect(jsonPath("$.sessionCount").value(1))
                .andExpect(jsonPath("$.sessionTrackingAvailable").value(true))
                .andExpect(jsonPath("$.securityLabel").value("COGNITO_PROTECTED"));
    }

    @Test
    void bootstrapPrefersTheNameClaimOverTheCognitoUsername() throws Exception {
        bootstrapAndExpectDisplayName("name-wins-sub", "name-wins@example.com",
                "Named Person", "9f1c2d3e-4a5b-4c6d-8e7f-0a1b2c3d4e5f", "Named Person");
    }

    @Test
    void bootstrapUsesAHumanCognitoUsernameWhenThereIsNoNameClaim() throws Exception {
        bootstrapAndExpectDisplayName("human-username-sub", "human-username@example.com",
                null, "handle_42", "handle_42");
    }

    @Test
    void bootstrapSkipsAUuidCognitoUsernameAndUsesTheEmailLocalPart() throws Exception {
        bootstrapAndExpectDisplayName("uuid-username-sub", "uuid.person@example.com",
                null, "9F1C2D3E-4A5B-4C6D-8E7F-0A1B2C3D4E5F", "uuid.person");
    }

    @Test
    void bootstrapSkipsACognitoUsernameEqualToTheSubject() throws Exception {
        bootstrapAndExpectDisplayName("same-as-subject", "subject.person@example.com",
                null, "same-as-subject", "subject.person");
    }

    private void bootstrapAndExpectDisplayName(String subject, String email, String nameClaim,
                                               String cognitoUsername, String expectedDisplayName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> {
                            jwt.subject(subject)
                                    .claim("email", email)
                                    .claim("cognito:username", cognitoUsername)
                                    .claim("email_verified", true);
                            if (nameClaim != null) {
                                jwt.claim("name", nameClaim);
                            }
                        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(expectedDisplayName));
    }

    @Test
    void localAccountCanUpdateRecoveryEmailWithCurrentPassword() throws Exception {
        registerUser("local-recovery@example.com", "Local Recovery", "supersecret");
        String token = loginAndGetAccessToken("local-recovery@example.com", "supersecret");

        mockMvc.perform(patch("/api/v1/auth/security/recovery-email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated-recovery@example.com",
                                  "currentPassword": "supersecret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.recoveryEmail").value("updated-recovery@example.com"))
                .andExpect(jsonPath("$.recoveryEmailUpdatable").value(true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "updated-recovery@example.com",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("updated-recovery@example.com"));
    }

    @Test
    void cognitoRecoveryEmailUpdateIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-9")
                                .claim("email", "cognito-recovery@example.com")
                                .claim("name", "Cognito Recovery")
                                .claim("email_verified", true))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/auth/security/recovery-email")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-9")
                                .claim("email", "cognito-recovery@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new-cognito@example.com",
                                  "currentPassword": "ignored-password"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Recovery email is managed by Cognito for this account"));
    }

    @Test
    void sessionsEndpointReturnsCurrentSessionSummary() throws Exception {
        registerUser("session-user@example.com", "Session User", "supersecret");
        String token = loginAndGetAccessToken("session-user@example.com", "supersecret");

        mockMvc.perform(get("/api/v1/auth/security/sessions")
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", "Mozilla/5.0 Chrome/125.0")
                        .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("current-session"))
                .andExpect(jsonPath("$[0].deviceLabel").value("This browser (Chrome)"))
                .andExpect(jsonPath("$[0].ipAddress").value("203.0.113.10"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].revocable").value(false))
                .andExpect(jsonPath("$[0].issuedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].expiresAt").isNotEmpty());
    }

    @Test
    void localAccountCanRequestAccountDeletionWithPasswordConfirmation() throws Exception {
        registerUser("delete-local@example.com", "Delete Local", "supersecret");
        String token = loginAndGetAccessToken("delete-local@example.com", "supersecret");

        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationText": "DELETE",
                                  "currentPassword": "supersecret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordConfirmationRequired").value(true));

        mockMvc.perform(get("/api/v1/auth/account-deletion")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void cognitoAccountCanRequestAccountDeletionWithoutPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-13")
                                .claim("email", "delete-cognito@example.com")
                                .claim("name", "Delete Cognito")
                                .claim("email_verified", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-13")
                                .claim("email", "delete-cognito@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationText": "DELETE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.passwordConfirmationRequired").value(false));
    }

    @Test
    void bootstrapRejectsExistingLocalEmail() throws Exception {
        registerUser("migrate-me@example.com", "Legacy User", "supersecret");

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-2")
                                .claim("email", "migrate-me@example.com")
                                .claim("name", "Migrated User")
                                .claim("email_verified", true))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A local account with this email already exists and requires manual migration"));
    }

    @Test
    void loginRejectsCognitoProvisionedUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-3")
                                .claim("email", "external-only@example.com")
                                .claim("name", "External Only")
                                .claim("email_verified", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "external-only@example.com",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void bootstrapRejectsUnverifiedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-4")
                                .claim("email", "unverified@example.com")
                                .claim("email_verified", false))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("A verified email is required for Cognito provisioning"));
    }

    @Test
    void existingErrorsKeepTheirFieldsAndGainNoCode() throws Exception {
        registerUser("shape@example.com", "Shape", "supersecret");

        String content = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "shape@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> fieldNames = new ArrayList<>();
        objectMapper.readTree(content).fieldNames().forEachRemaining(fieldNames::add);
        assertThat(fieldNames).containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path", "details");
    }

    @Test
    void bootstrapIsRefusedWhileADeletionIsPending() throws Exception {
        bootstrapOk("deletion-pending-sub", "deletion-pending@example.com");
        requestDeletion("deletion-pending-sub", "deletion-pending@example.com");

        String content = mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("deletion-pending-sub", "deletion-pending@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PENDING"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduledFor").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(content).get("data");
        Instant requestedAt = Instant.parse(data.get("requestedAt").asText());
        Instant scheduledFor = Instant.parse(data.get("scheduledFor").asText());
        AppUser stored = appUserRepository.findByExternalSubject("deletion-pending-sub").orElseThrow();
        assertThat(requestedAt).isEqualTo(stored.getAccountDeletionRequestedAt());
        assertThat(scheduledFor).isEqualTo(requestedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void anAcceptedDeletionRequestPublishesAccountDeletionRequestedOnce() throws Exception {
        bootstrapOk("deletion-event-sub", "deletion-event@example.com");
        AppUser user = appUserRepository.findByExternalSubject("deletion-event-sub").orElseThrow();
        assertThat(deletionEvents(user)).isEmpty();

        requestDeletion("deletion-event-sub", "deletion-event@example.com");
        // A second request while the first is pending is not a new acceptance.
        requestDeletion("deletion-event-sub", "deletion-event@example.com");

        List<OutboxEventEntity> events = deletionEvents(user);
        assertThat(events).hasSize(1);
        OutboxEventEntity event = events.get(0);
        assertThat(event.getTopic()).isEqualTo("user-events");
        assertThat(event.getAggregateId()).isEqualTo(user.getId().toString());
        Instant requestedAt = appUserRepository.findById(user.getId()).orElseThrow().getAccountDeletionRequestedAt();
        JsonNode payload = objectMapper.readTree(event.getPayload()).get("payload");
        // The column keeps microseconds while the event carries the instant as it was taken.
        Instant published = Instant.parse(payload.get("requestedAt").asText());
        assertThat(published.truncatedTo(ChronoUnit.MICROS)).isEqualTo(requestedAt.truncatedTo(ChronoUnit.MICROS));
        assertThat(Instant.parse(payload.get("scheduledFor").asText())).isEqualTo(published.plus(Duration.ofDays(7)));
    }

    @Test
    void aDeletionRequestedAgainAfterCancellingPublishesAgain() throws Exception {
        bootstrapOk("deletion-again-sub", "deletion-again@example.com");
        AppUser user = appUserRepository.findByExternalSubject("deletion-again-sub").orElseThrow();
        requestDeletion("deletion-again-sub", "deletion-again@example.com");
        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("deletion-again-sub", "deletion-again@example.com")))
                .andExpect(status().isOk());

        requestDeletion("deletion-again-sub", "deletion-again@example.com");

        assertThat(deletionEvents(user)).hasSize(2);
    }

    @Test
    void cancellingAPendingDeletionPublishesAccountDeletionCancelled() throws Exception {
        bootstrapOk("cancel-event-sub", "cancel-event@example.com");
        AppUser user = appUserRepository.findByExternalSubject("cancel-event-sub").orElseThrow();
        requestDeletion("cancel-event-sub", "cancel-event@example.com");
        Instant before = Instant.now();

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("cancel-event-sub", "cancel-event@example.com")))
                .andExpect(status().isOk());

        List<OutboxEventEntity> events = eventsOf(user, "AccountDeletionCancelled");
        assertThat(events).hasSize(1);
        OutboxEventEntity event = events.get(0);
        assertThat(event.getTopic()).isEqualTo("user-events");
        assertThat(event.getAggregateId()).isEqualTo(user.getId().toString());
        JsonNode payload = objectMapper.readTree(event.getPayload()).get("payload");
        assertThat(Instant.parse(payload.get("cancelledAt").asText())).isAfterOrEqualTo(before);
    }

    @Test
    void cancellingWithoutAPendingDeletionPublishesNothing() throws Exception {
        bootstrapOk("cancel-none-sub", "cancel-none@example.com");
        AppUser user = appUserRepository.findByExternalSubject("cancel-none-sub").orElseThrow();

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("cancel-none-sub", "cancel-none@example.com")))
                .andExpect(status().isOk());

        assertThat(eventsOf(user, "AccountDeletionCancelled")).isEmpty();
    }

    @Test
    void theDeletionStatusCarriesTheEndOfTheGracePeriod() throws Exception {
        bootstrapOk("status-sub", "status-user@example.com");

        mockMvc.perform(get("/api/v1/auth/account-deletion")
                        .with(cognitoJwt("status-sub", "status-user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.requestedAt").doesNotExist())
                .andExpect(jsonPath("$.scheduledFor").doesNotExist());

        requestDeletion("status-sub", "status-user@example.com");

        String content = mockMvc.perform(get("/api/v1/auth/account-deletion")
                        .with(cognitoJwt("status-sub", "status-user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        assertThat(Instant.parse(json.get("scheduledFor").asText()))
                .isEqualTo(Instant.parse(json.get("requestedAt").asText()).plus(Duration.ofDays(7)));
    }

    @Test
    void cancellingWithinTheGracePeriodRestoresBootstrap() throws Exception {
        bootstrapOk("cancel-sub", "cancel-user@example.com");
        requestDeletion("cancel-sub", "cancel-user@example.com");

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("cancel-sub", "cancel-user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.requestedAt").doesNotExist())
                .andExpect(jsonPath("$.scheduledFor").doesNotExist())
                .andExpect(jsonPath("$.passwordConfirmationRequired").value(false));

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("cancel-sub", "cancel-user@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void cancellingAfterTheGracePeriodIsRefusedAndTheAccountStaysClosed() throws Exception {
        bootstrapOk("final-sub", "final-user@example.com");
        requestDeletion("final-sub", "final-user@example.com");
        AppUser user = appUserRepository.findByExternalSubject("final-sub").orElseThrow();
        user.setAccountDeletionRequestedAt(Instant.now().minus(Duration.ofDays(8)));
        appUserRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("final-sub", "final-user@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_FINAL"))
                .andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduledFor").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/bootstrap")
                        .with(cognitoJwt("final-sub", "final-user@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PENDING"));
    }

    @Test
    void cancellingWithoutAPendingDeletionChangesNothing() throws Exception {
        bootstrapOk("nothing-pending-sub", "nothing-pending@example.com");

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("nothing-pending-sub", "nothing-pending@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_REQUESTED"));
    }

    @Test
    void cancellingRequiresCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingWithAnApplicationTokenIsRefused() throws Exception {
        registerUser("cancel-local@example.com", "Cancel Local", "supersecret");
        String applicationToken = loginAndGetAccessToken("cancel-local@example.com", "supersecret");

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .header("Authorization", "Bearer " + applicationToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancellingRequiresAVerifiedEmail() throws Exception {
        bootstrapOk("cancel-unverified-sub", "cancel-unverified@example.com");
        requestDeletion("cancel-unverified-sub", "cancel-unverified@example.com");

        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cancel-unverified-sub")
                                .claim("email", "cancel-unverified@example.com")
                                .claim("email_verified", false))))
                .andExpect(status().isUnauthorized());
        assertThat(appUserRepository.findByExternalSubject("cancel-unverified-sub").orElseThrow()
                .getAccountDeletionRequestedAt()).isNotNull();
    }

    @Test
    void cancellingForAnUnknownSubjectDoesNotCreateAUser() throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion/cancel")
                        .with(cognitoJwt("never-seen-sub", "never-seen@example.com")))
                .andExpect(status().isNotFound());

        assertThat(appUserRepository.findByExternalSubject("never-seen-sub")).isEmpty();
        assertThat(appUserRepository.findByEmailIgnoreCase("never-seen@example.com")).isEmpty();
    }

    @Test
    void localLoginIsRefusedWhileADeletionIsPending() throws Exception {
        registerUser("login-pending@example.com", "Login Pending", "supersecret");
        String applicationToken = loginAndGetAccessToken("login-pending@example.com", "supersecret");
        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .header("Authorization", "Bearer " + applicationToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationText": "DELETE",
                                  "currentPassword": "supersecret"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login-pending@example.com",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PENDING"))
                .andExpect(jsonPath("$.data.requestedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.scheduledFor").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login-pending@example.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    private List<OutboxEventEntity> deletionEvents(AppUser user) {
        return eventsOf(user, "AccountDeletionRequested");
    }

    private List<OutboxEventEntity> eventsOf(AppUser user, String eventType) {
        return outboxEventRepository.findAll().stream()
                .filter(event -> eventType.equals(event.getEventType()))
                .filter(event -> user.getId().equals(event.getUserId()))
                .toList();
    }

    private JwtRequestPostProcessor cognitoJwt(String subject, String email) {
        return jwt().jwt(jwt -> jwt
                .subject(subject)
                .claim("email", email)
                .claim("name", "Test Person")
                .claim("email_verified", true));
    }

    private void bootstrapOk(String subject, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/bootstrap").with(cognitoJwt(subject, email)))
                .andExpect(status().isOk());
    }

    private void requestDeletion(String subject, String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/account-deletion/request")
                        .with(cognitoJwt(subject, email))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "confirmationText": "DELETE"
                                }
                                """))
                .andExpect(status().isOk());
    }

    private void registerUser(String email, String displayName, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "displayName": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, displayName, password)))
                .andExpect(status().isCreated());
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        String content = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        return json.get("accessToken").asText();
    }
}
