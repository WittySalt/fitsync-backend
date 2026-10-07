package com.fitsync.workout.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDirectoryImageSourceTests {

    @TempDir
    Path tempDir;

    @Test
    void isUnavailableWhenNoDirectoryIsConfigured() {
        assertThat(new LocalDirectoryImageSource("").isAvailable()).isFalse();
    }

    @Test
    void isUnavailableWhenTheConfiguredDirectoryDoesNotExist() {
        assertThat(new LocalDirectoryImageSource(tempDir.resolve("missing").toString()).isAvailable()).isFalse();
    }

    @Test
    void isUnavailableWhenTheConfiguredDirectoryIsEmpty() {
        // Docker creates an empty directory for a bind mount whose host path does not exist;
        // that must not shadow the upstream download.
        assertThat(new LocalDirectoryImageSource(tempDir.toString()).isAvailable()).isFalse();
    }

    @Test
    void isAvailableAndLoadsImagesWhenTheDirectoryHasContent() throws IOException {
        Path exercise = Files.createDirectories(tempDir.resolve("Bench_Press"));
        Files.write(exercise.resolve("0.jpg"), new byte[] {1, 2, 3});
        LocalDirectoryImageSource source = new LocalDirectoryImageSource(tempDir.toString());

        assertThat(source.isAvailable()).isTrue();
        assertThat(source.load("Bench_Press/0.jpg")).hasValueSatisfying(image -> {
            assertThat(image.bytes()).containsExactly(1, 2, 3);
            assertThat(image.contentType()).isEqualTo("image/jpeg");
        });
    }
}
