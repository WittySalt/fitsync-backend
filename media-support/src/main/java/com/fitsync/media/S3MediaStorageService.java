package com.fitsync.media;

import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

public class S3MediaStorageService implements MediaStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    /** Cached for a year as immutable, so changed content has to be stored under a new key. */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;
    private final MediaStorageProperties properties;

    public S3MediaStorageService(ObjectProvider<S3Client> s3ClientProvider,
                                 ObjectProvider<S3Presigner> s3PresignerProvider,
                                 MediaStorageProperties properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.s3PresignerProvider = s3PresignerProvider;
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

    @Override
    public int deleteAll(String folderPrefix) {
        if (folderPrefix == null || !folderPrefix.endsWith("/")) {
            throw new IllegalArgumentException("Media folder prefix must end with '/'");
        }
        String prefix = normalizeKey(folderPrefix.substring(0, folderPrefix.length() - 1)) + "/";
        S3Client s3Client = s3ClientProvider.getObject();
        int deleted = 0;
        String continuationToken = null;
        do {
            // One listing page holds at most 1000 keys, which is also the most one DeleteObjects call takes.
            ListObjectsV2Response page = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.bucket())
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            List<ObjectIdentifier> keys = new ArrayList<>();
            for (S3Object object : page.contents()) {
                keys.add(ObjectIdentifier.builder().key(object.key()).build());
            }
            if (!keys.isEmpty()) {
                DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(properties.bucket())
                        .delete(Delete.builder().objects(keys).quiet(true).build())
                        .build());
                if (response.hasErrors() && !response.errors().isEmpty()) {
                    throw new IllegalStateException("Media objects could not be deleted: "
                            + response.errors().get(0).code());
                }
                deleted += keys.size();
            }
            continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
        return deleted;
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Pre-signed URL lifetime must be positive");
        }
        GetObjectPresignRequest request = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(get -> get.bucket(properties.bucket()).key(normalizeKey(key)))
                .build();
        return s3PresignerProvider.getObject().presignGetObject(request).url().toString();
    }

    @Override
    public Optional<String> keyForUrl(String url) {
        if (url == null) {
            return Optional.empty();
        }
        String prefix = trimTrailingSlash(properties.publicBaseUrl()) + "/";
        String trimmed = url.trim();
        if (!trimmed.startsWith(prefix) || trimmed.indexOf('?') >= 0 || trimmed.indexOf('#') >= 0) {
            return Optional.empty();
        }
        try {
            String key = Arrays.stream(trimmed.substring(prefix.length()).split("/", -1))
                    // Undoes encodePath; a literal '+' was encoded as %2B, so it must survive decoding.
                    .map(segment -> URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("/"));
            return Optional.of(normalizeKey(key));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private PutObjectRequest putRequest(String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType)
                .cacheControl(CACHE_CONTROL)
                .build();
    }

    /** Refuses absolute keys, empty segments and {@code .}/{@code ..} so a key cannot escape its prefix. */
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
                // URLEncoder encodes for forms, where a space is '+'; in a path it has to be %20.
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
