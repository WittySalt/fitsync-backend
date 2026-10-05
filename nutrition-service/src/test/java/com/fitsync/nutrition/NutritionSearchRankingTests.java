package com.fitsync.nutrition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The catalogue search ranks by relevance in the query: the name is the query, the name or the brand starts with
 * it, a word of the name starts with it, then anything that only contains it. Inside a tier a branded item comes
 * first, then the shorter name, then the name, then the id.
 */
class NutritionSearchRankingTests extends DiaryApiTestBase {

    private String token;

    @BeforeEach
    void seedNuggets() {
        jdbcTemplate.update("delete from food_brands");
        UUID mcdonalds = brand("McDONALD'S");
        UUID wendys = brand("WENDY'S");
        // The shapes the brand migration leaves behind: the brand moved out of the name, the full USDA text kept
        // in the normalized name.
        branded(seed("Chicken McNuggets", "mcdonald's, chicken mcnuggets"), mcdonalds);
        branded(seed("Chicken Nuggets", "wendy's, chicken nuggets"), wendys);
        seed("Chicken, nuggets, white meat, precooked, frozen, not reheated", null);
        seed("Ice cream bar, stick or nugget, with crunch coating", null);
        seed("Nugget", null);
        seed("Chicken breast", null);
        token = tokenFor("ranking@example.com");
    }

    @Test
    void anExactNameThenAWordStartThenAContainedMatchWithBrandedBeforeUnbrandedInATier() throws Exception {
        assertThat(names("nugget", 0, 20)).containsExactly(
                "Nugget",
                "Chicken Nuggets",
                "Ice cream bar, stick or nugget, with crunch coating",
                "Chicken, nuggets, white meat, precooked, frozen, not reheated",
                "Chicken McNuggets");
    }

    @Test
    void aNameThatStartsWithTheQueryComesBeforeOneThatOnlyHasItInAWord() throws Exception {
        assertThat(names("chicken", 0, 20)).containsExactly(
                "Chicken Nuggets",
                "Chicken McNuggets",
                "Chicken breast",
                "Chicken, nuggets, white meat, precooked, frozen, not reheated");
    }

    @Test
    void aBrandThatStartsWithTheQueryRanksTheItemLikeANameStart() throws Exception {
        assertThat(names("mcdonald", 0, 20)).containsExactly("Chicken McNuggets");
        assertThat(names("wendy", 0, 20)).containsExactly("Chicken Nuggets");
    }

    @Test
    void pagingWalksTheSameOrderWithoutRepeatsOrGaps() throws Exception {
        List<String> all = names("nugget", 0, 20);
        List<String> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            paged.addAll(names("nugget", page, 2));
        }
        assertThat(paged).containsExactlyElementsOf(all);
    }

    @Test
    void sameNamesAreOrderedByIdSoThePageNeverShuffles() throws Exception {
        seed("Twin pudding", null);
        seed("Twin pudding", null);
        List<String> first = ids("twin", 0, 20);
        assertThat(first).hasSize(2).isSorted();
        assertThat(ids("twin", 0, 1)).containsExactly(first.get(0));
        assertThat(ids("twin", 1, 1)).containsExactly(first.get(1));
    }

    @Test
    void likeWildcardsInTheQueryAreLiteral() throws Exception {
        assertThat(names("chicken%", 0, 20)).isEmpty();
        assertThat(names("n_gget", 0, 20)).isEmpty();
    }

    private List<String> names(String query, int page, int size) throws Exception {
        return read(query, page, size, "$.content[*].displayName");
    }

    private List<String> ids(String query, int page, int size) throws Exception {
        return read(query, page, size, "$.content[*].foodId");
    }

    private List<String> read(String query, int page, int size, String path) throws Exception {
        String body = mockMvc.perform(get(BASE + "/foods/search").header("Authorization", "Bearer " + token)
                        .param("q", query).param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, path);
    }

    private UUID seed(String displayName, String normalizedName) {
        UUID id = seedFood(displayName, "PREPARED", false, "g", 200, "1", null, mapOf())
                .addServing("100 g", "g", "100", "100").foodId;
        ensureCategory("PREPARED", "OTHER");
        if (normalizedName != null) {
            jdbcTemplate.update("update food_catalog_items set normalized_name = ? where food_id = ?",
                    normalizedName, id);
        }
        return id;
    }

    private void branded(UUID foodId, UUID brandId) {
        jdbcTemplate.update("update food_catalog_items set brand_id = ? where food_id = ?", brandId, foodId);
    }

    private UUID brand(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into food_brands (brand_id, name, normalized_name, active, date_time_created,
                    date_time_updated, created_by, updated_by, version)
                values (?, ?, ?, true, current_timestamp, current_timestamp, 'test', 'test', 0)
                """, id, name, name.toLowerCase(java.util.Locale.ROOT));
        return id;
    }
}
