package com.fitsync.user;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.user.messaging.OutboxEventStatus;
import com.fitsync.user.messaging.entity.OutboxEventEntity;
import com.fitsync.user.messaging.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxPendingTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void clearOutbox() {
        outboxEventRepository.deleteAll();
    }

    @Test
    void adminSeesPendingOutboxCountAndOldestTimestamp() throws Exception {
        outboxEventRepository.save(pendingEvent("aggregate-1"));

        mockMvc.perform(get("/api/v1/internal/outbox/pending")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.oldestCreatedAt").isNotEmpty());
    }

    @Test
    void userCannotSeePendingOutboxCount() throws Exception {
        mockMvc.perform(get("/api/v1/internal/outbox/pending")
                        .with(user("someone").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOutboxCountRejectsMissingAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/internal/outbox/pending"))
                .andExpect(status().isUnauthorized());
    }

    private OutboxEventEntity pendingEvent(String aggregateId) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setTopic("user-events");
        event.setEventType("Test");
        event.setAggregateType("Test");
        event.setAggregateId(aggregateId);
        event.setEventVersion(1);
        event.setPayload("{}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setDateTimeProduced(Instant.now());
        return event;
    }
}
