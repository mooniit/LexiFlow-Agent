package com.example.lexiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.security.jwt")
public record JwtProperties(String issuer, String secret, long expiresMinutes) {
}

