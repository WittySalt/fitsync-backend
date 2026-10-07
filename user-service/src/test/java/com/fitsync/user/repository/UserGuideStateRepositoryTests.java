package com.fitsync.user.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;

import com.fitsync.user.entity.UserGuideStateEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserGuideStateRepositoryTests {

    @Autowired
    private UserGuideStateRepository repository;

    @Test
    void savingTwiceKeepsOneRowAndTheLatestState() {
        UUID userId = UUID.randomUUID();
        repository.saveAndFlush(new UserGuideStateEntity(userId, "{\"a\":1}", Instant.parse("2026-10-07T01:00:00Z")));

        UserGuideStateEntity existing = repository.findById(userId).orElseThrow();
        existing.setState("{\"a\":2}");
        existing.setUpdatedAt(Instant.parse("2026-10-07T02:00:00Z"));
        repository.saveAndFlush(existing);

        UserGuideStateEntity reloaded = repository.findById(userId).orElseThrow();
        assertEquals("{\"a\":2}", reloaded.getState());
        assertEquals(Instant.parse("2026-10-07T02:00:00Z"), reloaded.getUpdatedAt());
        assertEquals(1, repository.findAll().stream().filter(row -> row.getUserId().equals(userId)).count());

        repository.deleteById(userId);
        assertTrue(repository.findById(userId).isEmpty());
    }
}
