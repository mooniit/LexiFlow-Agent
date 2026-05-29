package com.example.lexiflow.config;

import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RabbitMqProperties.class, StorageProperties.class})
public class RabbitMqConfig {

    @Bean
    public Queue contractReviewQueue() {
        return new Queue("contract.review.queue", true);
    }

    @Bean
    public Queue documentIngestQueue() {
        return new Queue("document.ingest.queue", true);
    }

    @Bean
    public Queue toolRetryQueue() {
        return new Queue("tool.retry.queue", true);
    }

    @Bean
    public Queue approvalEventQueue() {
        return new Queue("approval.event.queue", true);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue("notification.queue", true);
    }
}

