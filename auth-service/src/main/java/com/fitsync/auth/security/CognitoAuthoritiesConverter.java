package com.fitsync.backend.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.StringUtils;

/**
 * Adds role authorities from a Cognito token's {@code cognito:groups} and custom {@code role} claims to the
 * default scope authorities, upper-casing and prefixing each with {@code ROLE_} unless it already is.
 */
public class CognitoAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> defaultAuthorities = defaultAuthoritiesConverter.convert(jwt);
        List<GrantedAuthority> authorities = new ArrayList<>(defaultAuthorities == null ? List.of() : defaultAuthorities);

        Object groupsClaim = jwt.getClaims().get("cognito:groups");
        if (groupsClaim instanceof Collection<?> groups) {
            groups.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(StringUtils::hasText)
                    .map(group -> group.startsWith("ROLE_") ? group : "ROLE_" + group.toUpperCase(Locale.ROOT))
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        String roleClaim = jwt.getClaimAsString("role");
        if (StringUtils.hasText(roleClaim)) {
            String role = roleClaim.startsWith("ROLE_") ? roleClaim : "ROLE_" + roleClaim.toUpperCase(Locale.ROOT);
            authorities.add(new SimpleGrantedAuthority(role));
        }

        return authorities;
    }
}
