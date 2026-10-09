package com.fitsync.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class MediaStorageAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MediaStorageAutoConfiguration.class));

    @Test
    void usesTheConfiguredKeysByDefaultSoLocalMinioKeepsWorking() {
        runner.run(context -> assertThat(credentialsProvider(context.getBean(S3Client.class)))
                .isInstanceOf(StaticCredentialsProvider.class));
    }

    @Test
    void usesTheAwsDefaultChainWhenAskedSoTheCloudNeedsNoKeys() {
        runner.withPropertyValues("fitsync.media.aws-default-credentials=true")
                .run(context -> assertThat(credentialsProvider(context.getBean(S3Client.class)))
                        .isInstanceOf(DefaultCredentialsProvider.class));
    }

    @Test
    void signsUrlsForTheBrowserFacingEndpointWhenOneIsSet() {
        runner.withPropertyValues("fitsync.media.endpoint=http://minio:9000",
                        "fitsync.media.presign-endpoint=http://localhost:9000")
                .run(context -> assertThat(context.getBean(MediaStorageService.class)
                        .presignedGetUrl("avatars/u/1.png", Duration.ofMinutes(5)))
                        .startsWith("http://localhost:9000/fitsync-media/avatars/u/1.png?"));
    }

    @Test
    void signsUrlsForTheServiceEndpointWhenNoPresignEndpointIsSet() {
        runner.withPropertyValues("fitsync.media.endpoint=https://s3.ap-southeast-1.amazonaws.com",
                        "fitsync.media.region=ap-southeast-1")
                .run(context -> assertThat(context.getBean(S3Presigner.class)
                        .presignGetObject(request -> request.signatureDuration(Duration.ofMinutes(5))
                                .getObjectRequest(get -> get.bucket("fitsync-media").key("avatars/u/1.png")))
                        .url().toString())
                        .startsWith("https://s3.ap-southeast-1.amazonaws.com/fitsync-media/avatars/u/1.png?"));
    }

    private static Object credentialsProvider(S3Client client) {
        return client.serviceClientConfiguration().credentialsProvider();
    }
}
