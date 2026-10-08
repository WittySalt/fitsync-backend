package com.fitsync.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fitsync.notification.email.EmailSender;
import com.fitsync.notification.email.LoggingEmailSender;
import com.fitsync.notification.email.SesEmailSender;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/**
 * Picks how email leaves the service. SES is used only when {@code fitsync.notification.email.enabled}
 * is true, so a local run without AWS credentials never tries to reach it.
 */
@Configuration
public class EmailSenderConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "fitsync.notification.email", name = "enabled", havingValue = "true")
    SesV2Client sesV2Client(NotificationProperties properties) {
        // The region comes from configuration and the credentials from the default chain, which is
        // the instance role in the cloud.
        return SesV2Client.builder()
                .region(Region.of(properties.email().region()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.notification.email", name = "enabled", havingValue = "true")
    EmailSender sesEmailSender(SesV2Client client, NotificationProperties properties) {
        return new SesEmailSender(client, properties.email().sender(), properties.email().senderName());
    }

    @Bean
    @ConditionalOnProperty(prefix = "fitsync.notification.email", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    EmailSender loggingEmailSender() {
        return new LoggingEmailSender();
    }
}
