package com.fitsync.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void registerReturnsJwtToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "alice@example.com",
                                  "displayName": "Alice",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
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
                .andExpect(jsonPath("$.authProvider").value("COGNITO"))
                .andExpect(jsonPath("$.role").value("USER"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("cognito-sub-1")
                                .claim("email", "cognito-user@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cognito-user@example.com"))
                .andExpect(jsonPath("$.authProvider").value("COGNITO"));
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
}
