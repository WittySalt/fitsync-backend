package com.fitsync.workout.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

public record CatalogImage(byte[] bytes, String contentType) {

    private static final String JPEG_CONTENT_TYPE = "image/jpeg";

    public CatalogImage {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * The content type of a catalogue image judged by its file name: PNG and WebP by their suffix,
     * anything else JPEG, which is what free-exercise-db ships. Both image sources use this so a
     * file is typed the same whether it was read locally or downloaded.
     */
    static String contentTypeOf(String imagePath) {
        String normalized = imagePath == null ? "" : imagePath.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".png")) {
            return "image/png";
        }
        if (normalized.endsWith(".webp")) {
            return "image/webp";
        }
        return JPEG_CONTENT_TYPE;
    }

    /**
     * The file extension the stored copy is given, so a PNG or WebP is not served under a .jpg name.
     * JPEG and anything unrecognised keep "jpg", which is what every key was before, so re-importing
     * an unchanged JPEG catalogue writes to the same keys.
     */
    String fileExtension() {
        String value = contentType == null ? "" : contentType;
        int parameters = value.indexOf(';');
        String mediaType = (parameters >= 0 ? value.substring(0, parameters) : value).trim();
        if (isMediaType(mediaType, "image/png")) {
            return "png";
        }
        if (isMediaType(mediaType, "image/webp")) {
            return "webp";
        }
        return "jpg";
    }

    /** Media types compare without regard to case; both sides are ASCII, so no case mapping is needed. */
    private static boolean isMediaType(String value, String expected) {
        return value.length() == expected.length() && value.regionMatches(true, 0, expected, 0, expected.length());
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogImage catalogImage)) {
            return false;
        }
        return Arrays.equals(bytes, catalogImage.bytes)
                && Objects.equals(contentType, catalogImage.contentType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
        return "CatalogImage[bytes=" + Arrays.toString(bytes) + ", contentType=" + contentType + "]";
    }
}
