package com.fitsync.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
class InternalUserLookupTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminFindsAnExistingUserByEmail() throws Exception {
        registerUser("lookup-me@example.com", "Lookup Me", "supersecret");

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("admin").roles("ADMIN"))
                        .content("{\"email\":\"" + "Lookup-Me@Example.com" + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lookup-me@example.com"))
                .andExpect(jsonPath("$.displayName").value("Lookup Me"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.userId").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void adminLookupReturnsNotFoundForUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("admin").roles("ADMIN"))
                        .content("{\"email\":\"" + "nobody-here@example.com" + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminLookupRejectsAMalformedEmail() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("admin").roles("ADMIN"))
                        .content("{\"email\":\"" + "not-an-email" + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminLookupRejectsAnOverlongEmail() throws Exception {
        String overlongLocalPart = "a".repeat(115);
        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("admin").roles("ADMIN"))
                        .content("{\"email\":\"" + overlongLocalPart + "@example.com" + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookupRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(user("someone").roles("USER"))
                        .content("{\"email\":\"" + "anyone@example.com" + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + "anyone@example.com" + "\"}"))
                .andExpect(status().isUnauthorized());
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
