package com.example.lexiflow.review.service;

import com.example.lexiflow.infrastructure.mq.RabbitMqNames;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewJobConsumer {

    private final ContractReviewService contractReviewService;

    public ReviewJobConsumer(ContractReviewService contractReviewService) {
        this.contractReviewService = contractReviewService;
    }

    @RabbitListener(queues = RabbitMqNames.CONTRACT_REVIEW_QUEUE)
    public void consume(ContractReviewJobMessage message) {
        contractReviewService.runQueuedReview(message.reviewId(), message.userId());
    }
}

