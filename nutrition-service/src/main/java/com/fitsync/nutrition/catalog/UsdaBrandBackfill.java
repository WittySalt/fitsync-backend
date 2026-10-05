package com.fitsync.nutrition.catalog;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves the brand of the USDA foods out of their names: a food named "McDONALD'S, BIG MAC" gets the brand
 * McDONALD'S and the display name "Big Mac", and keeps the full original in its normalized name so that a search for
 * the whole text still finds it. Foods that do not start with a listed brand are not touched, and a food that already
 * has a brand is skipped, so running it again changes nothing.
 */
public final class UsdaBrandBackfill {

    private static final String SOURCE_DATASET = "USDA_SR";
    private static final int NAME_LIMIT = 120;
    private static final String UPDATED_BY = "usda-brand-backfill";

    private UsdaBrandBackfill() {
    }

    /** Applies the rules on the connection and returns how many foods were branded. */
    public static int apply(Connection connection) throws SQLException {
        List<Branded> branded = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "select food_id, display_name from food_catalog_items where source_dataset = ? and brand_id is null")) {
            select.setString(1, SOURCE_DATASET);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String original = rows.getString("display_name");
                    Optional<UsdaBrandRules.Rewrite> rewrite = UsdaBrandRules.rewrite(original);
                    if (rewrite.isPresent()) {
                        branded.add(new Branded((UUID) rows.getObject("food_id"), original, rewrite.get()));
                    }
                }
            }
        }
        if (branded.isEmpty()) {
            return 0;
        }
        try (PreparedStatement update = connection.prepareStatement(
                "update food_catalog_items set brand_id = ?, display_name = ?, normalized_name = ?,"
                        + " date_time_updated = current_timestamp, updated_by = ?, version = version + 1"
                        + " where food_id = ?")) {
            for (Branded item : branded) {
                update.setObject(1, brandId(connection, item.rewrite().brandName()));
                update.setString(2, limit(item.rewrite().displayName()));
                update.setString(3, limit(item.original().toLowerCase(Locale.ROOT)));
                update.setString(4, UPDATED_BY);
                update.setObject(5, item.foodId());
                update.addBatch();
            }
            update.executeBatch();
        }
        return branded.size();
    }

    private static UUID brandId(Connection connection, String brandName) throws SQLException {
        String normalized = brandName.toLowerCase(Locale.ROOT);
        try (PreparedStatement find = connection.prepareStatement(
                "select brand_id from food_brands where normalized_name = ?")) {
            find.setString(1, normalized);
            try (ResultSet rows = find.executeQuery()) {
                if (rows.next()) {
                    return (UUID) rows.getObject("brand_id");
                }
            }
        }
        UUID id = UUID.nameUUIDFromBytes(("fitsync-usda-brand:" + normalized).getBytes(StandardCharsets.UTF_8));
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into food_brands (brand_id, name, normalized_name, country_code, date_effective_from,"
                        + " date_effective_to, active, date_time_created, date_time_updated, created_by, updated_by,"
                        + " version) values (?, ?, ?, null, date '2020-01-01', null, true, current_timestamp,"
                        + " current_timestamp, 'usda-import', 'usda-import', 0)")) {
            insert.setObject(1, id);
            insert.setString(2, brandName);
            insert.setString(3, normalized);
            insert.executeUpdate();
        }
        return id;
    }

    private static String limit(String value) {
        return value.length() <= NAME_LIMIT ? value : value.substring(0, NAME_LIMIT);
    }

    private record Branded(UUID foodId, String original, UsdaBrandRules.Rewrite rewrite) {
    }
}
