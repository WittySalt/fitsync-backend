package com.fitsync.workout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.fitsync.web.config.CommonWebErrorConfiguration;

@SpringBootApplication
@ConfigurationPropertiesScan
@Import(CommonWebErrorConfiguration.class)
@EnableScheduling
public class WorkoutServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkoutServiceApplication.class, args);
    }
}
