package com.fitsync.backend.config;

import com.fitsync.backend.security.JwtAuthenticationFilter;
import com.fitsync.backend.security.RestAuthenticationEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final CognitoProperties cognitoProperties;
    private final JwtDecoder cognitoJwtDecoder;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> cognitoJwtAuthenticationConverter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint,
                          CognitoProperties cognitoProperties,
                          ObjectProvider<JwtDecoder> cognitoJwtDecoderProvider,
                          ObjectProvider<Converter<Jwt, ? extends AbstractAuthenticationToken>> cognitoJwtAuthenticationConverterProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.cognitoProperties = cognitoProperties;
        this.cognitoJwtDecoder = cognitoJwtDecoderProvider.getIfAvailable();
        this.cognitoJwtAuthenticationConverter = cognitoJwtAuthenticationConverterProvider.getIfAvailable();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/internal/**").hasRole("ADMIN")
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (cognitoProperties.enabled()) {
            if (cognitoJwtDecoder == null || cognitoJwtAuthenticationConverter == null) {
                throw new IllegalStateException("Cognito auth is enabled but JWT resource server beans are missing");
            }
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .jwt(jwt -> jwt
                            .decoder(cognitoJwtDecoder)
                            .jwtAuthenticationConverter(cognitoJwtAuthenticationConverter)));
        }

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
