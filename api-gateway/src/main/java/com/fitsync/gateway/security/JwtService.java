package com.fitsync.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.fitsync.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public GatewayUserPrincipal extractCurrentUser(String token) {
        Claims claims = extractAllClaims(token);
        return new GatewayUserPrincipal(
                UUID.fromString(claims.get("userId", String.class)),
                claims.getSubject(),
                claims.get("displayName", String.class),
                claims.get("role", String.class));
    }

    /**
     * The {@code sid} claim naming the auth-service session the token belongs to, or {@code null} when the token
     * has none (issued before sessions existed) or it is not a UUID.
     */
    public UUID extractSessionId(String token) {
        String sid = extractAllClaims(token).get("sid", String.class);
        if (sid == null) {
            return null;
        }
        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = resolveKeyBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Accepts the secret Base64-encoded or, when it does not decode, as plain UTF-8 text. */
    private byte[] resolveKeyBytes() {
        String secret = jwtProperties.secret();
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
