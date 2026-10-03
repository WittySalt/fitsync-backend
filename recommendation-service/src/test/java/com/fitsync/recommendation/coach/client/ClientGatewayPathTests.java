package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The coach reads every service through the API gateway: one base address, and only the public
 * paths the gateway routes, with the caller's token and zone forwarded.
 */
class ClientGatewayPathTests {

    private static final String GATEWAY = "http://api-gateway:8088";
    private static final String CALLER = "caller-credential";

    private final CoachProperties properties = new CoachProperties(null, null, GATEWAY + "/", null, null);

    @Test
    @DisplayName("without a configured address the coach uses the local gateway")
    void defaultsToTheLocalGateway() {
        assertThat(new CoachProperties(null, null, null, null, null).apiBaseUrl()).isEqualTo("http://localhost:8088");
        assertThat(properties.apiBaseUrl()).isEqualTo(GATEWAY);
    }

    @Test
    @DisplayName("every path a client reads or writes is a public gateway path of the service that owns it")
    void everyPathIsAPublicGatewayPath() throws IllegalAccessException {
        Map<Class<?>, String> owners = Map.of(
                WorkoutCatalogueClient.class, "/api/v1/workouts/",
                NutritionCatalogueClient.class, "/api/v1/nutrition/",
                UserProfileClient.class, "/api/v1/users/",
                AnalyticsProgressClient.class, "/api/v1/analytics/");
        for (Map.Entry<Class<?>, String> owner : owners.entrySet()) {
            List<String> paths = paths(owner.getKey());
            assertThat(paths).as(owner.getKey().getSimpleName()).isNotEmpty();
            assertThat(paths).as(owner.getKey().getSimpleName())
                    .allSatisfy(path -> assertThat(path).startsWith("/api/v1/").startsWith(owner.getValue())
                            .doesNotContain("/internal"));
        }
    }

    @Test
    @DisplayName("each client calls the gateway with the caller's token and zone")
    void eachClientCallsTheGateway() {
        LocalDate day = LocalDate.parse("2026-09-28");

        RestClient.Builder workout = RestClient.builder();
        MockRestServiceServer workoutServer = expect(workout,
                GATEWAY + "/api/v1/workouts/progress/training?from=2026-09-28&to=2026-09-28");
        new WorkoutCatalogueClient(workout, properties, new ObjectMapper()).trainingProgress(CALLER, "UTC", day, day);
        workoutServer.verify();

        RestClient.Builder nutrition = RestClient.builder();
        MockRestServiceServer nutritionServer = expect(nutrition, GATEWAY + "/api/v1/nutrition/summary/daily?date=2026-09-28");
        new NutritionCatalogueClient(nutrition, properties, new ObjectMapper()).dailySummary(CALLER, day, "UTC");
        nutritionServer.verify();

        RestClient.Builder user = RestClient.builder();
        MockRestServiceServer userServer = expect(user, GATEWAY + "/api/v1/users/me/insights/progress");
        new UserProfileClient(user, properties, new ObjectMapper()).progressInsight(CALLER, "UTC");
        userServer.verify();

        RestClient.Builder analytics = RestClient.builder();
        MockRestServiceServer analyticsServer = expect(analytics,
                GATEWAY + "/api/v1/analytics/progress/food?from=2026-09-28&to=2026-09-28");
        new AnalyticsProgressClient(analytics, properties).foodProgress(CALLER, "UTC", day, day);
        analyticsServer.verify();
    }

    private static MockRestServiceServer expect(RestClient.Builder builder, String url) {
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(url))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andExpect(header("X-Timezone", "UTC"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        return server;
    }

    /** The path constants a client declares, which are the only paths it builds its requests from. */
    private static List<String> paths(Class<?> client) throws IllegalAccessException {
        List<String> paths = new ArrayList<>();
        for (Field field : client.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof String text && text.startsWith("/")) {
                    paths.add(text);
                }
            }
        }
        return paths;
    }
}
