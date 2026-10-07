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

@AutoConfiguration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    S3Client mediaS3Client(MediaStorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentialsProvider(properties))
                .forcePathStyle(true);
        if (StringUtils.hasText(properties.endpoint())) {
            builder.endpointOverride(URI.create(properties.endpoint()));
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
                                            MediaStorageProperties properties) {
        return new S3MediaStorageService(mediaS3ClientProvider, properties);
    }
}
