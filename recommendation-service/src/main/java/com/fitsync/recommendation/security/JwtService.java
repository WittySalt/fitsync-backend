package com.fitsync.recommendation.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.SecretKey;

import com.fitsync.recommendation.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/**
 * Verifies the access tokens auth-service issues and reads the caller out of them. This service only
 * verifies: it shares the signing secret and never mints a token. The secret is used Base64-decoded
 * when it decodes, otherwise as its raw UTF-8 bytes.
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
