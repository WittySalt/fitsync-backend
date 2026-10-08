package com.fitsync.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fitsync.notification.config.NotificationProperties;

@SpringBootApplication(scanBasePackages = {"com.fitsync.notification", "com.fitsync.web"})
@EnableConfigurationProperties(NotificationProperties.class)
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
