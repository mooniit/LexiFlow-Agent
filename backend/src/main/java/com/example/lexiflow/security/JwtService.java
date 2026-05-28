package com.example.lexiflow.security;

import com.example.lexiflow.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createToken(CurrentUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.expiresMinutes() * 60);

        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("uid", user.id())
                .claim("displayName", user.displayName())
                .claim("roles", user.roles())
                .claim("permissions", user.permissions())
                .signWith(secretKey)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public CurrentUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new CurrentUser(
                ((Number) claims.get("uid")).longValue(),
                claims.getSubject(),
                claims.get("displayName", String.class),
                (List<String>) claims.get("roles", List.class),
                (List<String>) claims.get("permissions", List.class),
                true
        );
    }
}

