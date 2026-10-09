package com.fitsync.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.analytics.dto.AnalyticsAchievementsResponse;
import com.fitsync.analytics.dto.AnalyticsActivityCalendarResponse;
import com.fitsync.analytics.dto.AnalyticsBodyTrendsResponse;
import com.fitsync.analytics.dto.AnalyticsCompareResponse;
import com.fitsync.analytics.dto.AnalyticsDashboardResponse;
import com.fitsync.analytics.dto.AnalyticsGoalProgressResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsNutritionSummaryResponse;
import com.fitsync.analytics.dto.AnalyticsStreaksResponse;
import com.fitsync.analytics.dto.AnalyticsWeeklyReportResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSeriesResponse;
import com.fitsync.analytics.dto.AnalyticsWorkoutSummaryResponse;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Round-trips the return type of every {@code @Cacheable} analytics method through the cache's own Redis
 * serializer (no Redis needed), with the application's ObjectMapper. Each value is built by reflection with
 * every record component filled, collections as JDK immutables ({@code List.of}, {@code Set.of},
 * {@code Map.of}), nested records, enums and {@code java.time} values, so a type the serializer cannot
 * write or read back fails here instead of on the first cache hit in the cloud.
 */
@SpringBootTest(classes = JacksonAutoConfiguration.class)
class CacheValueSerializerRoundTripTests {

    @Autowired
    private ObjectMapper objectMapper;

    static Stream<Class<? extends Record>> cachedTypes() {
        return Stream.of(
                AnalyticsDashboardResponse.class,
                AnalyticsBodyTrendsResponse.class,
                AnalyticsWorkoutSummaryResponse.class,
                AnalyticsWorkoutSeriesResponse.class,
                AnalyticsActivityCalendarResponse.class,
                AnalyticsNutritionSummaryResponse.class,
                AnalyticsNutritionSeriesResponse.class,
                AnalyticsCompareResponse.class,
                AnalyticsGoalProgressResponse.class,
                AnalyticsStreaksResponse.class,
                AnalyticsWeeklyReportResponse.class,
                AnalyticsAchievementsResponse.class);
    }

    @ParameterizedTest
    @MethodSource("cachedTypes")
    void cachedValueSurvivesTheRedisSerializer(Class<? extends Record> type) {
        RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer(objectMapper);
        Object value = sample(type, 0);

        byte[] bytes = serializer.serialize(value);
        Object back = serializer.deserialize(bytes);

        assertThat(back).isInstanceOf(type);
        assertThat(back).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("cachedTypes")
    void emptyCollectionsSurviveTheRedisSerializer(Class<? extends Record> type) {
        RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer(objectMapper);
        Object value = sample(type, 0, true);

        assertThat(serializer.deserialize(serializer.serialize(value))).isEqualTo(value);
    }

    @Test
    void refusesATypeOutsideTheAllowList() {
        RedisSerializer<Object> serializer = CacheConfig.cacheValueSerializer(objectMapper);
        // A plain bean outside the allow list: the old allow-all validator would have instantiated it.
        byte[] foreign = "{\"@class\":\"org.springframework.util.StopWatch\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(foreign))
                .isInstanceOf(SerializationException.class)
                .hasStackTraceContaining("PolymorphicTypeValidator");
    }

    // ── reflective sample builder ────────────────────────────────────────────

    private static Object sample(Type type, int depth) {
        return sample(type, depth, false);
    }

    private static Object sample(Type type, int depth, boolean empty) {
        if (depth > 24) {
            throw new IllegalStateException("record nesting too deep at " + type);
        }
        if (type instanceof WildcardType wildcard) {
            return sample(wildcard.getUpperBounds()[0], depth, empty);
        }
        if (type instanceof ParameterizedType parameterized) {
            Class<?> raw = (Class<?>) parameterized.getRawType();
            Type[] args = parameterized.getActualTypeArguments();
            if (List.class.equals(raw) || Collection.class.equals(raw)) {
                return empty ? List.of() : List.of(sample(args[0], depth + 1, false), sample(args[0], depth + 1, false));
            }
            if (Set.class.equals(raw)) {
                return empty ? Set.of() : Set.of(sample(args[0], depth + 1, false));
            }
            if (Map.class.equals(raw)) {
                return empty ? Map.of() : Map.of(sample(args[0], depth + 1, false), sample(args[1], depth + 1, false));
            }
            if (Optional.class.equals(raw)) {
                return empty ? Optional.empty() : Optional.of(sample(args[0], depth + 1, false));
            }
            throw new IllegalStateException("no sample for " + type);
        }
        Class<?> cls = (Class<?>) type;
        if (cls == String.class) return "text";
        if (cls == int.class || cls == Integer.class) return 7;
        if (cls == long.class || cls == Long.class) return 7L;
        if (cls == double.class || cls == Double.class) return 7.5d;
        if (cls == float.class || cls == Float.class) return 7.5f;
        if (cls == short.class || cls == Short.class) return (short) 7;
        if (cls == boolean.class || cls == Boolean.class) return true;
        if (cls == BigDecimal.class) return new BigDecimal("78.40");
        if (cls == UUID.class) return UUID.fromString("11111111-1111-1111-1111-111111111111");
        if (cls == LocalDate.class) return LocalDate.of(2026, 10, 9);
        if (cls == LocalDateTime.class) return LocalDateTime.of(2026, 10, 9, 8, 30);
        if (cls == LocalTime.class) return LocalTime.of(8, 30);
        if (cls == Instant.class) return Instant.parse("2026-10-09T00:30:15.123Z");
        if (cls == OffsetDateTime.class) return OffsetDateTime.parse("2026-10-09T08:30:15+08:00");
        if (cls == ZonedDateTime.class) return ZonedDateTime.parse("2026-10-09T08:30:15+08:00[Asia/Singapore]");
        if (cls == YearMonth.class) return YearMonth.of(2026, 10);
        if (cls == Duration.class) return Duration.ofMinutes(45);
        if (cls == ZoneId.class) return ZoneId.of("Asia/Singapore");
        if (cls.isEnum()) return cls.getEnumConstants()[0];
        if (cls.isRecord()) return record(cls, depth, empty);
        if (List.class.isAssignableFrom(cls) || Map.class.isAssignableFrom(cls) || Set.class.isAssignableFrom(cls)) {
            throw new IllegalStateException("raw collection type without element type: " + cls);
        }
        throw new IllegalStateException("no sample for " + cls.getName() + "; add one to the builder");
    }

    private static Object record(Class<?> cls, int depth, boolean empty) {
        RecordComponent[] components = cls.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = sample(components[i].getGenericType(), depth + 1, empty);
        }
        try {
            Constructor<?> constructor = cls.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("cannot build " + cls.getName(), ex);
        }
    }
}
