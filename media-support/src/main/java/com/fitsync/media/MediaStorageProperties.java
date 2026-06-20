package com.fitsync.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "fitsync.media")
public record MediaStorageProperties(
        @DefaultValue("http://localhost:9000") String endpoint,
        @DefaultValue("us-east-1") String region,
        @DefaultValue("fitsync") String accessKey,
        @DefaultValue("fitsync-password") String secretKey,
        @DefaultValue("fitsync-media") String bucket,
        @DefaultValue("http://localhost:9000/fitsync-media") String publicBaseUrl) {
}
