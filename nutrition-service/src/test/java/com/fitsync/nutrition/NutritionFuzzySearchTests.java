package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fitsync.nutrition.service.FuzzyFoodSearch;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The typo-tolerant fallback of the food search. H2 cannot run trigram queries, so the similarity lookup is a
 * stand-in here and these tests cover when it is asked and how its answer is merged; the SQL itself needs
 * PostgreSQL.
 */
class NutritionFuzzySearchTests extends DiaryApiTestBase {

    @MockitoBean
    private FuzzyFoodSearch fuzzyFoodSearch;

    private String token;
    private UUID chickenBreast;
    private UUID chickenThigh;
    private UUID chickpeas;
    private UUID beef;

    @BeforeEach
    void seedFoodsAndToken() {
        ensureCategory("PREPARED", "OTHER");
        chickenBreast = seed("Chicken breast");
        chickenThigh = seed("Chicken thigh");
        chickpeas = seed("Chickpeas");
        beef = seed("Beef mince");
        token = tokenFor("fuzzy@example.com");
    }

    @Test
    void aTypoFindsTheFoodThroughTheSimilarNames() throws Exception {
        when(fuzzyFoodSearch.similarFoodIds(any(), eq("chiken"), any(), anySet(), anyInt()))
                .thenReturn(List.of(chickenBreast, chickpeas));

        String body = search("chiken", 0, 20, null);

        assertThat(names(body)).containsExactly("Chicken breast", "Chickpeas");
        assertThat(JsonPath.<Integer>read(body, "$.totalElements")).isEqualTo(2);
    }

    @Test
    void similarNamesComeAfterTheExactOnesWithNothingTwiceAndTheLimitIsWhatIsLeftOfThePage() throws Exception {
        when(fuzzyFoodSearch.similarFoodIds(any(), eq("chick"), any(), anySet(), anyInt()))
                .thenReturn(List.of(beef, chickenBreast));

        // The exact search finds three chicken foods; "Beef mince" is added, and the one already found is not.
        String body = search("chick", 0, 4, null);

        assertThat(names(body)).hasSize(4).endsWith("Beef mince").doesNotHaveDuplicates();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<UUID>> found = ArgumentCaptor.forClass(Set.class);
        verify(fuzzyFoodSearch).similarFoodIds(any(), eq("chick"), any(), found.capture(), eq(1));
        assertThat(found.getValue()).containsExactlyInAnyOrder(chickenBreast, chickenThigh, chickpeas);
        assertThat(JsonPath.<Integer>read(body, "$.totalElements")).isEqualTo(4);
    }

    @Test
    void nothingSimilarLeavesTheExactResultsAsTheyAre() throws Exception {
        when(fuzzyFoodSearch.similarFoodIds(any(), any(), any(), anySet(), anyInt())).thenReturn(List.of());

        String body = search("beef", 0, 20, null);

        assertThat(names(body)).containsExactly("Beef mince");
        assertThat(JsonPath.<Integer>read(body, "$.totalElements")).isEqualTo(1);
    }

    @Test
    void fiveExactMatchesNeverAskForSimilarOnes() throws Exception {
        seed("Chicken wings");
        seed("Chicken soup");
        seed("Chicken stock");

        assertThat(names(search("chicken", 0, 20, null))).hasSize(5);

        verify(fuzzyFoodSearch, never()).similarFoodIds(any(), any(), any(), anySet(), anyInt());
    }

    @Test
    void theFallbackOnlyRunsForAPlainTextSearchOnTheFirstPage() throws Exception {
        search("beef", 1, 20, null);
        search("beef", 0, 20, "verified=true");
        search("beef", 0, 20, "scope=GLOBAL");
        search("beef", 0, 20, "foodGroup=PROTEIN");
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token).param("q", "  "))
                .andExpect(status().isOk());

        verify(fuzzyFoodSearch, never()).similarFoodIds(any(), any(), any(), anySet(), anyInt());
    }

    private String search(String query, int page, int size, String extra) throws Exception {
        var request = get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                .param("q", query).param("page", String.valueOf(page)).param("size", String.valueOf(size));
        if (extra != null) {
            String[] pair = extra.split("=");
            request = request.param(pair[0], pair[1]);
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static List<String> names(String body) {
        return JsonPath.read(body, "$.content[*].displayName");
    }

    private UUID seed(String displayName) {
        return seedFood(displayName, "PREPARED", false, "g", 200, "1", null, mapOf())
                .addServing("100 g", "g", "100", "100").foodId;
    }
}
