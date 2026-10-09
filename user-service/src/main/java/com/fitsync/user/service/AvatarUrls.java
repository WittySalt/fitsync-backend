package com.fitsync.user.service;

import java.time.Duration;
import java.util.Optional;

import com.fitsync.media.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the avatar reference stored on a profile into a URL the browser can load. Avatars are private objects
 * under {@code avatars/}, so each read hands out a pre-signed URL that expires after {@link #READ_URL_TTL}.
 *
 * <p>The profile row keeps the stable public-form URL that the upload returned (rows written before avatars were
 * private hold the same form), and the object key is derived from it here. A value that does not point into the
 * media bucket's {@code avatars/} prefix is passed through unchanged, since it is not ours to sign.
 */
final class AvatarUrls {

    static final Duration READ_URL_TTL = Duration.ofHours(1);
    static final String AVATAR_KEY_PREFIX = "avatars/";

    private static final Logger log = LoggerFactory.getLogger(AvatarUrls.class);

    private AvatarUrls() {
    }

    /**
     * The URL to return for a stored avatar reference: a pre-signed URL for one of our avatars, the stored value
     * for anything else, and null when an avatar of ours cannot be signed (a public URL would only answer 403).
     */
    static String readableUrl(String stored, MediaStorageService media) {
        if (stored == null || stored.isBlank() || media == null) {
            return stored;
        }
        Optional<String> key = stored.contains("://") ? media.keyForUrl(stored) : Optional.of(stored.trim());
        if (key.isEmpty() || !key.get().startsWith(AVATAR_KEY_PREFIX)) {
            return stored;
        }
        try {
            return media.presignedGetUrl(key.get(), READ_URL_TTL);
        } catch (RuntimeException ex) {
            log.warn("Could not sign an avatar read URL: {}", ex.getClass().getSimpleName());
            return null;
        }
    }
}
