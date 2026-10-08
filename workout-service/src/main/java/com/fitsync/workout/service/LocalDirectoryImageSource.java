package com.fitsync.workout.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads catalogue images from a local checkout of free-exercise-db, configured with
 * {@code fitsync.workout.catalog.free-exercise-db.image-source-directory}. Paths that would resolve outside
 * that directory are refused.
 */
@Component
public class LocalDirectoryImageSource implements CatalogImageSource {

    private final Optional<Path> imageSourceDirectory;

    public LocalDirectoryImageSource(
            @Value("${fitsync.workout.catalog.free-exercise-db.image-source-directory:}")
            String imageSourceDirectory) {
        this.imageSourceDirectory = normalizeImageSourceDirectory(imageSourceDirectory);
    }

    /**
     * Available only when the configured directory exists and holds something. Docker creates
     * an empty directory for a bind mount whose host path is missing; treating that as a source
     * would skip every image instead of falling back to the upstream download.
     */
    @Override
    public boolean isAvailable() {
        return imageSourceDirectory.filter(LocalDirectoryImageSource::hasEntries).isPresent();
    }

    private static boolean hasEntries(Path directory) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.findAny().isPresent();
        } catch (IOException ex) {
            return false;
        }
    }

    @Override
    public Optional<CatalogImage> load(String relativeImagePath) {
        Path baseDirectory = imageSourceDirectory.orElseThrow();
        Path imagePath = baseDirectory.resolve(relativeImagePath.replace('\\', '/')).normalize();
        if (!imagePath.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("free-exercise-db image path escapes the configured source directory");
        }
        if (!Files.isRegularFile(imagePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new CatalogImage(Files.readAllBytes(imagePath), CatalogImage.contentTypeOf(relativeImagePath)));
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read free-exercise-db image " + relativeImagePath, ex);
        }
    }

    private Optional<Path> normalizeImageSourceDirectory(String configuredDirectory) {
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Path.of(configuredDirectory).toAbsolutePath().normalize());
    }
}
