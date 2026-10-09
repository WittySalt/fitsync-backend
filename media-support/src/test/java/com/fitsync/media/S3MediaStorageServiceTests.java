package com.fitsync.media;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
                false,
                "");
        S3MediaStorageService service = new S3MediaStorageService(
                s3ClientProvider(s3Client), presignerProvider(), properties);

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
        S3MediaStorageService service = serviceWithBaseUrl("http://media.test/fitsync-media");

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
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), presignerProvider(), properties());

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
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), presignerProvider(), properties());

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
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(s3Client), presignerProvider(), properties());

        assertThrows(IllegalStateException.class, () -> service.deleteAll("avatars/u3/"));
    }

    @Test
    void shouldRefuseAPrefixThatIsNotAFolderOrEscapesIt() {
        S3MediaStorageService service = new S3MediaStorageService(s3ClientProvider(mock(S3Client.class)), presignerProvider(),
                properties());

        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("avatars/u1"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("/"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll("avatars/../"));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAll(null));
    }

    private static MediaStorageProperties properties() {
        return new MediaStorageProperties("http://localhost:9000", "us-east-1", "fitsync", "fitsync-password",
                "fitsync-media", "http://media.test/fitsync-media", false, "http://localhost:9000");
    }

    @Test
    void shouldPresignAShortLivedReadUrlOnThePresignEndpoint() {
        S3MediaStorageService service = serviceWithBaseUrl("http://media.test/fitsync-media");

        URI url = URI.create(service.presignedGetUrl("avatars/u-1/a b.png", Duration.ofHours(1)));

        assertEquals("localhost", url.getHost());
        assertEquals(9000, url.getPort());
        assertEquals("/fitsync-media/avatars/u-1/a%20b.png", url.getRawPath());
        assertTrue(url.getRawQuery().contains("X-Amz-Expires=3600"));
        assertTrue(url.getRawQuery().contains("X-Amz-Signature="));
    }

    @Test
    void shouldRefuseUnsafeKeysAndNonPositiveLifetimesWhenPresigning() {
        S3MediaStorageService service = serviceWithBaseUrl("http://media.test/fitsync-media");

        assertThrows(IllegalArgumentException.class,
                () -> service.presignedGetUrl("../avatars/x.png", Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.presignedGetUrl("avatars/x.png", Duration.ZERO));
    }

    @Test
    void shouldDeriveTheKeyBackFromAPublicUrl() {
        S3MediaStorageService service = serviceWithBaseUrl("http://media.test/fitsync-media/");
        String url = service.urlFor("avatars/u-1/a b+c.png");

        assertEquals(Optional.of("avatars/u-1/a b+c.png"), service.keyForUrl(url));
    }

    @Test
    void shouldNotDeriveAKeyFromForeignOrUnsafeUrls() {
        S3MediaStorageService service = serviceWithBaseUrl("http://media.test/fitsync-media");

        assertEquals(Optional.empty(), service.keyForUrl(null));
        assertEquals(Optional.empty(), service.keyForUrl("https://elsewhere.test/fitsync-media/avatars/x.png"));
        assertEquals(Optional.empty(), service.keyForUrl("http://media.test/fitsync-media/avatars/%2E%2E/x.png"));
        assertEquals(Optional.empty(), service.keyForUrl("http://media.test/fitsync-media/avatars/x.png?sig=1"));
        assertEquals(Optional.empty(), service.keyForUrl("http://media.test/fitsync-media/"));
    }

    private S3MediaStorageService serviceWithBaseUrl(String publicBaseUrl) {
        return new S3MediaStorageService(s3ClientProvider(mock(S3Client.class)), presignerProvider(),
                new MediaStorageProperties(
                        "http://minio:9000",
                        "us-east-1",
                        "fitsync",
                        "fitsync-password",
                        "fitsync-media",
                        publicBaseUrl,
                        false,
                        "http://localhost:9000"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3Presigner> presignerProvider() {
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("fitsync", "fitsync-password")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .endpointOverride(URI.create("http://localhost:9000"))
                .build();
        ObjectProvider<S3Presigner> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(presigner);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<S3Client> s3ClientProvider(S3Client s3Client) {
        ObjectProvider<S3Client> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(s3Client);
        return provider;
    }
}
