package com.example.lexiflow.approval.service;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ApprovalEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ApprovalEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ApprovalEventMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.AGENT_EXCHANGE,
                RabbitMqNames.APPROVAL_EVENT_ROUTING_KEY,
                message
        );
    }
}
