package com.fitsync.nutrition.dto;

import java.time.Instant;
import java.util.UUID;

/** A recipe the person saved, as it appears in their personal data export. */
public record RecipeFavoriteExportView(UUID recipeId, Instant savedAt) {
}
