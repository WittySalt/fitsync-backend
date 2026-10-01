package com.fitsync.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthAndUserApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
