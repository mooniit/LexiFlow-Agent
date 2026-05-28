package com.example.lexiflow.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lexiflow.rabbitmq")
public record RabbitMqProperties(List<String> queues) {
}

