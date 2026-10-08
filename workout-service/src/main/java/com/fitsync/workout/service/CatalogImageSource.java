package com.fitsync.workout.service;

import java.util.Optional;

/**
 * Where the catalogue importer reads free-exercise-db images from: a local copy of the repository or the
 * upstream download. The importer uses the first source that reports itself available.
 */
public interface CatalogImageSource {

    boolean isAvailable();

    /** Loads an image by its path relative to the free-exercise-db exercises folder; empty when it is missing. */
    Optional<CatalogImage> load(String relativeImagePath);
}
