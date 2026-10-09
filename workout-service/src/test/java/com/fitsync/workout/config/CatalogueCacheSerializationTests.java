package com.fitsync.workout.config;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.web.response.PageResponse;
import com.fitsync.workout.domain.ExerciseEquipment;
import com.fitsync.workout.domain.ExerciseStatus;
import com.fitsync.workout.domain.ExerciseType;
import com.fitsync.workout.domain.MuscleGroup;
import com.fitsync.workout.domain.WorkoutDifficulty;
import com.fitsync.workout.dto.ExerciseView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** What the Redis store will hold: a page of records, written as plain JSON and read back as the same page. */
class CatalogueCacheSerializationTests {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void aPageOfExercisesSurvivesTheRoundTripThroughRedis() {
        RedisCacheConfiguration configuration = cacheConfiguration();
        PageResponse<ExerciseView> page = PageResponse.of(List.of(new ExerciseView(
                UUID.randomUUID(), null, "bench-press", "Bench Press", "Press the bar", ExerciseType.STRENGTH,
                MuscleGroup.CHEST, ExerciseEquipment.BARBELL, WorkoutDifficulty.BEGINNER, ExerciseStatus.ACTIVE,
                false, Instant.parse("2026-10-01T08:00:00Z"), Instant.parse("2026-10-02T08:00:00Z"))),
                0, 20, 1, 1, true, true);

        ByteBuffer stored = configuration.getValueSerializationPair().write(page);
        Object readBack = configuration.getValueSerializationPair().read(stored.duplicate());

        assertThat(readBack).isEqualTo(page);
        // Bound to its type: no class name is written into the entry for a reader to trust.
        assertThat(StandardCharsets.UTF_8.decode(stored).toString()).doesNotContain("@class");
    }

    @Test
    void entriesExpireAfterTheConfiguredTtl() {
        assertThat(cacheConfiguration().getTtlFunction().getTimeToLive(new Object(), null))
                .isEqualTo(Duration.ofHours(1));
    }

    private RedisCacheConfiguration cacheConfiguration() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisCacheManager.RedisCacheManagerBuilder builder = RedisCacheManager.builder(connectionFactory);
        new CatalogueCacheConfig()
                .catalogueRedisCacheManagerBuilderCustomizer(objectMapper, connectionFactory, Duration.ofHours(1))
                .customize(builder);
        return builder.getCacheConfigurationFor(CatalogueCacheConfig.EXERCISE_SEARCH_CACHE).orElseThrow();
    }
}
