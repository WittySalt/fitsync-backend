package com.fitsync.notification.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.fitsync.notification.config.JwtProperties;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Verifies the HMAC-signed access tokens issued by auth-service and reads the caller from their
 * claims, the same way the other services do. The shared secret may be Base64-encoded or plain text.
 */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public CurrentUserPrincipal extractCurrentUser(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String userId = claims.get("userId", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || role == null) {
            throw new IllegalArgumentException("Token has no user id or role");
        }
        return new CurrentUserPrincipal(UUID.fromString(userId), role);
    }

    private SecretKey signingKey() {
        String secret = jwtProperties.secret();
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (RuntimeException ignored) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
