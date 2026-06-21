package com.fitsync.workout.service;

import java.util.Arrays;

public record CatalogImage(byte[] bytes, String contentType) {

    public CatalogImage {
        bytes = bytes == null ? new byte[0] : Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }
}
