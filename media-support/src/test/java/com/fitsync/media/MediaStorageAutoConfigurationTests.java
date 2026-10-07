package com.fitsync.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;

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

    private static Object credentialsProvider(S3Client client) {
        return client.serviceClientConfiguration().credentialsProvider();
    }
}
