package com.fitsync.workout.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalDirectoryImageSource implements CatalogImageSource {

    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    private final Optional<Path> imageSourceDirectory;

    public LocalDirectoryImageSource(
            @Value("${fitsync.workout.catalog.free-exercise-db.image-source-directory:}")
            String imageSourceDirectory) {
        this.imageSourceDirectory = normalizeImageSourceDirectory(imageSourceDirectory);
    }

    @Override
    public boolean isAvailable() {
        return imageSourceDirectory.isPresent();
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
            return Optional.of(new CatalogImage(Files.readAllBytes(imagePath), contentType(relativeImagePath)));
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

    private String contentType(String sourceImage) {
        String normalized = sourceImage == null ? "" : sourceImage.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return JPEG_CONTENT_TYPE;
    }
}
