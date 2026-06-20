package com.fitsync.workout.dto;

import java.util.List;

public record ExerciseCatalogImportResponse(
        int processed,
        int created,
        int updated,
        int rejected,
        List<ExerciseCatalogUnmappedTermView> unmappedTerms) {

    public ExerciseCatalogImportResponse {
        unmappedTerms = unmappedTerms == null ? List.of() : List.copyOf(unmappedTerms);
    }

    @Override
    public List<ExerciseCatalogUnmappedTermView> unmappedTerms() {
        return List.copyOf(unmappedTerms);
    }
}
