package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fitsync.recommendation.coach.config.CoachProperties;
import com.fitsync.recommendation.coach.reading.NutritionInsight;
import com.fitsync.recommendation.coach.reading.ProgressInsight;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The two upstream insight reads a page reading is built from. */
class PageInsightClientTests {

    private static final String CALLER = "abc-credential";

    private static final String NUTRITION_BODY = """
            {"timezone":"UTC","period":"PREVIOUS_WEEK","periodStart":"2026-09-21","periodEnd":"2026-09-27",
             "daysLogged":7,"proteinPerDayGrams":163.8,"earlierProteinPerDayGrams":162.2,"proteinTargetGrams":140,
             "caloriesPerDay":2173,"earlierCaloriesPerDay":2153,"calorieTargetKcal":2100,
             "proteinByMeal":[{"mealType":"BREAKFAST","label":"Breakfast","gramsPerDay":30.3}],
             "somethingNew":true}""";

    private static final String PROGRESS_BODY = """
            {"timezone":"UTC","referenceDate":"2026-10-01","weightKg":71.1,"measuredOn":"2026-09-28","entries":16,
             "weeklyRateKg":-0.21,"rateWindowDays":28,"goalWeightKg":68.0,"goalDate":"2026-12-09",
             "projectedDate":"2027-01-10","series":[{"date":"2026-09-28","weightKg":71.1}]}""";

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CoachProperties properties = new CoachProperties(null, null, "http://api-gateway:8088", null, null);

    @Test
    @DisplayName("the nutrition insight is read with the caller's credential and parsed, unknown fields ignored")
    void readsTheNutritionInsight() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/nutrition/insights/page")))
                .andExpect(header("X-Timezone", "UTC"))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess(NUTRITION_BODY, MediaType.APPLICATION_JSON));

        NutritionInsight insight = new NutritionCatalogueClient(builder, properties, mapper).pageInsight(CALLER, "UTC");

        server.verify();
        assertThat(insight.period()).isEqualTo("PREVIOUS_WEEK");
        assertThat(insight.periodStart()).isEqualTo(LocalDate.parse("2026-09-21"));
        assertThat(insight.proteinPerDayGrams()).isEqualByComparingTo(new BigDecimal("163.8"));
        assertThat(insight.calorieTargetKcal()).isEqualByComparingTo(new BigDecimal("2100"));
        assertThat(insight.proteinByMeal()).hasSize(1);
        assertThat(insight.proteinByMeal().get(0).mealType()).isEqualTo("BREAKFAST");
    }

    @Test
    @DisplayName("a nutrition body that is not the contract, or a failing upstream, reads as null")
    void nutritionFailuresAreNull() {
        RestClient.Builder unreadable = RestClient.builder();
        MockRestServiceServer first = MockRestServiceServer.bindTo(unreadable).build();
        first.expect(method(HttpMethod.GET)).andRespond(withSuccess("[1, 2]", MediaType.APPLICATION_JSON));
        assertThat(new NutritionCatalogueClient(unreadable, properties, mapper).pageInsight(CALLER, "UTC")).isNull();

        RestClient.Builder failing = RestClient.builder();
        MockRestServiceServer second = MockRestServiceServer.bindTo(failing).build();
        second.expect(method(HttpMethod.GET)).andRespond(withServerError());
        assertThat(new NutritionCatalogueClient(failing, properties, mapper).pageInsight(CALLER, "UTC")).isNull();
    }

    @Test
    @DisplayName("the progress insight is read with the caller's credential and parsed, the series ignored")
    void readsTheProgressInsight() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/users/me/insights/progress")))
                .andExpect(header("X-Timezone", "UTC"))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess(PROGRESS_BODY, MediaType.APPLICATION_JSON));

        ProgressInsight insight = new UserProfileClient(builder, properties, mapper).progressInsight(CALLER, "UTC");

        server.verify();
        assertThat(insight.weightKg()).isEqualByComparingTo(new BigDecimal("71.1"));
        assertThat(insight.measuredOn()).isEqualTo(LocalDate.parse("2026-09-28"));
        assertThat(insight.entries()).isEqualTo(16);
        assertThat(insight.weeklyRateKg()).isEqualByComparingTo(new BigDecimal("-0.21"));
        assertThat(insight.projectedDate()).isEqualTo(LocalDate.parse("2027-01-10"));
    }

    @Test
    @DisplayName("a progress body that is not the contract, or a failing upstream, reads as null")
    void progressFailuresAreNull() {
        RestClient.Builder unreadable = RestClient.builder();
        MockRestServiceServer first = MockRestServiceServer.bindTo(unreadable).build();
        first.expect(method(HttpMethod.GET)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));
        assertThat(new UserProfileClient(unreadable, properties, mapper).progressInsight(CALLER, "UTC")).isNull();

        RestClient.Builder failing = RestClient.builder();
        MockRestServiceServer second = MockRestServiceServer.bindTo(failing).build();
        second.expect(method(HttpMethod.GET)).andRespond(withServerError());
        assertThat(new UserProfileClient(failing, properties, mapper).progressInsight(CALLER, "UTC")).isNull();
    }
}
