package com.fitsync.nutrition.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Applies the brand backfill on H2 to fixture rows shaped like the V900 seed. */
@SpringBootTest
@ActiveProfiles("test")
class UsdaBrandBackfillTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void clean() {
        jdbc.update("delete from meal_item_nutrient_details");
        jdbc.update("delete from food_nutrient_details");
        jdbc.update("delete from food_servings");
        jdbc.update("delete from food_nutrient_profiles");
        jdbc.update("delete from food_catalog_items");
        jdbc.update("delete from food_brands");
    }

    @Test
    void brandsTheMatchingUsdaFoodsAndLeavesTheRestAlone() throws Exception {
        UUID bigMac = usda("McDONALD'S, BIG MAC");
        UUID second = usda("McDONALD'S, Cheeseburger");
        UUID nuggets = usda("WENDY'S, Chicken Nuggets");
        UUID plain = usda("Chicken, nuggets, white meat, precooked");
        UUID imported = other("McDONALD'S, BIG MAC", "OFF");

        assertThat(apply()).isEqualTo(3);

        Map<String, Object> row = food(bigMac);
        assertThat(row).containsEntry("DISPLAY_NAME", "Big Mac").containsEntry("NORMALIZED_NAME", "mcdonald's, big mac");
        assertThat(brandName(bigMac)).isEqualTo("McDONALD'S");
        assertThat(food(second).get("BRAND_ID")).isEqualTo(food(bigMac).get("BRAND_ID"));
        assertThat(brandName(nuggets)).isEqualTo("WENDY'S");
        assertThat(food(plain)).containsEntry("DISPLAY_NAME", "Chicken, nuggets, white meat, precooked");
        assertThat(food(plain).get("BRAND_ID")).isNull();
        assertThat(food(imported)).containsEntry("DISPLAY_NAME", "McDONALD'S, BIG MAC");
        assertThat(jdbc.queryForObject("select count(*) from food_brands", Integer.class)).isEqualTo(2);
    }

    @Test
    void aSecondRunChangesNothingAndAnEmptyTableIsANoOp() throws Exception {
        assertThat(apply()).isZero();
        usda("KFC, biscuit");
        assertThat(apply()).isEqualTo(1);
        assertThat(apply()).isZero();
    }

    @Test
    void anExistingBrandRowIsReusedByItsNormalizedName() throws Exception {
        UUID existing = UUID.randomUUID();
        jdbc.update("""
                insert into food_brands (brand_id, name, normalized_name, active, date_time_created, date_time_updated,
                    created_by, updated_by, version)
                values (?, 'Quaker', 'quaker', true, current_timestamp, current_timestamp, 'x', 'x', 0)
                """, existing);
        UUID food = usda("QUAKER, CAP'N CRUNCH");
        UUID cereal = usda("Cereals ready-to-eat, QUAKER, CAP'N CRUNCH");

        assertThat(apply()).isEqualTo(1);
        assertThat(food(food).get("BRAND_ID")).isEqualTo(existing);
        assertThat(food(food)).containsEntry("DISPLAY_NAME", "Cap'n Crunch");
        assertThat(food(cereal).get("BRAND_ID")).isNull();
        assertThat(jdbc.queryForObject("select count(*) from food_brands", Integer.class)).isEqualTo(1);
    }

    private int apply() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return UsdaBrandBackfill.apply(connection);
        }
    }

    private UUID usda(String name) {
        return other(name, "USDA_SR");
    }

    private UUID other(String name, String dataset) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into food_catalog_items (food_id, scope, source, display_name, normalized_name, active, verified,
                    counts_toward_water, source_dataset, date_time_created, date_time_updated, created_by, updated_by,
                    version, creation_mode)
                values (?, 'GLOBAL', 'IMPORTED', ?, ?, true, true, false, ?, current_timestamp, current_timestamp,
                    'usda-import', 'usda-import', 0, 'MANUAL')
                """, id, name, name.toLowerCase(Locale.ROOT), dataset);
        return id;
    }

    private Map<String, Object> food(UUID id) {
        return jdbc.queryForMap("select * from food_catalog_items where food_id = ?", id);
    }

    private String brandName(UUID foodId) {
        List<String> names = jdbc.queryForList("""
                select b.name from food_brands b join food_catalog_items f on f.brand_id = b.brand_id
                where f.food_id = ?
                """, String.class, foodId);
        return names.isEmpty() ? null : names.get(0);
    }
}
