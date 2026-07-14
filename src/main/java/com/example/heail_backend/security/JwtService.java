package com.example.heail_backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties props;

    private SecretKey key() {
        byte[] bytes = props.getSecret().getBytes(StandardCharsets.UTF_8);
        // pad / truncate to 32 bytes for HS256
        byte[] key32 = new byte[32];
        System.arraycopy(bytes, 0, key32, 0, Math.min(bytes.length, 32));
        return Keys.hmacShaKeyFor(key32);
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getAccessTokenExpiryMs()))
                .signWith(key())
                .compact();
    }

    public String generateRefreshTokenValue() {
        return java.util.UUID.randomUUID().toString();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessTokenValid(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractSubject(String token) {
        return parseAccessToken(token).getSubject();
    }

    public long accessTokenExpirySeconds() {
        return props.getAccessTokenExpiryMs() / 1000;
    }
}
