package com.fitsync.nutrition.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.fitsync.nutrition.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Verifies the HMAC-signed access tokens the auth service issues and reads the caller from their claims. The
 * shared secret may be configured base64-encoded or as plain text.
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public CurrentUserPrincipal extractCurrentUser(String token) {
        Claims claims = extractAllClaims(token);
        return new CurrentUserPrincipal(
                UUID.fromString(claims.get("userId", String.class)),
                claims.getSubject(),
                claims.get("displayName", String.class),
                claims.get("role", String.class));
    }

    private SecretKey signingKey() {
        byte[] keyBytes = resolveKeyBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] resolveKeyBytes() {
        String secret = jwtProperties.secret();
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
