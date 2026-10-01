package com.fitsync.nutrition.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fitsync.nutrition.config.UserServiceProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The lookup must give a value when user-service answers and an "unknown" in every other case, because a nutrition
 * request is never allowed to fail on someone else's outage. A real local HTTP server stands in for user-service.
 */
class UserServicePersonLookupTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-02T00:00:00Z"), ZoneOffset.UTC);

    private HttpServer server;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();
    private final Map<String, String> receivedAuthorization = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private String authorization;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            receivedAuthorization.put(path, String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            byte[] body = bodies.getOrDefault(path, "{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statuses.getOrDefault(path, 200), body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        authorization = "Bearer " + UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void stopServer() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    private UserServicePersonLookup lookup() {
        return lookup(true, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private UserServicePersonLookup lookup(boolean enabled, String baseUrl) {
        return new UserServicePersonLookup(new UserServiceProperties(enabled, baseUrl, null, null), CLOCK);
    }

    @Test
    void readsSexAndAgeFromTheProfileAndForwardsTheCallersToken() {
        bodies.put("/api/v1/users/me/profile", "{\"gender\": \"FEMALE\", \"birthDate\": \"1990-10-03\"}");

        PersonBasics basics = lookup().basics();

        assertThat(basics.sex()).isEqualTo("FEMALE");
        assertThat(basics.age()).isEqualTo(35);
        assertThat(receivedAuthorization.get("/api/v1/users/me/profile")).isEqualTo(authorization);
    }

    @Test
    void aGenderThatDecidesNoReferenceLeavesSexUnknownButKeepsTheAge() {
        bodies.put("/api/v1/users/me/profile", "{\"gender\": \"NON_BINARY\", \"birthDate\": \"2000-01-01\"}");

        PersonBasics basics = lookup().basics();

        assertThat(basics.sex()).isNull();
        assertThat(basics.age()).isEqualTo(26);
    }

    @Test
    void aMissingOrImpossibleBirthDateLeavesTheAgeUnknown() {
        bodies.put("/api/v1/users/me/profile", "{\"gender\": \"MALE\", \"birthDate\": \"2999-01-01\"}");
        assertThat(lookup().basics()).isEqualTo(new PersonBasics("MALE", null));

        bodies.put("/api/v1/users/me/profile", "{\"gender\": \"MALE\", \"birthDate\": \"not a date\"}");
        assertThat(lookup().basics()).isEqualTo(new PersonBasics("MALE", null));

        bodies.put("/api/v1/users/me/profile", "{\"gender\": \"MALE\"}");
        assertThat(lookup().basics()).isEqualTo(new PersonBasics("MALE", null));
    }

    @Test
    void readsTheLatestWeightAndTreatsAMissingOneAsUnknown() {
        bodies.put("/api/v1/users/me/body-metrics/latest", "{\"weightKg\": 72.5}");
        assertThat(lookup().latestWeightKg()).isEqualByComparingTo("72.5");

        statuses.put("/api/v1/users/me/body-metrics/latest", 404);
        assertThat(lookup().latestWeightKg()).isNull();

        statuses.put("/api/v1/users/me/body-metrics/latest", 200);
        bodies.put("/api/v1/users/me/body-metrics/latest", "{\"weightKg\": 0}");
        assertThat(lookup().latestWeightKg()).isNull();
    }

    @Test
    void readsTheDietaryPatternAndPassesOnOnlyKnownCodes() {
        // user-service writes the numeric code of the pattern.
        bodies.put("/api/v1/users/me/preferences", "{\"nutrition\": {\"dietaryPattern\": \"300004\"}}");
        assertThat(lookup().dietaryPattern()).isEqualTo("VEGAN");

        bodies.put("/api/v1/users/me/preferences", "{\"nutrition\": {\"dietaryPattern\": \"300003\"}}");
        assertThat(lookup().dietaryPattern()).isEqualTo("VEGETARIAN");

        bodies.put("/api/v1/users/me/preferences", "{\"nutrition\": {\"dietaryPattern\": \"vegan\"}}");
        assertThat(lookup().dietaryPattern()).isEqualTo("VEGAN");

        bodies.put("/api/v1/users/me/preferences", "{\"nutrition\": {\"dietaryPattern\": \"something else\"}}");
        assertThat(lookup().dietaryPattern()).isNull();

        bodies.put("/api/v1/users/me/preferences", "{\"nutrition\": null}");
        assertThat(lookup().dietaryPattern()).isNull();
    }

    @Test
    void anErrorStatusOrAnUnreadableBodyIsAnUnknown() {
        statuses.put("/api/v1/users/me/profile", 500);
        assertThat(lookup().basics()).isEqualTo(PersonBasics.unknown());

        statuses.put("/api/v1/users/me/profile", 200);
        bodies.put("/api/v1/users/me/profile", "this is not json");
        assertThat(lookup().basics()).isEqualTo(PersonBasics.unknown());
    }

    @Test
    void aServiceThatCannotBeReachedIsAnUnknown() {
        UserServicePersonLookup unreachable = lookup(true, "http://127.0.0.1:1");

        assertThat(unreachable.basics()).isEqualTo(PersonBasics.unknown());
        assertThat(unreachable.latestWeightKg()).isNull();
        assertThat(unreachable.dietaryPattern()).isNull();
    }

    @Test
    void withoutARequestOrATokenNothingIsAskedAndEverythingIsUnknown() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(lookup().basics()).isEqualTo(PersonBasics.unknown());

        MockHttpServletRequest withoutToken = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(withoutToken));
        assertThat(lookup().latestWeightKg()).isNull();

        assertThat(calls.get()).isZero();
    }

    @Test
    void aSwitchedOffLookupAsksNobody() {
        UserServicePersonLookup off = lookup(false, "http://127.0.0.1:" + server.getAddress().getPort());

        assertThat(off.basics()).isEqualTo(PersonBasics.unknown());
        assertThat(off.dietaryPattern()).isNull();
        assertThat(calls.get()).isZero();
    }

    @Test
    void thePropertiesFillInSafeDefaults() {
        UserServiceProperties defaults = new UserServiceProperties(null, " ", null, null);

        assertThat(defaults.enabled()).isTrue();
        assertThat(defaults.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(defaults.connectTimeout()).isPositive();
        assertThat(defaults.readTimeout()).isPositive();
        assertThat(new UserServiceProperties(true, "http://user-service:8081/", null, null).baseUrl())
                .isEqualTo("http://user-service:8081");
    }
}
