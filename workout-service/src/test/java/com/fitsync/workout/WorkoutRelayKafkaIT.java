package com.fitsync.workout;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}",
        "fitsync.kafka.enabled=true",
        "fitsync.kafka.topics.workout-events=fitsync-workout-events-it",
        "fitsync.outbox.relay.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
@EnabledIfEnvironmentVariable(named = "KAFKA_INTEGRATION_TESTS", matches = "true")
class WorkoutRelayKafkaIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void relayPublishesCompletedWorkoutToKafkaTopic() throws Exception {
        String topic = "fitsync-workout-events-it";

        try (KafkaConsumer<String, String> consumer = consumer(topic)) {
            mockMvc.perform(post("/api/v1/workouts/sessions")
                            .with(authentication(TestJwt.authentication("USER")))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "planId": null,
                                      "name": "Kafka Relay Workout",
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

            mockMvc.perform(post("/api/v1/internal/outbox/publish")
                            .with(authentication(TestJwt.authentication("ADMIN")))
                            .with(csrf())
                            .param("batchSize", "10"))
                    .andExpect(status().isOk());

            ConsumerRecord<String, String> record = eventuallyConsume(consumer);
            assertThat(record.topic()).isEqualTo(topic);
            assertThat(record.key()).isEqualTo("11111111-1111-1111-1111-111111111111");
            assertThat(record.value()).contains("WorkoutLogged");
            assertThat(record.value()).contains("CARDIO");
        }
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "workout-relay-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private ConsumerRecord<String, String> eventuallyConsume(KafkaConsumer<String, String> consumer) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Expected workout event was not published to Kafka");
    }
}
