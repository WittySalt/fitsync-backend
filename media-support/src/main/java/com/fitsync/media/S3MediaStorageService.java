package com.fitsync.media;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class S3MediaStorageService implements MediaStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final MediaStorageProperties properties;

    public S3MediaStorageService(ObjectProvider<S3Client> s3ClientProvider, MediaStorageProperties properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.properties = properties;
    }

    @Override
    public String put(String key, byte[] bytes, String contentType) {
        String normalizedKey = normalizeKey(key);
        byte[] body = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
        s3ClientProvider.getObject().putObject(putRequest(normalizedKey, contentType), RequestBody.fromBytes(body));
        return urlFor(normalizedKey);
    }

    @Override
    public String put(String key, InputStream inputStream, long contentLength, String contentType) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Media input stream must not be null");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("Media content length must not be negative");
        }
        String normalizedKey = normalizeKey(key);
        s3ClientProvider.getObject().putObject(
                putRequest(normalizedKey, contentType),
                RequestBody.fromInputStream(inputStream, contentLength));
        return urlFor(normalizedKey);
    }

    @Override
    public String urlFor(String key) {
        String baseUrl = trimTrailingSlash(properties.publicBaseUrl());
        return baseUrl + "/" + encodePath(normalizeKey(key));
    }

    private PutObjectRequest putRequest(String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
    }

    private String normalizeKey(String key) {
        if (key == null) {
            throw new IllegalArgumentException("Media key must not be null");
        }
        String normalized = key.trim().replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || normalized.contains("//")) {
            throw new IllegalArgumentException("Media key must be a relative object key");
        }
        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Media key contains an unsafe path segment");
            }
        }
        return normalized;
    }

    private String encodePath(String key) {
        return Arrays.stream(key.split("/"))
                .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Media public base URL must be configured");
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
