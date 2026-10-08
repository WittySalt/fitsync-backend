package com.fitsync.gateway.config;

import com.fitsync.gateway.security.AudienceValidator;
import com.fitsync.gateway.security.CognitoAuthoritiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.util.StringUtils;

/**
 * The Cognito token decoder and authentication converter that {@link SecurityConfig} requires once
 * {@code security.cognito.enabled} is true. Without them the gateway refused to start with Cognito on. Built the
 * same way as auth-service's: signature keys from the JWK set (the user pool's well-known one unless a JWK set
 * URI is given), the issuer and the optional audience checked, and roles read from the Cognito groups and role
 * claims. The JWK set is fetched on the first token, not at startup.
 */
@Configuration
@ConditionalOnProperty(prefix = "security.cognito", name = "enabled", havingValue = "true")
class CognitoSecurityConfiguration {

    @Bean
    JwtDecoder cognitoJwtDecoder(CognitoProperties properties) {
        String issuerUri = require(properties.issuerUri(), "security.cognito.issuer-uri");
        String jwkSetUri = StringUtils.hasText(properties.jwkSetUri())
                ? properties.jwkSetUri()
                : issuerUri + "/.well-known/jwks.json";

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AudienceValidator(properties.audience()));
        jwtDecoder.setJwtValidator(validator);
        return jwtDecoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> cognitoJwtAuthenticationConverter(
            CognitoProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new CognitoAuthoritiesConverter());
        if (StringUtils.hasText(properties.principalClaim())) {
            converter.setPrincipalClaimName(properties.principalClaim());
        }
        return converter;
    }

    private static String require(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when Cognito auth is enabled");
        }
        return value;
    }
}
