package com.khankiddo.learning.security;

import com.khankiddo.learning.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_USERNAME = "username";

    private final AuthProperties authProperties;

    public String generateToken(Long userId, String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(authProperties.getJwtExpirationHours(), ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = Long.parseLong(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);
            return Optional.of(new AuthenticatedUser(userId, username));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private SecretKey signingKey() {
        byte[] keyBytes = authProperties.getEffectiveJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
