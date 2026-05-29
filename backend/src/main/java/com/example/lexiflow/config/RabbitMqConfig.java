package com.example.lexiflow.config;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RabbitMqProperties.class, StorageProperties.class})
public class RabbitMqConfig {

    @Bean
    public Queue contractReviewQueue() {
        return new Queue(RabbitMqNames.CONTRACT_REVIEW_QUEUE, true);
    }

    @Bean
    public Queue documentIngestQueue() {
        return new Queue(RabbitMqNames.DOCUMENT_INGEST_QUEUE, true);
    }

    @Bean
    public Queue toolRetryQueue() {
        return new Queue(RabbitMqNames.TOOL_RETRY_QUEUE, true);
    }

    @Bean
    public Queue approvalEventQueue() {
        return new Queue(RabbitMqNames.APPROVAL_EVENT_QUEUE, true);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(RabbitMqNames.NOTIFICATION_QUEUE, true);
    }

    @Bean
    public DirectExchange agentExchange() {
        return new DirectExchange(RabbitMqNames.AGENT_EXCHANGE, true, false);
    }

    @Bean
    public Binding contractReviewBinding(Queue contractReviewQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(contractReviewQueue).to(agentExchange).with(RabbitMqNames.CONTRACT_REVIEW_ROUTING_KEY);
    }

    @Bean
    public Binding documentIngestBinding(Queue documentIngestQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(documentIngestQueue).to(agentExchange).with(RabbitMqNames.DOCUMENT_INGEST_ROUTING_KEY);
    }

    @Bean
    public Binding toolRetryBinding(Queue toolRetryQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(toolRetryQueue).to(agentExchange).with(RabbitMqNames.TOOL_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding approvalEventBinding(Queue approvalEventQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(approvalEventQueue).to(agentExchange).with(RabbitMqNames.APPROVAL_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(notificationQueue).to(agentExchange).with(RabbitMqNames.NOTIFICATION_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

