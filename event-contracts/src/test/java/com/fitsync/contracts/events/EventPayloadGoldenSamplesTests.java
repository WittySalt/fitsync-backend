package com.fitsync.contracts.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fitsync.contracts.events.analytics.StreakMilestoneReachedPayload;
import com.fitsync.contracts.events.analytics.WeeklyRecapGeneratedPayload;
import com.fitsync.contracts.events.nutrition.MealDeletedPayload;
import com.fitsync.contracts.events.nutrition.MealLoggedPayload;
import com.fitsync.contracts.events.nutrition.MealUpdatedPayload;
import com.fitsync.contracts.events.user.AccountDeletedPayload;
import com.fitsync.contracts.events.user.BodyMetricsRecordedPayload;
import com.fitsync.contracts.events.user.GoalUpdatedPayload;
import com.fitsync.contracts.events.user.UserPreferencesUpdatedPayload;
import com.fitsync.contracts.events.user.UserProfileUpdatedPayload;
import com.fitsync.contracts.events.user.UserRegisteredPayload;
import com.fitsync.contracts.events.workout.PersonalRecordAchievedPayload;
import com.fitsync.contracts.events.workout.WorkoutDeletedPayload;
import com.fitsync.contracts.events.workout.WorkoutLoggedPayload;
import com.fitsync.contracts.events.workout.WorkoutUpdatedPayload;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventPayloadGoldenSamplesTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void goldenSampleDeserializesAndPreservesAllCurrentFields(Sample sample) throws Exception {
        Map<String, Object> json = readObject(sample.resourcePath());

        Object payload = decodeRecord(sample.payloadType(), json);
        Map<String, Object> roundTripped = encodeRecord(payload);

        assertEquals(json, roundTripped);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void goldenSampleWithUnknownFieldStillDeserializes(Sample sample) throws Exception {
        Map<String, Object> json = readObject(sample.resourcePath());
        Map<String, Object> futureJson = new LinkedHashMap<>(json);
        futureJson.put("futureField", "ignored");

        Object payload = decodeRecord(sample.payloadType(), futureJson);
        Map<String, Object> roundTripped = encodeRecord(payload);

        assertEquals(json, roundTripped);
    }

    static Stream<Sample> samples() {
        return Stream.of(
                sample("workout/WorkoutLoggedPayload.json", WorkoutLoggedPayload.class),
                sample("workout/WorkoutUpdatedPayload.json", WorkoutUpdatedPayload.class),
                sample("workout/WorkoutDeletedPayload.json", WorkoutDeletedPayload.class),
                sample("workout/PersonalRecordAchievedPayload.json", PersonalRecordAchievedPayload.class),
                sample("nutrition/MealLoggedPayload.json", MealLoggedPayload.class),
                sample("nutrition/MealUpdatedPayload.json", MealUpdatedPayload.class),
                sample("nutrition/MealDeletedPayload.json", MealDeletedPayload.class),
                sample("user/UserRegisteredPayload.json", UserRegisteredPayload.class),
                sample("user/UserProfileUpdatedPayload.json", UserProfileUpdatedPayload.class),
                sample("user/UserPreferencesUpdatedPayload.json", UserPreferencesUpdatedPayload.class),
                sample("user/GoalUpdatedPayload.json", GoalUpdatedPayload.class),
                sample("user/BodyMetricsRecordedPayload.json", BodyMetricsRecordedPayload.class),
                sample("user/AccountDeletedPayload.json", AccountDeletedPayload.class),
                sample("analytics/StreakMilestoneReachedPayload.json", StreakMilestoneReachedPayload.class),
                sample("analytics/WeeklyRecapGeneratedPayload.json", WeeklyRecapGeneratedPayload.class));
    }

    private static Sample sample(String path, Class<? extends Record> payloadType) {
        return new Sample(path, payloadType);
    }

    private static Map<String, Object> readObject(String resourcePath) throws IOException {
        String fullPath = "contracts/" + resourcePath;
        try (InputStream stream = EventPayloadGoldenSamplesTests.class.getClassLoader()
                .getResourceAsStream(fullPath)) {
            assertTrue(stream != null, "Missing golden sample " + fullPath);
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            Object parsed = new JsonParser(json).parse();
            assertTrue(parsed instanceof Map<?, ?>, "Golden sample must be a JSON object: " + fullPath);
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) parsed;
            return object;
        }
    }

    private static Object decodeRecord(Class<? extends Record> type, Map<String, Object> json)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] parameterTypes = Stream.of(components)
                .map(RecordComponent::getType)
                .toArray(Class<?>[]::new);
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            assertTrue(json.containsKey(component.getName()),
                    () -> type.getSimpleName() + " golden sample is missing field " + component.getName());
            args[i] = convertValue(json.get(component.getName()), component.getType(), component.getGenericType());
        }
        Constructor<? extends Record> constructor = type.getDeclaredConstructor(parameterTypes);
        return constructor.newInstance(args);
    }

    private static Object convertValue(Object value, Class<?> targetType, java.lang.reflect.Type genericType)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        if (value == null) {
            return null;
        }
        if (targetType == String.class || targetType == Object.class) {
            return value;
        }
        if (targetType == UUID.class) {
            return UUID.fromString((String) value);
        }
        if (targetType == Instant.class) {
            return Instant.parse((String) value);
        }
        if (targetType == LocalDate.class) {
            return LocalDate.parse((String) value);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return ((Number) value).intValue();
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return value;
        }
        if (targetType == BigDecimal.class) {
            return new BigDecimal(value.toString());
        }
        if (List.class.isAssignableFrom(targetType)) {
            return convertList(value, genericType);
        }
        if (targetType.isRecord()) {
            @SuppressWarnings("unchecked")
            Class<? extends Record> recordType = (Class<? extends Record>) targetType;
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) value;
            return decodeRecord(recordType, object);
        }
        throw new IllegalArgumentException("Unsupported payload field type: " + targetType.getName());
    }

    private static List<?> convertList(Object value, java.lang.reflect.Type genericType)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        @SuppressWarnings("unchecked")
        List<Object> values = (List<Object>) value;
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return values;
        }
        java.lang.reflect.Type elementType = parameterizedType.getActualTypeArguments()[0];
        if (elementType == String.class) {
            return List.copyOf(values);
        }
        if (elementType instanceof Class<?> elementClass && elementClass.isRecord()) {
            List<Object> converted = new ArrayList<>();
            for (Object item : values) {
                @SuppressWarnings("unchecked")
                Map<String, Object> object = (Map<String, Object>) item;
                @SuppressWarnings("unchecked")
                Class<? extends Record> recordClass = (Class<? extends Record>) elementClass;
                converted.add(decodeRecord(recordClass, object));
            }
            return List.copyOf(converted);
        }
        throw new IllegalArgumentException("Unsupported list element type: " + elementType);
    }

    private static Map<String, Object> encodeRecord(Object value)
            throws InvocationTargetException, IllegalAccessException {
        Map<String, Object> object = new LinkedHashMap<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object componentValue = component.getAccessor().invoke(value);
            object.put(component.getName(), encodeValue(componentValue));
        }
        return object;
    }

    private static Object encodeValue(Object value) throws InvocationTargetException, IllegalAccessException {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Integer) {
            return value;
        }
        if (value instanceof UUID || value instanceof Instant || value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof List<?> list) {
            List<Object> encoded = new ArrayList<>();
            for (Object item : list) {
                encoded.add(encodeValue(item));
            }
            return List.copyOf(encoded);
        }
        if (value.getClass().isRecord()) {
            return encodeRecord(value);
        }
        throw new IllegalArgumentException("Unsupported payload field value: " + value.getClass().getName());
    }

    private record Sample(String resourcePath, Class<? extends Record> payloadType) {
        @Override
        public String toString() {
            return payloadType.getSimpleName();
        }
    }

    private static final class JsonParser {
        private final String input;
        private int position;

        private JsonParser(String input) {
            this.input = input;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (position != input.length()) {
                throw new IllegalArgumentException("Unexpected trailing JSON at position " + position);
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            char next = input.charAt(position);
            if (next == '{') {
                return parseObject();
            }
            if (next == '[') {
                return parseArray();
            }
            if (next == '"') {
                return parseString();
            }
            if (next == 't' || next == 'f') {
                return parseBoolean();
            }
            if (next == 'n') {
                position += 4;
                return null;
            }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                position++;
                return object;
            }
            do {
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
            } while (consume(','));
            expect('}');
            return object;
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                position++;
                return values;
            }
            do {
                values.add(parseValue());
                skipWhitespace();
            } while (consume(','));
            expect(']');
            return values;
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (position < input.length()) {
                char current = input.charAt(position++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    char escaped = input.charAt(position++);
                    builder.append(switch (escaped) {
                        case '"', '\\', '/' -> escaped;
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> throw new IllegalArgumentException("Unsupported escape: " + escaped);
                    });
                } else {
                    builder.append(current);
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Boolean parseBoolean() {
            if (input.startsWith("true", position)) {
                position += 4;
                return true;
            }
            if (input.startsWith("false", position)) {
                position += 5;
                return false;
            }
            throw new IllegalArgumentException("Invalid boolean at position " + position);
        }

        private Number parseNumber() {
            int start = position;
            boolean decimal = false;
            while (position < input.length()) {
                char current = input.charAt(position);
                if ((current >= '0' && current <= '9') || current == '-' || current == '+'
                        || current == '.' || current == 'e' || current == 'E') {
                    decimal = decimal || current == '.' || current == 'e' || current == 'E';
                    position++;
                } else {
                    break;
                }
            }
            String number = input.substring(start, position);
            if (decimal) {
                return new BigDecimal(number);
            }
            return Integer.valueOf(number);
        }

        private boolean consume(char expected) {
            if (peek(expected)) {
                position++;
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            skipWhitespace();
            return position < input.length() && input.charAt(position) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (position >= input.length() || input.charAt(position) != expected) {
                throw new IllegalArgumentException("Expected " + expected + " at position " + position);
            }
            position++;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }
    }
}
