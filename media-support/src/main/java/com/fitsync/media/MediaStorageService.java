package com.fitsync.media;

import java.io.InputStream;

/** Stores public media objects under a relative key and returns the URL clients load them from. */
public interface MediaStorageService {

    String put(String key, byte[] bytes, String contentType);

    String put(String key, InputStream inputStream, long contentLength, String contentType);

    String urlFor(String key);
}
