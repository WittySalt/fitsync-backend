package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The two reads behind the coach's view of what was eaten on one day. */
class NutritionMealsClientTests {

    private static final String CALLER = "abc-credential";

    private final CoachProperties properties = new CoachProperties(null, null, null,
            "http://nutrition-service:8083", "http://user-service:8081", null, null);

    @Test
    @DisplayName("the daily summary is read for one date and timezone with the caller's credential")
    void readsTheDailySummary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/nutrition/summary/daily?date=2026-09-30")))
                .andExpect(header("X-Timezone", "Asia/Singapore"))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("{\"totalCalories\":1850}", MediaType.APPLICATION_JSON));

        String body = new NutritionCatalogueClient(builder, properties, new ObjectMapper())
                .dailySummary(CALLER, LocalDate.parse("2026-09-30"), "Asia/Singapore");

        server.verify();
        assertThat(body).contains("1850");
    }

    @Test
    @DisplayName("the meals are read between two instants with the caller's credential")
    void readsTheMealsBetweenTwoInstants() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith(
                        "/api/v1/nutrition/meals?from=2026-09-29T16:00:00Z&to=2026-09-30T15:59:59.999999999Z")))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String body = new NutritionCatalogueClient(builder, properties, new ObjectMapper()).meals(
                CALLER, Instant.parse("2026-09-29T16:00:00Z"), Instant.parse("2026-09-30T15:59:59.999999999Z"));

        server.verify();
        assertThat(body).isEqualTo("[]");
    }

    @Test
    @DisplayName("the nutrient summary and the water of a day are read with the caller's credential")
    void readsTheDiaryFigures() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith(
                        "/api/v1/nutrition/nutrients/summary?from=2026-09-24&to=2026-09-30")))
                .andExpect(header("X-Timezone", "Asia/Singapore"))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("{\"daysLogged\":5}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/nutrition/water?date=2026-09-30")))
                .andExpect(header("X-Timezone", "Asia/Singapore"))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("{\"fromDrinksMl\":350}", MediaType.APPLICATION_JSON));
        NutritionCatalogueClient client = new NutritionCatalogueClient(builder, properties, new ObjectMapper());

        String nutrients = client.nutrientsSummary(CALLER, LocalDate.parse("2026-09-24"),
                LocalDate.parse("2026-09-30"), "Asia/Singapore");
        String water = client.water(CALLER, LocalDate.parse("2026-09-30"), "Asia/Singapore");

        server.verify();
        assertThat(nutrients).contains("daysLogged");
        assertThat(water).contains("350");
    }

    @Test
    @DisplayName("an older nutrition-service without the new endpoints reads as null")
    void anOlderServiceReadsAsNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        NutritionCatalogueClient client = new NutritionCatalogueClient(builder, properties, new ObjectMapper());

        assertThat(client.nutrientsSummary(CALLER, LocalDate.parse("2026-09-30"), LocalDate.parse("2026-09-30"),
                "UTC")).isNull();
        assertThat(client.water(CALLER, LocalDate.parse("2026-09-30"), "UTC")).isNull();
    }

    @Test
    @DisplayName("a failing upstream reads as null for both calls")
    void failuresAreNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());
        NutritionCatalogueClient client = new NutritionCatalogueClient(builder, properties, new ObjectMapper());

        assertThat(client.dailySummary(CALLER, LocalDate.parse("2026-09-30"), "UTC")).isNull();
        assertThat(client.meals(CALLER, Instant.parse("2026-09-30T00:00:00Z"), Instant.parse("2026-09-30T23:59:59Z")))
                .isNull();
    }
}
