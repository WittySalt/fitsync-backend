package com.fitsync.media;

import java.io.InputStream;

/** Stores public media objects under a relative key and returns the URL clients load them from. */
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
}
