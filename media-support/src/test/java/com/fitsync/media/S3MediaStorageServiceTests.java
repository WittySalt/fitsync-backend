package com.fitsync.media;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3MediaStorageServiceTests {

    @Test
    void shouldPutBytesAndReturnStablePublicUrl() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
        MediaStorageProperties properties = new MediaStorageProperties(
                "http://localhost:9000",
                "us-east-1",
                "fitsync",
                "fitsync-password",
                "fitsync-media",
                "http://media.test/fitsync-media/");
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), properties);

        String url = service.put("exercises/Bench Press/0.jpg", "image".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertEquals("fitsync-media", request.bucket());
        assertEquals("exercises/Bench Press/0.jpg", request.key());
        assertEquals("image/jpeg", request.contentType());
        assertEquals("public, max-age=31536000, immutable", request.cacheControl());
        assertEquals("http://media.test/fitsync-media/exercises/Bench%20Press/0.jpg", url);
    }

    @Test
    void shouldRejectUnsafeKeys() {
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(mock(S3Client.class)), new MediaStorageProperties(
                "http://localhost:9000",
                "us-east-1",
                "fitsync",
                "fitsync-password",
                "fitsync-media",
                "http://media.test/fitsync-media"));

        assertThrows(IllegalArgumentException.class, () -> service.urlFor("../secret.jpg"));
        assertThrows(IllegalArgumentException.class, () -> service.urlFor("/absolute.jpg"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3Client> s3ClientProvider(S3Client s3Client) {
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(s3Client);
        return provider;
    }
}
