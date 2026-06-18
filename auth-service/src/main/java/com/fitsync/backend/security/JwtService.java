package com.fitsync.backend.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import com.fitsync.backend.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(UserDetails userDetails, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.expiration())))
                .signWith(signingKey())
                .compact();
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

    private byte[] resolveKeyBytes() {
        String secret = jwtProperties.secret();
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException ignored) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
