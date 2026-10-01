package com.fitsync.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.fitsync.backend.messaging.OutboxEventStatus;
import com.fitsync.backend.messaging.entity.OutboxEventEntity;
import com.fitsync.backend.messaging.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "fitsync.auth.local-accounts-enabled=true")
@AutoConfigureMockMvc
class OutboxEventTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void registerCreatesUserRegisteredOutboxEvent() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "outbox@example.com",
                                  "displayName": "Outbox User",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isCreated());

        assertThat(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("UserRegistered");
                    assertThat(event.getTopic()).isEqualTo("user-events");
                    assertThat(event.getPayload()).contains("outbox@example.com");
                });
    }

    @Test
    void adminCanRelayPendingOutboxEvents() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "relay@example.com",
                                  "displayName": "Relay User",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(user("admin").roles("ADMIN"))
                        .param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBatchSize").value(10))
                .andExpect(jsonPath("$.publishedCount").value(1));

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .extracting(OutboxEventEntity::getStatus)
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }

    @Test
    void pendingCountReportsOutstandingEventsToAnAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "pending@example.com",
                                  "displayName": "Pending User",
                                  "password": "supersecret"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/internal/outbox/pending")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.oldestCreatedAt").isNotEmpty());
    }

    @Test
    void pendingCountRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(get("/api/v1/internal/outbox/pending")
                        .with(user("someone").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void relayRejectsOrdinaryUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(user("someone").roles("USER"))
                        .param("batchSize", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void relayRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .param("batchSize", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pendingCountRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/internal/outbox/pending"))
                .andExpect(status().isUnauthorized());
    }
}
