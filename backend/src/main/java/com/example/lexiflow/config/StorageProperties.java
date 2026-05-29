package com.example.lexiflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.storage")
public record StorageProperties(String contractsDir, String knowledgeDir) {
}
