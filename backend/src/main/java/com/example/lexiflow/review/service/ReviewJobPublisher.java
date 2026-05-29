package com.example.lexiflow.review.service;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import com.example.lexiflow.security.CurrentUser;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReviewJobPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ReviewJobPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishReviewJob(Long reviewId, CurrentUser user) {
        rabbitTemplate.convertAndSend(
                RabbitMqNames.AGENT_EXCHANGE,
                RabbitMqNames.CONTRACT_REVIEW_ROUTING_KEY,
                new ContractReviewJobMessage(reviewId, user.id(), user.username())
        );
    }
}

