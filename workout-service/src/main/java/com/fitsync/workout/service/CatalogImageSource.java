package com.fitsync.workout.service;

import java.util.Optional;

public interface CatalogImageSource {

    boolean isAvailable();

    Optional<CatalogImage> load(String relativeImagePath);
}
