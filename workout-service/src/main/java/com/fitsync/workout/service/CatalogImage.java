package com.fitsync.workout.service;

import java.util.Arrays;
import java.util.Objects;

public record CatalogImage(byte[] bytes, String contentType) {

    public CatalogImage {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
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
