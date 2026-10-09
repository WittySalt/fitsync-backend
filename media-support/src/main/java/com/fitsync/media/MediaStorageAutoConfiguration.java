package com.fitsync.media;

import java.net.URI;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Provides a {@link MediaStorageService} backed by S3 (MinIO locally) to any service that has media-support on
 * its classpath. Either bean can be replaced by declaring one of the same type.
 */
@AutoConfiguration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    S3Client mediaS3Client(MediaStorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                // Path-style URLs (endpoint/bucket/key), which MinIO needs and S3 still accepts.
                .forcePathStyle(true);
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
        }
        return builder.build();
    }

    /**
     * Signs read URLs for private objects such as avatars. Signing happens locally with the same credentials as
     * the client, so it needs no network call. The URL names {@code presignEndpoint} when it is set, because the
     * browser, not the service, follows it.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    S3Presigner mediaS3Presigner(MediaStorageProperties properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        String endpoint = StringUtils.hasText(properties.presignEndpoint())
                ? properties.presignEndpoint()
                : properties.endpoint();
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credentialsProvider(MediaStorageProperties properties) {
        if (properties.awsDefaultCredentials()) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }

    @Bean
    @ConditionalOnMissingBean
    MediaStorageService mediaStorageService(ObjectProvider<S3Client> mediaS3ClientProvider,
                                            ObjectProvider<S3Presigner> mediaS3PresignerProvider,
                                            MediaStorageProperties properties) {
        return new S3MediaStorageService(mediaS3ClientProvider, mediaS3PresignerProvider, properties);
    }
}
