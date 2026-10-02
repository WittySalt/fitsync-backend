package com.fitsync.recommendation.coach.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.config.CoachProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The two reads behind the coach's recipe tools. */
class NutritionRecipesClientTests {

    private static final String CALLER = "abc-credential";
    private static final UUID RECIPE = UUID.fromString("6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb");

    private final CoachProperties properties = new CoachProperties(null, null, null,
            "http://nutrition-service:8083", "http://user-service:8081", null, null);

    @Test
    @DisplayName("one recipe is read by id with the caller's credential")
    void readsOneRecipe() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/nutrition/recipes/" + RECIPE)))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("{\"name\":\"Garides Saganaki\"}", MediaType.APPLICATION_JSON));

        String body = new NutritionCatalogueClient(builder, properties, new ObjectMapper()).recipe(CALLER, RECIPE);

        server.verify();
        assertThat(body).contains("Garides Saganaki");
    }

    @Test
    @DisplayName("a recipe the caller cannot see, or a failing service, reads as null")
    void aMissingRecipeIsNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());
        NutritionCatalogueClient client = new NutritionCatalogueClient(builder, properties, new ObjectMapper());

        assertThat(client.recipe(CALLER, RECIPE)).isNull();
        assertThat(client.recipe(CALLER, RECIPE)).isNull();
    }

    @Test
    @DisplayName("the library is read best fit first with the filters, the zone and the page, and absent filters are dropped")
    void searchesTheLibrary() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith("/api/v1/nutrition/recipes?scope=library&sort=fit"
                        + "&timezone=Asia/Singapore&q=chicken&diet=VEGAN&tag=HIGH_PROTEIN&page=1&size=50")))
                .andExpect(header("Authorization", "Bearer " + CALLER))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(endsWith(
                        "/api/v1/nutrition/recipes?scope=library&sort=fit&timezone=UTC&page=0&size=50")))
                .andRespond(withSuccess("{\"content\":[]}", MediaType.APPLICATION_JSON));
        NutritionCatalogueClient client = new NutritionCatalogueClient(builder, properties, new ObjectMapper());

        assertThat(client.searchRecipes(CALLER, "chicken", "VEGAN", "HIGH_PROTEIN", "Asia/Singapore", 1, 50))
                .contains("content");
        assertThat(client.searchRecipes(CALLER, null, null, null, "UTC", 0, 50)).contains("content");
        server.verify();
    }

    @Test
    @DisplayName("an unreachable library reads as null")
    void anUnreachableLibraryIsNull() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(method(HttpMethod.GET)).andRespond(withServerError());

        assertThat(new NutritionCatalogueClient(builder, properties, new ObjectMapper())
                .searchRecipes(CALLER, null, null, null, "UTC", 0, 50)).isNull();
    }
}
