package com.fitsync.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import com.fitsync.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

/** Issues and reads the application's own HMAC-signed JWTs (not Cognito tokens, which Spring decodes). */
@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /** Claim naming the server-side session ({@code auth_sessions.id}) the token was issued for. */
    public static final String SESSION_ID_CLAIM = "sid";

    /**
     * Signs a token for {@code subject} valid from {@code issuedAt} until {@code expiresAt}. JWT times have whole
     * seconds, so callers pass instants already truncated to seconds to report the exact expiry.
     */
    public String generateToken(String subject, Map<String, Object> claims, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();
    }

    /** How long an access token lives ({@code security.jwt.expiration}). */
    public Duration accessTokenLifetime() {
        return jwtProperties.expiration();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Instant extractIssuedAt(String token) {
        Date issuedAt = extractAllClaims(token).getIssuedAt();
        return issuedAt == null ? null : issuedAt.toInstant();
    }

    public Instant extractExpiration(String token) {
        Date expiration = extractAllClaims(token).getExpiration();
        return expiration == null ? null : expiration.toInstant();
    }

    /**
     * The session id the token was issued for, or {@code null} for a token issued before sessions existed (or one
     * whose claim is not a UUID). The token must already have been verified by the caller; this parses it again.
     */
    public UUID extractSessionId(String token) {
        String sid = extractAllClaims(token).get(SESSION_ID_CLAIM, String.class);
        if (sid == null) {
            return null;
        }
        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        return userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
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
