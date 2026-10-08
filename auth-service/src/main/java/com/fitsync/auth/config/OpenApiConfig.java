package com.fitsync.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI fitSyncOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FitSync AI Backend API")
                        .version("v1")
                        .description("REST APIs for authentication, fitness tracking, nutrition, analytics, and recommendations."));
    }
}
