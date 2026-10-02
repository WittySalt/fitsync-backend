package com.fitsync.nutrition.dto;

import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

/** A page of recipe cards ranked by how well they fit today, with the figures the ranking used. */
public class RecipeFitPage extends PageImpl<RecipeListItemView> {

    private static final long serialVersionUID = 1L;

    private final RecipeFitView fit;

    public RecipeFitPage(List<RecipeListItemView> content, Pageable pageable, long total, RecipeFitView fit) {
        super(content, pageable, total);
        this.fit = fit;
    }

    public RecipeFitView getFit() {
        return fit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RecipeFitPage page && super.equals(other) && Objects.equals(fit, page.fit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), fit);
    }
}
