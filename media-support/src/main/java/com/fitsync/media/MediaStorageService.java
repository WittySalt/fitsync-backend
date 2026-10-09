package com.fitsync.media;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

/**
 * Stores media objects under a relative key and returns the URL clients load them from. Catalogue media is read
 * through its stable public URL; private media such as avatars is read through a short-lived pre-signed URL.
 */
public interface MediaStorageService {

    String put(String key, byte[] bytes, String contentType);

    String put(String key, InputStream inputStream, long contentLength, String contentType);

    String urlFor(String key);

    /**
     * Deletes every object whose key starts with this folder prefix (which must end with {@code /}).
     *
     * @return how many objects were deleted
     */
    int deleteAll(String folderPrefix);

    /** A URL that lets whoever holds it read the object for {@code ttl}, whether or not the object is public. */
    String presignedGetUrl(String key, Duration ttl);

    /**
     * The object key behind a URL that {@link #urlFor(String)} produced, or empty when the URL does not point into
     * this storage's public base URL or names an unsafe key.
     */
    Optional<String> keyForUrl(String url);
}
