package com.fitsync.nutrition.service.ingest;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fitsync.nutrition.service.ingest.UsdaSrLegacyImporter.SrLegacyFood;
import org.springframework.stereotype.Component;

/**
 * Parses the two USDA FoodData Central — SR Legacy CSV files needed to build catalog rows:
 * <ul>
 *   <li>{@code food.csv} — {@code fdc_id, data_type, description, food_category_id, publication_date}</li>
 *   <li>{@code food_category.csv} — {@code id, code, description}</li>
 * </ul>
 * Foods are joined to their category description via {@code food_category_id}; the description is what the
 * seeded {@code external_food_category_map} (V14) keys on. Columns are resolved by header name so column
 * order changes do not break parsing.
 *
 * <p>Self-contained RFC-4180 reader (quoted fields, embedded commas, doubled-quote escapes) so no CSV
 * library dependency is added. SR Legacy is ~7.8k rows, so reading fully into memory is fine.
 */
@Component
public class UsdaSrLegacyCsvParser {

    private static final String SR_LEGACY_DATA_TYPE = "sr_legacy_food";

    /**
     * Parse SR Legacy foods, joining each to its category description.
     *
     * @param foodCsv         reader over {@code food.csv}
     * @param foodCategoryCsv reader over {@code food_category.csv}
     */
    public List<SrLegacyFood> parse(Reader foodCsv, Reader foodCategoryCsv) throws IOException {
        Map<String, String> categoryDescriptionById = readCategories(foodCategoryCsv);

        List<List<String>> rows = readCsv(foodCsv);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        Integer fdcIdCol = col.get("fdc_id");
        Integer descriptionCol = col.get("description");
        Integer categoryIdCol = col.get("food_category_id");
        Integer dataTypeCol = col.get("data_type");
        if (fdcIdCol == null || descriptionCol == null) {
            throw new IOException("food.csv missing required columns fdc_id/description");
        }

        List<SrLegacyFood> foods = new ArrayList<>(rows.size());
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (dataTypeCol != null && dataTypeCol < row.size()
                    && !SR_LEGACY_DATA_TYPE.equals(row.get(dataTypeCol))) {
                continue;
            }
            String fdcId = at(row, fdcIdCol);
            String description = at(row, descriptionCol);
            if (fdcId == null || fdcId.isBlank() || description == null || description.isBlank()) {
                continue;
            }
            String categoryId = categoryIdCol == null ? null : at(row, categoryIdCol);
            String usdaCategory = categoryId == null || categoryId.isBlank()
                    ? null : categoryDescriptionById.get(categoryId);
            foods.add(new SrLegacyFood(fdcId, description, usdaCategory, null));
        }
        return foods;
    }

    private Map<String, String> readCategories(Reader foodCategoryCsv) throws IOException {
        List<List<String>> rows = readCsv(foodCategoryCsv);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        Integer idCol = col.get("id");
        Integer descriptionCol = col.get("description");
        if (idCol == null || descriptionCol == null) {
            throw new IOException("food_category.csv missing required columns id/description");
        }
        Map<String, String> byId = new HashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            String id = at(row, idCol);
            String description = at(row, descriptionCol);
            if (id != null && !id.isBlank() && description != null) {
                byId.put(id, description);
            }
        }
        return byId;
    }

    private static Map<String, Integer> headerIndex(List<String> header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            index.put(header.get(i).trim(), i);
        }
        return index;
    }

    private static String at(List<String> row, int column) {
        return column < row.size() ? row.get(column) : null;
    }

    /** Minimal RFC-4180 reader: comma-separated, {@code "}-quoted fields, {@code ""} escapes a quote. */
    private static List<List<String>> readCsv(Reader reader) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int ch;
        while ((ch = reader.read()) != -1) {
            char c = (char) ch;
            if (inQuotes) {
                if (c == '"') {
                    int next = reader.read();
                    if (next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = false;
                        if (next == -1) {
                            break;
                        }
                        c = (char) next;
                        if (c == ',') {
                            record.add(field.toString());
                            field.setLength(0);
                            fieldStarted = false;
                        } else if (c == '\n' || c == '\r') {
                            endRecord(records, record, field);
                            fieldStarted = false;
                        }
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n' || c == '\r') {
                if (fieldStarted || !field.isEmpty() || !record.isEmpty()) {
                    endRecord(records, record, field);
                }
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || !field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            records.add(new ArrayList<>(record));
        }
        return records;
    }

    private static void endRecord(List<List<String>> records, List<String> record, StringBuilder field) {
        record.add(field.toString());
        field.setLength(0);
        records.add(new ArrayList<>(record));
        record.clear();
    }
}
