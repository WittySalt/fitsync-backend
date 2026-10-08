package com.fitsync.workout.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Downloads catalogue images from the free-exercise-db repository over HTTP. A 404 is skipped at once;
 * other statuses and I/O errors are retried up to {@link #MAX_ATTEMPTS} times, after which the image is
 * skipped rather than failing the import. Each retry waits a little longer than the one before
 * ({@link #DEFAULT_RETRY_BACKOFF} times the retry number), so a brief upstream hiccup or rate limit has
 * time to clear instead of being hit three times in a row; the wait stays short because the import runs
 * synchronously over some 1700 images.
 */
@Component
public final class UpstreamHttpImageSource implements CatalogImageSource {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpstreamHttpImageSource.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(250);

    private final boolean downloadWhenMissing;
    private final URI imageBaseUri;
    private final HttpClient httpClient;
    private final Duration retryBackoff;
    private final RetryPause retryPause;

    /** Waits between attempts; tests pass one that records the wait instead of sleeping. */
    @FunctionalInterface
    interface RetryPause {
        void pause(Duration duration) throws InterruptedException;
    }

    @Autowired
    public UpstreamHttpImageSource(
            @Value("${fitsync.workout.catalog.free-exercise-db.download-when-missing:true}")
            boolean downloadWhenMissing,
            @Value("${fitsync.workout.catalog.free-exercise-db.image-base-url:https://raw.githubusercontent.com/yuhonas/free-exercise-db/main/exercises/}")
            String imageBaseUrl) {
        this(downloadWhenMissing, imageBaseUrl, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    UpstreamHttpImageSource(boolean downloadWhenMissing, String imageBaseUrl, HttpClient httpClient) {
        this(downloadWhenMissing, imageBaseUrl, httpClient, DEFAULT_RETRY_BACKOFF,
                duration -> Thread.sleep(duration.toMillis()));
    }

    UpstreamHttpImageSource(boolean downloadWhenMissing,
                            String imageBaseUrl,
                            HttpClient httpClient,
                            Duration retryBackoff,
                            RetryPause retryPause) {
        this.downloadWhenMissing = downloadWhenMissing;
        this.imageBaseUri = normalizeBaseUri(imageBaseUrl);
        this.httpClient = httpClient;
        this.retryBackoff = retryBackoff;
        this.retryPause = retryPause;
    }

    @Override
    public boolean isAvailable() {
        return downloadWhenMissing;
    }

    @Override
    public Optional<CatalogImage> load(String relativeImagePath) {
        URI imageUri = imageUri(relativeImagePath);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                if (attempt > 1) {
                    retryPause.pause(retryBackoff.multipliedBy(attempt - 1L));
                }
                HttpResponse<byte[]> response = httpClient.send(
                        HttpRequest.newBuilder(imageUri).timeout(READ_TIMEOUT).GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return Optional.of(new CatalogImage(response.body(), contentType(relativeImagePath, response)));
                }
                if (response.statusCode() == 404) {
                    LOGGER.warn("free-exercise-db upstream image {} returned 404 and will be skipped",
                            sanitizeForLog(relativeImagePath));
                    return Optional.empty();
                }
                LOGGER.warn("free-exercise-db upstream image {} attempt {} returned status {}",
                        sanitizeForLog(relativeImagePath),
                        attempt,
                        response.statusCode());
            } catch (IOException ex) {
                LOGGER.warn("free-exercise-db upstream image {} attempt {} failed: {}",
                        sanitizeForLog(relativeImagePath),
                        attempt,
                        sanitizeForLog(ex.getMessage()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                LOGGER.warn("free-exercise-db upstream image {} download was interrupted",
                        sanitizeForLog(relativeImagePath));
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private URI normalizeBaseUri(String imageBaseUrl) {
        if (imageBaseUrl == null || imageBaseUrl.isBlank()) {
            throw new IllegalArgumentException("free-exercise-db image base URL must be configured");
        }
        URI baseUri = URI.create(imageBaseUrl.endsWith("/") ? imageBaseUrl : imageBaseUrl + "/");
        String scheme = baseUri.getScheme();
        String normalizedScheme = normalizeAsciiScheme(scheme);
        if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
            throw new IllegalArgumentException("free-exercise-db image base URL must be http or https");
        }
        return baseUri;
    }

    /** Lower-cases ASCII letters only, since URI schemes compare case-insensitively. */
    private String normalizeAsciiScheme(String scheme) {
        if (scheme == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(scheme.length());
        for (int index = 0; index < scheme.length(); index++) {
            char character = scheme.charAt(index);
            if (character >= 'A' && character <= 'Z') {
                normalized.append((char) (character + ('a' - 'A')));
            } else {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private URI imageUri(String relativeImagePath) {
        URI relativeUri = URI.create(UriUtils.encodePath(
                relativeImagePath.replace('\\', '/'),
                StandardCharsets.UTF_8));
        if (relativeUri.isAbsolute() || relativeImagePath.startsWith("/") || relativeImagePath.contains("..")) {
            throw new IllegalArgumentException("free-exercise-db image path must be relative");
        }
        return imageBaseUri.resolve(relativeUri);
    }

    private String contentType(String relativeImagePath, HttpResponse<?> response) {
        return response.headers()
                .firstValue("Content-Type")
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith("image/"))
                .orElseGet(() -> CatalogImage.contentTypeOf(relativeImagePath));
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        return value.replace('\r', '_').replace('\n', '_');
    }
}
