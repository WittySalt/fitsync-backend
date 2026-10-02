package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.AnswerValidator;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadRecipeToolTests {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID RECIPE = UUID.fromString("6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb");
    private static final CoachCallContext CALL = new CoachCallContext(USER, "caller", UUID.randomUUID());

    private static final String DETAIL = """
            {"id":"6c6a1ac5-5b6b-584e-9e3f-40e36949cdfb","name":"Chicken rice bowl","servings":2.00,
             "perServing":{"kcal":377,"proteinGrams":36.45,"carbsGrams":41.2,"fatGrams":7.04,
               "fiberGrams":2.1,"sugarGrams":1.7,"sodiumMg":210,
               "nutrients":[
                 {"code":"IRON","displayName":"Iron","amount":2.1,"unit":"mg","share":0.26},
                 {"code":"VIT_C","displayName":"Vitamin C","amount":30,"unit":"mg","share":0.4},
                 {"code":"CALCIUM","displayName":"Calcium","amount":50,"unit":"mg","share":0.05},
                 {"code":"ZINC","displayName":"Zinc","amount":1,"unit":"mg","share":0.1},
                 {"code":"ODD","displayName":"Odd","amount":1,"unit":"mg","share":null}]},
             "ingredients":[
               {"name":"Chicken breast","foodName":"Chicken, breast, raw","grams":200.00},
               {"name":"White rice","foodName":"Rice","grams":300},
               {"name":"Broccoli","foodName":"Broccoli","grams":null}],
             "tags":["High protein"],"dietFlags":[]}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NutritionCatalogueClient client = mock(NutritionCatalogueClient.class);
    private final ReadRecipeTool tool = new ReadRecipeTool(client, objectMapper);

    private JsonNode args(String recipeId) {
        return objectMapper.createObjectNode().put("recipeId", recipeId);
    }

    @Test
    @DisplayName("the tool names itself as a READ tool and its description takes the id from a search line")
    void describesItself() {
        assertThat(tool.name()).isEqualTo("readRecipe");
        assertThat(tool.toolClass()).isEqualTo(ToolClass.READ);
        assertThat(tool.promptSignature()).contains("readRecipe(recipeId)", "searchRecipes line");
    }

    @Test
    @DisplayName("the recipe is read with the caller's credential and handed over with its figures")
    void handsOverTheRecipe() throws Exception {
        when(client.recipe("caller", RECIPE)).thenReturn(DETAIL);

        ToolResult result = tool.invoke(CALL, args(RECIPE.toString()));
        JsonNode recipe = objectMapper.readTree(result.observation());

        assertThat(recipe.path("name").asText()).isEqualTo("Chicken rice bowl");
        assertThat(recipe.path("servings").asText()).isEqualTo("2");
        assertThat(recipe.path("perServing").path("calories").asInt()).isEqualTo(377);
        assertThat(recipe.path("perServing").path("proteinGrams").toString()).isEqualTo("36.5");
        assertThat(recipe.path("perServing").path("carbohydrateGrams").toString()).isEqualTo("41.2");
        assertThat(recipe.path("perServing").path("fatGrams").toString()).isEqualTo("7");
        assertThat(recipe.path("perServing").path("fibreGrams").toString()).isEqualTo("2.1");
        assertThat(recipe.path("perServing").path("sugarGrams").toString()).isEqualTo("1.7");
        assertThat(recipe.path("perServing").path("sodiumMg").toString()).isEqualTo("210");
        assertThat(recipe.path("tags").get(0).asText()).isEqualTo("High protein");
        assertThat(recipe.path("dietFlags")).isEmpty();
        assertThat(result.summary()).isEqualTo("recipe Chicken rice bowl");
    }

    @Test
    @DisplayName("the top three vitamins and minerals come largest share first, with the percentage already worked out")
    void namesTheTopNutrients() throws Exception {
        when(client.recipe(any(), any())).thenReturn(DETAIL);

        JsonNode recipe = objectMapper.readTree(tool.invoke(CALL, args(RECIPE.toString())).observation());

        JsonNode top = recipe.path("topVitaminsAndMinerals");
        assertThat(top).hasSize(3);
        assertThat(top.get(0).asText()).isEqualTo("Vitamin C 40% of the daily amount (30 mg)");
        assertThat(top.get(1).asText()).isEqualTo("Iron 26% of the daily amount (2.1 mg)");
        assertThat(top.get(2).asText()).isEqualTo("Zinc 10% of the daily amount (1 mg)");
    }

    @Test
    @DisplayName("the ingredients keep their short name and carry grams when the recipe has them")
    void listsTheIngredients() throws Exception {
        when(client.recipe(any(), any())).thenReturn(DETAIL);

        JsonNode recipe = objectMapper.readTree(tool.invoke(CALL, args(RECIPE.toString())).observation());

        assertThat(recipe.path("ingredients").get(0).asText()).isEqualTo("Chicken breast 200 g");
        assertThat(recipe.path("ingredients").get(1).asText()).isEqualTo("White rice 300 g");
        assertThat(recipe.path("ingredients").get(2).asText()).isEqualTo("Broccoli");
    }

    @Test
    @DisplayName("a missing id or an id that is not a recipe id is a bad call and reaches no service")
    void rejectsABadId() {
        assertThatThrownBy(() -> tool.invoke(CALL, objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recipeId");
        assertThatThrownBy(() -> tool.invoke(CALL, args("pasta")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exactly");
        verify(client, never()).recipe(any(), any());
    }

    @Test
    @DisplayName("an unreachable service, a recipe the user cannot see and an unreadable body all read as unavailable")
    void unavailable() {
        when(client.recipe(any(), any())).thenReturn(null, "not json", "[]");

        for (int i = 0; i < 3; i++) {
            ToolResult result = tool.invoke(CALL, args(RECIPE.toString()));
            assertThat(result.summary()).isEqualTo("recipe unavailable");
            assertThat(result.observation()).contains("could not be read");
        }
    }

    @Test
    @DisplayName("a long recipe name is cut short inside the result")
    void cutsLongNames() throws Exception {
        String longName = "A".repeat(200);
        when(client.recipe(any(), any())).thenReturn("{\"name\":\"" + longName + "\",\"perServing\":{}}");

        JsonNode recipe = objectMapper.readTree(tool.invoke(CALL, args(RECIPE.toString())).observation());

        assertThat(recipe.path("name").asText()).hasSize(ReadMealsOnDayTool.MAX_FOOD_NAME_LENGTH);
    }

    @Test
    @DisplayName("an answer quoting the recipe passes the answer check, a figure the model worked out does not")
    void theAnswerCheckAcceptsTheRecipeFigures() {
        when(client.recipe(any(), any())).thenReturn(DETAIL);
        ToolResult result = tool.invoke(CALL, args(RECIPE.toString()));
        List<String> transcript = List.of("Result of readRecipe({\"recipeId\":\"" + RECIPE + "\"}):\n"
                + result.observation());
        AnswerValidator validator = new AnswerValidator();

        assertThat(validator.validate("One serving has 377 kcal and 36.5 g of protein, and Vitamin C gives 40% "
                + "of the daily amount.", "What is in it?", transcript, null).valid()).isTrue();
        assertThat(validator.validate("Two servings would be 754 kcal.", "What is in it?", transcript, null)
                .violations()).containsExactly("NUMBER_NOT_IN_RECORDS:754");
    }
}
