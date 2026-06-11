package com.fitsync.workout;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.workout.messaging.OutboxEventStatus;
import com.fitsync.workout.messaging.entity.OutboxEventEntity;
import com.fitsync.workout.messaging.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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
    void completedSessionCreatesWorkoutLoggedOutboxEvent() throws Exception {
        mockMvc.perform(post("/api/v1/workouts/sessions")
                        .with(authentication(TestJwt.authentication("USER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "planId": null,
                                  "name": "Outbox Workout",
                                  "workoutType": "CARDIO",
                                  "status": "COMPLETED",
                                  "source": "MANUAL",
                                  "startedAt": "2026-06-11T10:00:00Z",
                                  "completedAt": "2026-06-11T10:30:00Z",
                                  "durationMinutes": 30,
                                  "caloriesBurned": 280,
                                  "perceivedExertion": 6,
                                  "notes": "steady pace",
                                  "exercises": []
                                }
                                """))
                .andExpect(status().isCreated());

        assertThat(outboxEventRepository.findTop100ByStatusOrderByDateTimeCreatedAsc(OutboxEventStatus.PENDING))
                .anySatisfy(event -> {
                    assertThat(event.getEventType()).isEqualTo("WorkoutLogged");
                    assertThat(event.getTopic()).isEqualTo("workout-events");
                    assertThat(event.getPayload()).contains("WorkoutLogged");
                    assertThat(event.getPayload()).contains("CARDIO");
                });
    }

    @Test
    void userCannotRelayPendingOutboxEvents() throws Exception {
        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(authentication(TestJwt.authentication("USER")))
                        .with(csrf())
                        .param("batchSize", "10"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRelayPendingOutboxEvents() throws Exception {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setEventId(UUID.randomUUID());
        event.setTopic("workout-events");
        event.setEventType("WorkoutLogged");
        event.setAggregateType("WorkoutSession");
        event.setAggregateId("aggregate-1");
        event.setEventVersion(1);
        event.setPayload("{\"eventType\":\"WorkoutLogged\"}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setDateTimeProduced(Instant.now());
        outboxEventRepository.save(event);

        mockMvc.perform(post("/api/v1/internal/outbox/publish")
                        .with(authentication(TestJwt.authentication("ADMIN")))
                        .with(csrf())
                        .param("batchSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedBatchSize").value(10))
                .andExpect(jsonPath("$.publishedCount").value(1));

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .extracting(OutboxEventEntity::getStatus)
                .isEqualTo(OutboxEventStatus.PUBLISHED);
    }
}
