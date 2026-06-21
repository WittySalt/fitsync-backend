package com.fitsync.workout.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class CatalogImageTests {

    @Test
    void imagesWithEqualBytesAndContentTypeAreEqual() {
        CatalogImage first = new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/jpeg");
        CatalogImage second = new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        assertThat(first)
                .isEqualTo(second)
                .hasSameHashCodeAs(second);
    }

    @Test
    void equalsReturnsTrueForSameReference() {
        CatalogImage image = new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/jpeg");
        Object sameReference = image;

        assertThat(image.equals(sameReference)).isTrue();
    }

    @Test
    void notEqualToNullOrUnrelatedType() {
        CatalogImage image = new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/jpeg");
        Object unrelated = "image";

        assertThat(image.equals(null)).isFalse();
        assertThat(image.equals(unrelated)).isFalse();
    }

    @Test
    void notEqualWhenBytesOrContentTypeDiffer() {
        CatalogImage base = new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        assertThat(base.equals(new CatalogImage("other".getBytes(StandardCharsets.UTF_8), "image/jpeg"))).isFalse();
        assertThat(base.equals(new CatalogImage("image".getBytes(StandardCharsets.UTF_8), "image/png"))).isFalse();
    }

    @Test
    void bytesAreDefensivelyCopied() {
        byte[] bytes = "image".getBytes(StandardCharsets.UTF_8);
        CatalogImage image = new CatalogImage(bytes, "image/jpeg");

        bytes[0] = 'x';
        byte[] returnedBytes = image.bytes();
        returnedBytes[1] = 'x';

        assertThat(image.bytes()).isEqualTo("image".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void toStringIncludesArrayContent() {
        CatalogImage image = new CatalogImage(new byte[] {1, 2}, "image/png");

        assertThat(image).hasToString("CatalogImage[bytes=[1, 2], contentType=image/png]");
    }
}
