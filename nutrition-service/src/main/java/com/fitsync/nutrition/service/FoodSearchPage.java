package com.fitsync.nutrition.service;

import java.util.List;

import com.fitsync.nutrition.dto.FoodSearchResultView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * One page of food search results in a form that can be cached, rebuilt into the same {@link Page} the search
 * endpoint has always returned. {@code complete} is false when the external food source failed and the page
 * holds only local results; such a page is answered but never cached.
 */
public record FoodSearchPage(
        List<FoodSearchResultView> content,
        int page,
        int size,
        long totalElements,
        boolean complete) {

    public FoodSearchPage {
        content = content == null ? List.of() : List.copyOf(content);
    }

    @Override
    public List<FoodSearchResultView> content() {
        return List.copyOf(content);
    }

    public Page<FoodSearchResultView> toPage() {
        return new PageImpl<>(content, PageRequest.of(page, size), totalElements);
    }
}
