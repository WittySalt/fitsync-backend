package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import com.fitsync.recommendation.coach.service.CoachThreadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadRecipeSuggestionsToolTests {

    private static final UUID USER = UUID.randomUUID();
    private static final CoachCallContext CALL = new CoachCallContext(USER, "caller", UUID.randomUUID());
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");

    private static final String PAYLOAD = """
            {"state":"FITS",
             "facts":{"kcalLeft":620,"proteinShortGrams":41,"lowestNutrient":{"code":"IRON","displayName":"Iron"},
               "usualDinnerTime":"19:30","eatenKcal":1480,"targetKcal":2100,"proteinMet":false},
             "suggestions":[
               {"recipeId":"6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb","name":"  Chicken Quinoa Greek Salad ",
                "imageUrl":"https://images.example.test/salad.jpg","kcalPerServing":548,"proteinPerServing":38,
                "reason":"Fits your 620 left and closes most of the protein gap","source":"LIBRARY"}]}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NutritionCatalogueClient client = mock(NutritionCatalogueClient.class);
    private final UserCalendar calendar = mock(UserCalendar.class);
    private final ReadRecipeSuggestionsTool tool = new ReadRecipeSuggestionsTool(client, calendar, objectMapper);

    @BeforeEach
    void theUserIsInSingapore() {
        when(calendar.zone(USER)).thenReturn(SINGAPORE);
    }

    private JsonNode args(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    @Test
    @DisplayName("the tool names itself as a READ tool and tells the model to quote the sentence")
    void describesItself() {
        assertThat(tool.name()).isEqualTo("readRecipeSuggestions");
        assertThat(tool.toolClass()).isEqualTo(ToolClass.READ);
        assertThat(tool.promptSignature()).contains("readRecipeSuggestions(limit)", "one sentence", "quote");
    }

    @Test
    @DisplayName("the tool is listed in the prompt block of the registry")
    void theRegistryListsTheTool() {
        ToolRegistry registry = new ToolRegistry(List.of(tool), mock(CoachThreadService.class), objectMapper);

        assertThat(registry.names()).containsExactly("readRecipeSuggestions");
        assertThat(registry.promptBlock()).contains("readRecipeSuggestions(");
    }

    @Test
    @DisplayName("the payload is handed over as it came, with the name cleaned and the image address left out")
    void handsOverThePayload() throws Exception {
        when(client.recipeSuggestions("caller", 3, "Asia/Singapore")).thenReturn(PAYLOAD);

        ToolResult result = tool.invoke(CALL, args("{}"));
        JsonNode payload = objectMapper.readTree(result.observation());

        assertThat(payload.path("state").asText()).isEqualTo("FITS");
        assertThat(payload.path("facts").path("kcalLeft").asInt()).isEqualTo(620);
        assertThat(payload.path("facts").path("lowestNutrient").path("displayName").asText()).isEqualTo("Iron");
        assertThat(payload.path("facts").path("usualDinnerTime").asText()).isEqualTo("19:30");
        JsonNode first = payload.path("suggestions").get(0);
        assertThat(first.path("name").asText()).isEqualTo("Chicken Quinoa Greek Salad");
        assertThat(first.path("reason").asText()).isEqualTo("Fits your 620 left and closes most of the protein gap");
        assertThat(first.path("kcalPerServing").asInt()).isEqualTo(548);
        assertThat(first.has("imageUrl")).isFalse();
        assertThat(result.summary()).isEqualTo("recipe suggestions FITS");
    }

    @Test
    @DisplayName("a limit between one and three is passed on and anything else is refused before a call is made")
    void checksTheLimit() throws Exception {
        when(client.recipeSuggestions(any(), anyInt(), any())).thenReturn(PAYLOAD);

        tool.invoke(CALL, args("{\"limit\":1}"));

        verify(client).recipeSuggestions("caller", 1, "Asia/Singapore");
        assertThatThrownBy(() -> tool.invoke(CALL, args("{\"limit\":4}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.invoke(CALL, args("{\"limit\":\"many\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).recipeSuggestions("caller", 4, "Asia/Singapore");
    }

    @Test
    @DisplayName("an unreachable service or a body that is not the payload reads as unavailable")
    void unavailable() throws Exception {
        when(client.recipeSuggestions(any(), anyInt(), any())).thenReturn(null, "not json", "{\"other\":1}");

        for (int i = 0; i < 3; i++) {
            ToolResult result = tool.invoke(CALL, args("{}"));
            assertThat(result.summary()).isEqualTo("recipe suggestions unavailable");
        }
    }
}
