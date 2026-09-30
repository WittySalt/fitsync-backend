package com.fitsync.recommendation.coach.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.recommendation.coach.client.NutritionCatalogueClient;
import com.fitsync.recommendation.coach.orchestrator.AnswerValidator;
import com.fitsync.recommendation.coach.orchestrator.CoachCallContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadMealsOnDayToolTests {

    private static final UUID USER = UUID.randomUUID();
    private static final CoachCallContext CALL = new CoachCallContext(USER, "caller", UUID.randomUUID());
    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-01");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NutritionCatalogueClient client = mock(NutritionCatalogueClient.class);
    private final UserCalendar calendar = mock(UserCalendar.class);
    private final ReadMealsOnDayTool tool = new ReadMealsOnDayTool(client, calendar, objectMapper);

    @BeforeEach
    void theUserIsInSingaporeOnTheFirstOfOctober() {
        when(calendar.zone(USER)).thenReturn(SINGAPORE);
        when(calendar.today(SINGAPORE)).thenReturn(TODAY);
    }

    @Test
    @DisplayName("the day is read as the user's local day, and the tool names itself as a READ tool")
    void readsTheLocalDay() {
        when(client.meals(any(), any(), any())).thenReturn("[]");

        tool.invoke(CALL, args("2026-09-30"));

        // Midnight to midnight in Singapore is 16:00 to 16:00 in UTC.
        verify(client).meals("caller", Instant.parse("2026-09-29T16:00:00Z"),
                Instant.parse("2026-09-30T16:00:00Z").minusNanos(1));
        assertThat(tool.name()).isEqualTo("readMealsOnDay");
        assertThat(tool.toolClass()).isEqualTo(ToolClass.READ);
        assertThat(tool.promptSignature()).contains("readMealsOnDay(date)", "quote it", "yesterday");
    }

    @Test
    @DisplayName("totals, targets and the meals in time order are handed over, rounded like the facts")
    void handsOverTheDay() throws Exception {
        when(client.meals(any(), any(), any())).thenReturn("""
                [{"mealType":"DINNER","consumedAt":"2026-09-30T11:05:00Z","totalCalories":812,
                  "totalProteinGrams":48.25,"items":[{"foodName":"  Salmon fillet "},{"foodName":"Rice"}]},
                 {"mealType":"BREAKFAST","consumedAt":"2026-09-30T00:10:00Z","totalCalories":420.4,
                  "totalProteinGrams":30.0,"items":[{"foodName":"Rolled oats"}]}]""");
        when(client.dailySummary("caller", LocalDate.parse("2026-09-30"), "Asia/Singapore")).thenReturn("""
                {"totalCalories":1232,"totalProteinGrams":78.25,"totalCarbsGrams":100.04,"totalFatGrams":40,
                 "targetCalories":2000,"targetProteinGrams":140,"targetCarbsGrams":null}""");

        ToolResult result = tool.invoke(CALL, args("2026-09-30"));
        JsonNode day = objectMapper.readTree(result.observation());

        assertThat(day.path("date").asText()).isEqualTo("2026-09-30");
        assertThat(day.path("totals").path("calories").asInt()).isEqualTo(1232);
        assertThat(day.path("totals").path("proteinGrams").toString()).isEqualTo("78.3");
        assertThat(day.path("totals").path("carbohydrateGrams").toString()).isEqualTo("100");
        assertThat(day.path("targets").path("proteinGrams").toString()).isEqualTo("140");
        assertThat(day.path("targets").has("carbohydrateGrams")).isFalse();
        assertThat(day.path("mealCount").asInt()).isEqualTo(2);
        JsonNode first = day.path("meals").get(0);
        assertThat(first.path("mealType").asText()).isEqualTo("BREAKFAST");
        assertThat(first.path("time").asText()).isEqualTo("08:10");
        assertThat(first.path("calories").asInt()).isEqualTo(420);
        assertThat(first.path("proteinGrams").toString()).isEqualTo("30");
        assertThat(day.path("meals").get(1).path("time").asText()).isEqualTo("19:05");
        assertThat(day.path("meals").get(1).path("foods").get(0).asText()).isEqualTo("Salmon fillet");
        assertThat(result.summary()).isEqualTo("2 meals on 2026-09-30");
    }

    @Test
    @DisplayName("a day without meals returns the date, an empty list and a plain note, without a summary call")
    void anEmptyDay() throws Exception {
        when(client.meals(any(), any(), any())).thenReturn("[]");

        JsonNode day = objectMapper.readTree(tool.invoke(CALL, args("2026-09-30")).observation());

        assertThat(day.path("date").asText()).isEqualTo("2026-09-30");
        assertThat(day.path("meals").isArray()).isTrue();
        assertThat(day.path("meals")).isEmpty();
        assertThat(day.path("note").asText()).contains("Nothing was logged");
        verify(client, never()).dailySummary(any(), any(), any());
    }

    @Test
    @DisplayName("a meal outside the local day is left out even when the service returns it")
    void dropsMealsOutsideTheDay() throws Exception {
        when(client.meals(any(), any(), any())).thenReturn("""
                [{"mealType":"SNACK","consumedAt":"2026-09-30T16:00:00Z","totalCalories":100,"items":[]}]""");

        JsonNode day = objectMapper.readTree(tool.invoke(CALL, args("2026-09-30")).observation());

        assertThat(day.path("meals")).isEmpty();
    }

    @Test
    @DisplayName("at most ten meals and twelve food names a meal, each name cut to eighty characters")
    void capsWhatIsHandedOver() throws Exception {
        StringBuilder meals = new StringBuilder("[");
        for (int i = 0; i < 12; i++) {
            StringBuilder foods = new StringBuilder();
            for (int f = 0; f < 15; f++) {
                foods.append(f == 0 ? "" : ",").append("{\"foodName\":\"")
                        .append(f == 0 ? "x".repeat(200) : "Food " + f).append("\"}");
            }
            meals.append(i == 0 ? "" : ",").append("{\"mealType\":\"SNACK\",\"consumedAt\":\"2026-09-30T")
                    .append("%02d".formatted(i)).append(":00:00Z\",\"totalCalories\":100,\"items\":[")
                    .append(foods).append("]}");
        }
        when(client.meals(any(), any(), any())).thenReturn(meals.append("]").toString());
        when(client.dailySummary(any(), any(), any())).thenReturn("{\"totalCalories\":1200}");

        JsonNode day = objectMapper.readTree(tool.invoke(CALL, args("2026-09-30")).observation());

        assertThat(day.path("meals")).hasSize(10);
        assertThat(day.path("mealCount").asInt()).isEqualTo(12);
        assertThat(day.path("note").asText()).contains("first 10 meals");
        assertThat(day.path("meals").get(0).path("foods")).hasSize(12);
        assertThat(day.path("meals").get(0).path("foods").get(0).asText()).hasSize(80);
    }

    @Test
    @DisplayName("a date that does not parse, is in the future, or is too old is refused with words to relay")
    void refusesDatesOutsideTheWindow() {
        for (String bad : List.of("yesterday", "2026-13-40", "30/09/2026", "2026-9-30")) {
            ToolResult result = tool.invoke(CALL, args(bad));
            assertThat(result.summary()).isEqualTo("date refused");
            assertThat(result.observation()).contains("YYYY-MM-DD");
        }
        assertThat(tool.invoke(CALL, args("2026-10-02")).observation()).contains("not happened yet", "2026-10-01");
        assertThat(tool.invoke(CALL, args("2026-07-31")).observation()).contains("last 60 days");
        verify(client, never()).meals(any(), any(), any());
    }

    @Test
    @DisplayName("today and the sixtieth day back are both allowed")
    void theEdgesOfTheWindowAreAllowed() {
        when(client.meals(any(), any(), any())).thenReturn("[]");

        assertThat(tool.invoke(CALL, args("2026-10-01")).summary()).isEqualTo("0 meals");
        assertThat(tool.invoke(CALL, args("2026-08-02")).summary()).isEqualTo("0 meals");
    }

    @Test
    @DisplayName("a missing date is a bad call the model can correct")
    void aMissingDateIsRejected() {
        assertThatThrownBy(() -> tool.invoke(CALL, objectMapper.createObjectNode()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an upstream failure, or a body that is not meals, gives the failed result of the other tools")
    void upstreamFailure() {
        when(client.meals(any(), any(), any())).thenReturn(null);
        assertThat(tool.invoke(CALL, args("2026-09-30")).summary()).isEqualTo("meals unavailable");

        when(client.meals(any(), any(), any())).thenReturn("{\"not\":\"a list\"}");
        assertThat(tool.invoke(CALL, args("2026-09-30")).summary()).isEqualTo("meals unavailable");

        when(client.meals(any(), any(), any())).thenReturn("nonsense");
        assertThat(tool.invoke(CALL, args("2026-09-30")).summary()).isEqualTo("meals unavailable");

        when(client.meals(any(), any(), any())).thenReturn(
                "[{\"mealType\":\"LUNCH\",\"consumedAt\":\"2026-09-30T04:00:00Z\",\"items\":[]}]");
        when(client.dailySummary(any(), eq(LocalDate.parse("2026-09-30")), any())).thenReturn(null);
        ToolResult noSummary = tool.invoke(CALL, args("2026-09-30"));
        assertThat(noSummary.summary()).isEqualTo("meals unavailable");
        assertThat(noSummary.observation()).contains("could not be read");
    }

    @Test
    @DisplayName("an answer quoting a meal's calories and protein passes the answer check, a made-up figure fails")
    void theAnswerCheckAcceptsWhatTheToolReturned() {
        when(client.meals(any(), any(), any())).thenReturn("""
                [{"mealType":"DINNER","consumedAt":"2026-09-30T11:05:00Z","totalCalories":812,
                  "totalProteinGrams":48.25,"items":[{"foodName":"Salmon"}]}]""");
        when(client.dailySummary(any(), any(), any())).thenReturn("{\"totalCalories\":812,\"totalProteinGrams\":48.2}");
        ToolResult result = tool.invoke(CALL, args("2026-09-30"));
        List<String> transcript = List.of(
                "Result of readMealsOnDay({\"date\":\"2026-09-30\"}):\n" + result.observation());
        AnswerValidator validator = new AnswerValidator();

        assertThat(validator.validate("Dinner at 19:05 was 812 kcal with 48.3 g of protein.",
                "What did I eat yesterday?", transcript, null).valid()).isTrue();
        assertThat(validator.validate("Dinner was 900 kcal with 48.3 g of protein.",
                "What did I eat yesterday?", transcript, null).violations())
                .containsExactly("NUMBER_NOT_IN_RECORDS:900");
    }

    private JsonNode args(String date) {
        return objectMapper.createObjectNode().put("date", date);
    }
}
