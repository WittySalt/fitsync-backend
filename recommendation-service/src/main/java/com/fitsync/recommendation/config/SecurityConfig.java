package com.fitsync.recommendation.config;

import com.fitsync.recommendation.security.JwtAuthenticationFilter;
import com.fitsync.web.security.RestAccessDeniedHandler;
import com.fitsync.web.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider;
    private final ObjectProvider<RestAuthenticationEntryPoint> restAuthenticationEntryPointProvider;
    private final ObjectProvider<RestAccessDeniedHandler> restAccessDeniedHandlerProvider;

    public SecurityConfig(ObjectProvider<JwtAuthenticationFilter> jwtAuthenticationFilterProvider,
                          ObjectProvider<RestAuthenticationEntryPoint> restAuthenticationEntryPointProvider,
                          ObjectProvider<RestAccessDeniedHandler> restAccessDeniedHandlerProvider) {
        this.jwtAuthenticationFilterProvider = jwtAuthenticationFilterProvider;
        this.restAuthenticationEntryPointProvider = restAuthenticationEntryPointProvider;
        this.restAccessDeniedHandlerProvider = restAccessDeniedHandlerProvider;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtAuthenticationFilter = requireBean(
                jwtAuthenticationFilterProvider.getIfAvailable(), JwtAuthenticationFilter.class);
        RestAuthenticationEntryPoint restAuthenticationEntryPoint = requireBean(
                restAuthenticationEntryPointProvider.getIfAvailable(), RestAuthenticationEntryPoint.class);
        RestAccessDeniedHandler restAccessDeniedHandler = requireBean(
                restAccessDeniedHandlerProvider.getIfAvailable(), RestAccessDeniedHandler.class);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // The streamed coach answer finishes on an async dispatch of a request that was
                        // already authorised; checking it again fails after the response is committed.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/v1/internal/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static <T> T requireBean(T bean, Class<T> type) {
        if (bean == null) {
            throw new IllegalStateException("Missing required bean: " + type.getSimpleName());
        }
        return bean;
    }
}
