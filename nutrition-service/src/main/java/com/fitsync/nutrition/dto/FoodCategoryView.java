package com.fitsync.nutrition.dto;

import java.util.List;
import java.util.UUID;

/**
 * A food category node for the hierarchical browse/filter UI. Top-level groups carry their
 * subcategories in {@code children}; leaf nodes have an empty {@code children} list.
 */
public record FoodCategoryView(
        UUID categoryId,
        UUID parentId,
        String code,
        String displayName,
        String slug,
        int sortOrder,
        List<FoodCategoryView> children) {

    public FoodCategoryView {
        children = children == null ? List.of() : List.copyOf(children);
    }

    @Override
    public List<FoodCategoryView> children() {
        return List.copyOf(children);
    }
}
