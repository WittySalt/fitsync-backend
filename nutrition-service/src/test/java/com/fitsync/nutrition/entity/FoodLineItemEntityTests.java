package com.fitsync.nutrition.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.junit.jupiter.api.Test;

class FoodLineItemEntityTests {

    private static final List<AccessorCase> ACCESSOR_CASES = List.of(
            new AccessorCase("CustomFoodId", UUID.class, UUID.fromString("10000000-0000-0000-0000-000000000001")),
            new AccessorCase("FoodCatalogItemId", UUID.class, UUID.fromString("10000000-0000-0000-0000-000000000002")),
            new AccessorCase("FoodServingId", UUID.class, UUID.fromString("10000000-0000-0000-0000-000000000003")),
            new AccessorCase("ExternalFoodRef", String.class, "off:123"),
            new AccessorCase("Barcode", String.class, "737628064502"),
            new AccessorCase("FoodName", String.class, "Greek Yogurt"),
            new AccessorCase("BrandName", String.class, "FAGE"),
            new AccessorCase("Quantity", BigDecimal.class, new BigDecimal("1.50")),
            new AccessorCase("Unit", String.class, "serving"),
            new AccessorCase("ServingSize", BigDecimal.class, new BigDecimal("170.00")),
            new AccessorCase("ServingUnit", String.class, "g"),
            new AccessorCase("SnapshotServingLabel", String.class, "1 cup"),
            new AccessorCase("Calories", Integer.class, 120),
            new AccessorCase("ProteinGrams", BigDecimal.class, new BigDecimal("17.00")),
            new AccessorCase("CarbsGrams", BigDecimal.class, new BigDecimal("9.00")),
            new AccessorCase("FatGrams", BigDecimal.class, new BigDecimal("0.50")),
            new AccessorCase("FiberGrams", BigDecimal.class, new BigDecimal("1.00")),
            new AccessorCase("SugarGrams", BigDecimal.class, new BigDecimal("7.00")),
            new AccessorCase("SodiumMg", BigDecimal.class, new BigDecimal("65.00")));

    private static final List<ColumnCase> COLUMN_CASES = List.of(
            new ColumnCase("customFoodId", "custom_food_id", false, 255, 0, 0),
            new ColumnCase("foodCatalogItemId", "food_catalog_item_id", false, 255, 0, 0),
            new ColumnCase("foodServingId", "food_serving_id", false, 255, 0, 0),
            new ColumnCase("externalFoodRef", "external_food_ref", false, 120, 0, 0),
            new ColumnCase("barcode", "barcode", false, 40, 0, 0),
            new ColumnCase("foodName", "food_name", true, 120, 0, 0),
            new ColumnCase("brandName", "brand_name", false, 120, 0, 0),
            new ColumnCase("quantity", "quantity", true, 255, 10, 2),
            new ColumnCase("unit", "unit", true, 30, 0, 0),
            new ColumnCase("servingSize", "serving_size", false, 255, 10, 2),
            new ColumnCase("servingUnit", "serving_unit", false, 30, 0, 0),
            new ColumnCase("snapshotServingLabel", "snapshot_serving_label", false, 80, 0, 0),
            new ColumnCase("calories", "calories", true, 255, 0, 0),
            new ColumnCase("proteinGrams", "protein_grams", false, 255, 10, 2),
            new ColumnCase("carbsGrams", "carbs_grams", false, 255, 10, 2),
            new ColumnCase("fatGrams", "fat_grams", false, 255, 10, 2),
            new ColumnCase("fiberGrams", "fiber_grams", false, 255, 10, 2),
            new ColumnCase("sugarGrams", "sugar_grams", false, 255, 10, 2),
            new ColumnCase("sodiumMg", "sodium_mg", false, 255, 10, 2));

    @Test
    void sharedLineItemAccessorsRemainAvailableOnMealItems() throws Exception {
        MealItemEntity entity = new MealItemEntity();

        for (AccessorCase accessorCase : ACCESSOR_CASES) {
            Method setter = MealItemEntity.class.getMethod("set" + accessorCase.suffix(), accessorCase.type());
            Method getter = MealItemEntity.class.getMethod("get" + accessorCase.suffix());

            setter.invoke(entity, accessorCase.value());

            assertThat(getter.invoke(entity)).isEqualTo(accessorCase.value());
        }
    }

    @Test
    void concreteLineItemEntitiesKeepTheirOwnIdentifiers() {
        UUID mealItemId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        UUID mealId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        MealItemEntity mealItem = new MealItemEntity();
        mealItem.setMealItemId(mealItemId);
        mealItem.setMealId(mealId);

        UUID templateItemId = UUID.fromString("30000000-0000-0000-0000-000000000001");
        UUID templateId = UUID.fromString("30000000-0000-0000-0000-000000000002");
        MealTemplateItemEntity templateItem = new MealTemplateItemEntity();
        templateItem.setTemplateItemId(templateItemId);
        templateItem.setTemplateId(templateId);
        templateItem.setSortOrder(3);

        assertThat(mealItem.getMealItemId()).isEqualTo(mealItemId);
        assertThat(mealItem.getMealId()).isEqualTo(mealId);
        assertThat(templateItem.getTemplateItemId()).isEqualTo(templateItemId);
        assertThat(templateItem.getTemplateId()).isEqualTo(templateId);
        assertThat(templateItem.getSortOrder()).isEqualTo(3);
    }

    @Test
    void mappedSuperclassPreservesSharedColumnMappings() throws Exception {
        assertThat(AbstractFoodLineItemEntity.class).hasAnnotation(MappedSuperclass.class);

        for (ColumnCase columnCase : COLUMN_CASES) {
            Column column = AbstractFoodLineItemEntity.class
                    .getDeclaredField(columnCase.fieldName())
                    .getAnnotation(Column.class);

            assertThat(column).isNotNull();
            assertThat(column.name()).isEqualTo(columnCase.columnName());
            assertThat(column.nullable()).isEqualTo(!columnCase.required());
            assertThat(column.length()).isEqualTo(columnCase.length());
            assertThat(column.precision()).isEqualTo(columnCase.precision());
            assertThat(column.scale()).isEqualTo(columnCase.scale());
        }
    }

    private record AccessorCase(String suffix, Class<?> type, Object value) {
    }

    private record ColumnCase(
            String fieldName,
            String columnName,
            boolean required,
            int length,
            int precision,
            int scale) {
    }
}
