package com.fitsync.recommendation;

import com.fitsync.recommendation.config.JwtProperties;
import com.fitsync.recommendation.config.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.fitsync.recommendation", "com.fitsync.web"})
@EnableConfigurationProperties({JwtProperties.class, RecommendationProperties.class})
public class RecommendationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecommendationServiceApplication.class, args);
    }
}
