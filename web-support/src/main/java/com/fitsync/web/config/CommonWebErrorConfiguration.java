package com.fitsync.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitsync.web.error.ApiErrorFactory;
import com.fitsync.web.error.GlobalApiExceptionHandler;
import com.fitsync.web.security.RestAccessDeniedHandler;
import com.fitsync.web.security.RestAuthenticationEntryPoint;
import com.fitsync.web.timezone.RequestTimezoneArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.List;

@Configuration
public class CommonWebErrorConfiguration implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new RequestTimezoneArgumentResolver());
    }

    @Bean
    ApiErrorFactory apiErrorFactory() {
        return new ApiErrorFactory();
    }

    @Bean
    GlobalApiExceptionHandler globalApiExceptionHandler(ApiErrorFactory apiErrorFactory) {
        return new GlobalApiExceptionHandler(apiErrorFactory);
    }

    @Bean
    RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper,
                                                              ApiErrorFactory apiErrorFactory) {
        return new RestAuthenticationEntryPoint(objectMapper, apiErrorFactory);
    }

    @Bean
    RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper,
                                                    ApiErrorFactory apiErrorFactory) {
        return new RestAccessDeniedHandler(objectMapper, apiErrorFactory);
    }
}
