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

    @Test
    void contentTypeIsJudgedByTheFileSuffixWithJpegAsTheDefault() {
        assertThat(CatalogImage.contentTypeOf("Bench_Press/0.PNG ")).isEqualTo("image/png");
        assertThat(CatalogImage.contentTypeOf("Bench_Press/0.webp")).isEqualTo("image/webp");
        assertThat(CatalogImage.contentTypeOf("Bench_Press/0.jpg")).isEqualTo("image/jpeg");
        assertThat(CatalogImage.contentTypeOf(null)).isEqualTo("image/jpeg");
    }

    @Test
    void fileExtensionFollowsTheContentTypeAndKeepsJpgForJpegAndUnknownTypes() {
        assertThat(new CatalogImage(new byte[0], "image/png").fileExtension()).isEqualTo("png");
        assertThat(new CatalogImage(new byte[0], "image/WebP; q=1").fileExtension()).isEqualTo("webp");
        assertThat(new CatalogImage(new byte[0], "image/jpeg").fileExtension()).isEqualTo("jpg");
        assertThat(new CatalogImage(new byte[0], "image/gif").fileExtension()).isEqualTo("jpg");
        assertThat(new CatalogImage(new byte[0], null).fileExtension()).isEqualTo("jpg");
    }
}
