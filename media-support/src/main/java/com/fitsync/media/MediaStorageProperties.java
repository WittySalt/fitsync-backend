package com.fitsync.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param awsDefaultCredentials when true the S3 client takes credentials from the AWS default
 *                              chain (the EC2 instance role in the cloud) and ignores
 *                              accessKey/secretKey; when false (the default, for local MinIO)
 *                              it uses those keys
 * @param presignEndpoint       the endpoint written into pre-signed URLs, which a browser must be able to reach;
 *                              blank means {@code endpoint}. Locally the services reach MinIO as
 *                              {@code http://minio:9000} inside docker while the browser needs
 *                              {@code http://localhost:9000}, so the local compose file sets it
 */
@ConfigurationProperties(prefix = "fitsync.media")
public record MediaStorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("fitsync") String accessKey,
        @DefaultValue("fitsync-password") String secretKey,
        @DefaultValue("fitsync-media") String bucket,
        @DefaultValue("http://localhost:9000/fitsync-media") String publicBaseUrl,
        @DefaultValue("false") boolean awsDefaultCredentials,
        @DefaultValue("") String presignEndpoint) {
}
