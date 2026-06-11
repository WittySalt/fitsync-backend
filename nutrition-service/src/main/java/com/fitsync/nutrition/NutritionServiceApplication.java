package com.fitsync.nutrition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fitsync.web.config.CommonWebErrorConfiguration;

@EnableJpaAuditing
@EnableScheduling
@ConfigurationPropertiesScan
@Import(CommonWebErrorConfiguration.class)
@SpringBootApplication
public class NutritionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutritionServiceApplication.class, args);
    }
}
