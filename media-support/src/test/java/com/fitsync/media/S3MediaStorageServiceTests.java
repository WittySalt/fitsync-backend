package com.fitsync.media;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Error;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
                "http://media.test/fitsync-media/",
                false);
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
                "http://media.test/fitsync-media",
                false));

        assertThrows(IllegalArgumentException.class, () -> service.urlFor("../secret.jpg"));
        assertThrows(IllegalArgumentException.class, () -> service.urlFor("/absolute.jpg"));
    }

    @Test
    void shouldDeleteEveryObjectUnderAFolderAcrossListingPages() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("avatars/u1/a.png").build(),
                                S3Object.builder().key("avatars/u1/b.png").build())
                        .isTruncated(true).nextContinuationToken("page-2").build())
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("avatars/u1/c.png").build())
                        .isTruncated(false).build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder().build());
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), properties());

        int deleted = service.deleteAll("avatars/u1/");

        assertEquals(3, deleted);
        ArgumentCaptor<ListObjectsV2Request> listCaptor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client, times(2)).listObjectsV2(listCaptor.capture());
        assertEquals("avatars/u1/", listCaptor.getAllValues().get(0).prefix());
        assertEquals("page-2", listCaptor.getAllValues().get(1).continuationToken());
        ArgumentCaptor<DeleteObjectsRequest> deleteCaptor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client, times(2)).deleteObjects(deleteCaptor.capture());
        assertEquals("fitsync-media", deleteCaptor.getAllValues().get(0).bucket());
        assertEquals(List.of("avatars/u1/a.png", "avatars/u1/b.png"),
                deleteCaptor.getAllValues().get(0).delete().objects().stream().map(ObjectIdentifier::key).toList());
    }

    @Test
    void shouldDeleteNothingWhenTheFolderIsEmpty() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), properties());

        assertEquals(0, service.deleteAll("avatars/u2/"));
        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }

    @Test
    void shouldReportAnObjectThatCouldNotBeDeleted() {
        S3Client s3Client = mock(S3Client.class);
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("avatars/u3/a.png").build()).isTruncated(false).build());
        when(s3Client.deleteObjects(any(DeleteObjectsRequest.class)))
                .thenReturn(DeleteObjectsResponse.builder()
                        .errors(S3Error.builder().key("avatars/u3/a.png").code("AccessDenied").build()).build());
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), properties());

        assertThrows(IllegalStateException.class, () -> service.deleteAll("avatars/u3/"));
    }

    @Test
    void shouldRefuseAPrefixThatIsNotAFolderOrEscapesIt() {
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(mock(S3Client.class)), properties());

        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("avatars/u1"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("/"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("avatars/../"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll(null));
    }

    private static MediaStorageProperties properties() {
        return new MediaStorageProperties("http://localhost:9000", "us-east-1", "fitsync", "fitsync-password",
                "fitsync-media", "http://media.test/fitsync-media", false);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3Client> s3ClientProvider(S3Client s3Client) {
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(s3Client);
        return provider;
    }
}
